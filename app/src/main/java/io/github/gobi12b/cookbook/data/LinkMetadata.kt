package io.github.gobi12b.cookbook.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** [caption] is the raw text (with hashtags) used for auto-picking groups; [title] is the cleaned name. */
data class LinkMeta(val title: String?, val thumbnailUrl: String?, val caption: String? = null)

/**
 * Detects a title and thumbnail for a shared link.
 * YouTube goes through its public oEmbed endpoint; everything else is read from Open Graph tags.
 */
object LinkMetadata {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    private val URL_REGEX = Regex("""https?://[^\s<>"']+""")

    /** Pulls the first http(s) URL out of shared text such as "Check this out https://youtu.be/xyz". */
    fun extractUrl(text: String?): String? =
        text?.let { URL_REGEX.find(it)?.value?.trimEnd('.', ',', ')', '!', '?') }

    suspend fun fetch(url: String): LinkMeta = withContext(Dispatchers.IO) {
        val yt = if (isYouTube(url)) runCatching { youTubeOEmbed(url) }.getOrNull() else null
        if (yt?.title != null) return@withContext yt
        val og = runCatching { openGraph(url) }.getOrNull()
        LinkMeta(
            title = og?.title ?: yt?.title,
            thumbnailUrl = og?.thumbnailUrl ?: yt?.thumbnailUrl ?: youTubeId(url)?.let {
                "https://i.ytimg.com/vi/$it/hqdefault.jpg"
            },
            caption = listOfNotNull(og?.caption, yt?.caption).joinToString("\n").ifBlank { null },
        )
    }

    /** Downloads and downsamples an image to [dest] as JPEG. Returns true on success. */
    suspend fun downloadThumbnail(imageUrl: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = open(imageUrl).run { try { inputStream.use { it.readBytes() } } finally { disconnect() } }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 720) sample *= 2
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching false
            val bmp = trimLetterbox(raw)
            dest.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (bmp !== raw) raw.recycle()
            bmp.recycle()
            true
        }.getOrDefault(false)
    }

    /** Removes the solid black bars YouTube bakes into 4:3 thumbnails of 16:9 videos. */
    internal fun trimLetterbox(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        fun isBlack(y: Int): Boolean {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            var dark = 0
            for (x in 0 until w step 4) {
                val p = row[x]
                if (((p shr 16) and 0xFF) < 24 && ((p shr 8) and 0xFF) < 24 && (p and 0xFF) < 24) dark++
            }
            return dark >= (w / 4) * 0.97
        }
        var top = 0
        while (top < h / 3 && isBlack(top)) top++
        var bottom = h - 1
        while (bottom > h * 2 / 3 && isBlack(bottom)) bottom--
        val newH = bottom - top + 1
        return if (top < h * 0.04 && h - 1 - bottom < h * 0.04) bmp else Bitmap.createBitmap(bmp, 0, top, w, newH)
    }

    private fun isYouTube(url: String) = "youtube.com" in url || "youtu.be" in url

    private fun youTubeId(url: String): String? =
        Regex("""(?:youtu\.be/|v=|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})""").find(url)?.groupValues?.get(1)

    private fun youTubeOEmbed(url: String): LinkMeta {
        val endpoint = "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(url, "UTF-8")
        val json = JSONObject(readText(endpoint, 64 * 1024))
        val title = json.optString("title").ifBlank { null }
        return LinkMeta(title, json.optString("thumbnail_url").ifBlank { null }, title)
    }

    private fun openGraph(url: String): LinkMeta {
        val html = readText(url, 768 * 1024)
        fun meta(vararg keys: String): String? {
            for (key in keys) {
                val k = Regex.escape(key)
                val a = Regex("""<meta[^>]+(?:property|name)\s*=\s*["']$k["'][^>]*content\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                val b = Regex("""<meta[^>]+content\s*=\s*["']([^"']*)["'][^>]*(?:property|name)\s*=\s*["']$k["']""", RegexOption.IGNORE_CASE)
                val v = (a.find(html) ?: b.find(html))?.groupValues?.get(1)?.let(::decode)
                if (!v.isNullOrBlank()) return v
            }
            return null
        }
        val title = meta("og:title", "twitter:title")
            ?: Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::decode)
        val image = meta("og:image:secure_url", "og:image", "twitter:image")
        val description = meta("og:description", "description", "twitter:description")
        return LinkMeta(
            title?.let(::cleanTitle)?.takeUnless(::isGenericTitle), image,
            listOfNotNull(title, description).joinToString("\n").ifBlank { null },
        )
    }

    /** Instagram-style titles look like `Chef on Instagram: "Butter chicken 🔥 …"` — keep the caption's first line. */
    internal fun cleanTitle(raw: String): String? {
        var t = raw.trim()
        Regex("""^.{1,80}? on (?:Instagram|TikTok|Facebook)\s*:\s*["“](.+)""", RegexOption.DOT_MATCHES_ALL).find(t)?.let {
            t = it.groupValues[1].trimEnd('"', '”', ' ')
        }
        t = t.removeSuffix(" - YouTube").lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        // Drop hashtags ("Mutton kulambu ❤️ #muttonkulambu #shorts") unless they're all there is.
        val noTags = t.replace(Regex("""#[\p{L}\p{M}\p{N}_]+"""), "").replace(Regex("""\s{2,}"""), " ").trim().trimEnd('|', '-', '·', ' ')
        if (noTags.count { it.isLetterOrDigit() } >= 3) t = noTags
        if (t.length > 90) t = t.take(90).substringBeforeLast(' ').trimEnd(',', '.', ' ') + "…"
        return t.ifBlank { null }
    }

    /** Login walls and app shells report the site name instead of the post's caption. */
    private val GENERIC_TITLES = Regex(
        """^(instagram|login\s*•\s*instagram|log in.*|sign up.*|tiktok.*make your day|facebook|youtube|x|twitter|threads)$""",
        RegexOption.IGNORE_CASE,
    )

    internal fun isGenericTitle(t: String) = GENERIC_TITLES.matches(t.trim())

    private fun decode(s: String) = HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "en")
            if (responseCode !in 200..299) {
                disconnect(); error("HTTP $responseCode for $url")
            }
        }

    private fun readText(url: String, limit: Int): String {
        val conn = open(url)
        try {
            conn.inputStream.use { input ->
                val buf = ByteArray(limit)
                var n = 0
                while (n < limit) {
                    val r = input.read(buf, n, limit - n)
                    if (r < 0) break
                    n += r
                }
                return String(buf, 0, n, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }
}
