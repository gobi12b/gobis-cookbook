package io.github.gobi12b.cookbook.data

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Reads Instagram reel captions and thumbnails.
 *
 * 1. **No login:** Instagram's public embed page (`/reel/<id>/embed/captioned/`, the one websites use to
 *    embed posts) is rendered in a hidden WebView and the caption + image are read from it.
 * 2. **Signed in (optional):** if the embed fails (e.g. private account) and the user has connected
 *    Instagram in InstagramLoginDialog, the full post page is read with their session instead.
 */
object InstagramSession {
    const val LOGIN_URL = "https://www.instagram.com/accounts/login/"

    private val POST_URL = Regex("""instagram\.com/(?:[A-Za-z0-9_.]+/)?(reels?|p|tv)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)

    fun isInstagram(url: String) = "instagram.com" in url.lowercase()

    fun isConnected(): Boolean =
        CookieManager.getInstance().getCookie("https://www.instagram.com")?.contains("sessionid=") == true

    fun disconnect() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    internal fun embedUrl(url: String): String? = POST_URL.find(url)?.let { m ->
        val type = m.groupValues[1].lowercase().let { if (it == "reels") "reel" else it }
        "https://www.instagram.com/$type/${m.groupValues[2]}/embed/captioned/"
    }

    /** Embed page: caption text without the username / comment-count lines, plus the media image. */
    private const val EMBED_JS = """
        (function() {
          var cap = document.querySelector('.Caption'), title = null;
          if (cap) {
            var c = cap.cloneNode(true);
            c.querySelectorAll('.CaptionUsername, .CaptionComments').forEach(function(e) { e.remove(); });
            title = c.innerText;
          }
          var img = document.querySelector('img.EmbeddedMediaImage'), v = document.querySelector('video[poster]');
          var image = img && img.src ? img.src : (v ? v.poster : null);
          return JSON.stringify({ title: title, image: image });
        })();
    """

    /** Full post page (signed in): Open Graph tags, falling back to what's rendered. */
    private const val PAGE_JS = """
        (function() {
          function meta(p) { var m = document.querySelector('meta[property="' + p + '"],meta[name="' + p + '"]'); return m ? m.content : null; }
          var img = meta('og:image');
          if (!img) { var v = document.querySelector('video[poster]'); if (v) img = v.poster; }
          if (!img) { var i = document.querySelector('article img, main img[src*="scontent"], img[src*="cdninstagram"][alt]'); if (i && i.src) img = i.src; }
          var title = meta('og:title') || meta('og:description');
          if (!title) { var h = document.querySelector('h1'); if (h) title = h.innerText; }
          return JSON.stringify({ title: title, image: img });
        })();
    """

    suspend fun fetch(context: Context, url: String): LinkMeta? {
        val embed = embedUrl(url)?.let { read(context, it, EMBED_JS, 12_000) }
        if (embed?.title != null && embed.thumbnailUrl != null) return embed
        val page = if (isConnected()) read(context, url, PAGE_JS, 15_000) else null
        return LinkMeta(embed?.title ?: page?.title, embed?.thumbnailUrl ?: page?.thumbnailUrl, embed?.caption ?: page?.caption)
            .takeIf { it.title != null || it.thumbnailUrl != null }
    }

    /** Turns a raw caption into a short title: first meaningful line, hashtags and mentions removed. */
    internal fun captionToTitle(raw: String): String? {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            val cleaned = line.replace(Regex("""[#@][\p{L}\p{M}\p{N}_.]+"""), "").replace(Regex("""\s{2,}"""), " ").trim()
            if (cleaned.count { it.isLetterOrDigit() } >= 3) return LinkMetadata.cleanTitle(cleaned)
        }
        return lines.firstOrNull()?.let(LinkMetadata::cleanTitle)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun read(context: Context, pageUrl: String, js: String, timeoutMs: Long): LinkMeta? =
        withContext(Dispatchers.Main) {
            val web = WebView(context.applicationContext)
            try {
                // A real phone-sized viewport so percentage-height layouts render off-screen.
                val dm = context.resources.displayMetrics
                web.measure(
                    View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY),
                )
                web.layout(0, 0, dm.widthPixels, dm.heightPixels)
                web.settings.javaScriptEnabled = true
                web.settings.domStorageEnabled = true
                web.settings.blockNetworkImage = true // only the image URL is needed, not the bytes
                CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
                val loaded = CompletableDeferred<Unit>()
                web.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, u: String) { loaded.complete(Unit) }
                }
                web.loadUrl(pageUrl)

                withTimeoutOrNull(timeoutMs) {
                    loaded.await()
                    var best: LinkMeta? = null
                    // Instagram renders client-side; poll until both pieces appear.
                    while (true) {
                        val json = runCatching { JSONObject(evaluate(web, js)) }.getOrNull()
                        if (json != null) {
                            val raw = json.optString("title").takeIf { it.isNotBlank() && it != "null" }
                            val title = raw?.let(::captionToTitle)?.takeUnless(LinkMetadata::isGenericTitle)
                            val image = json.optString("image").takeIf { it.startsWith("http") }
                            best = LinkMeta(title ?: best?.title, image ?: best?.thumbnailUrl, raw ?: best?.caption)
                            if (best.title != null && best.thumbnailUrl != null) break
                        }
                        delay(400)
                    }
                    best
                } ?: run {
                    // Timed out: return whatever was found so far.
                    runCatching { JSONObject(evaluate(web, js)) }.getOrNull()?.let { j ->
                        val raw = j.optString("title").takeIf { it.isNotBlank() && it != "null" }
                        LinkMeta(
                            raw?.let(::captionToTitle)?.takeUnless(LinkMetadata::isGenericTitle),
                            j.optString("image").takeIf { it.startsWith("http") },
                            raw,
                        )
                    }
                }
            } finally {
                web.stopLoading()
                web.destroy()
            }
        }

    private suspend fun evaluate(web: WebView, js: String): String {
        val result = CompletableDeferred<String>()
        // evaluateJavascript hands back a JSON-encoded string literal.
        web.evaluateJavascript(js) { v -> result.complete(runCatching { JSONObject("{\"v\":$v}").optString("v") }.getOrDefault("")) }
        return result.await()
    }
}
