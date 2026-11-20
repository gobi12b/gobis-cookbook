package io.github.gobi12b.cookbook.data

import org.json.JSONArray
import org.json.JSONObject

/** Where a saved link came from; drives the badge shown on the thumbnail ("Video", "Reel", …). */
enum class SourceKind(val label: String) {
    VIDEO("Video"), SHORT("Short"), REEL("Reel"), POST("Post"), LINK("Link");

    companion object {
        fun fromUrl(url: String): SourceKind {
            val u = url.lowercase()
            return when {
                "youtube.com/shorts/" in u -> SHORT
                "youtube.com" in u || "youtu.be" in u -> VIDEO
                "instagram.com/reel" in u -> REEL
                "instagram.com/p/" in u -> POST
                "tiktok.com" in u -> REEL
                "facebook.com/reel" in u || "fb.watch" in u -> REEL
                "vimeo.com" in u -> VIDEO
                else -> LINK
            }
        }
    }
}

enum class FetchStatus { LOADING, DONE, FAILED }

data class Group(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val createdAt: Long,
    val emoji: String = GroupEmoji.guess(name),
)

/** Picks a fitting food emoji for a group name; used for defaults and as the user types a new name. */
object GroupEmoji {
    val choices = listOf(
        "🍛", "🥞", "⚡", "🍮", "🍿", "🍚", "🍗", "🍖", "🥚", "🐟", "🥗", "🍲",
        "🌶️", "🧁", "🍕", "🍜", "🥘", "🫓", "☕", "🥤", "🍰", "🥦", "🍝", "🌮",
    )
    private val rules = listOf(
        listOf("dinner", "curry", "gravy", "kulambu", "kuzhambu") to "🍛",
        listOf("tiffin", "breakfast", "dosa", "idli", "pancake") to "🥞",
        listOf("quick", "fast", "easy", "10 min", "instant") to "⚡",
        listOf("sweet", "dessert", "payasam", "halwa") to "🍮",
        listOf("snack", "evening", "chaat") to "🍿",
        listOf("biryani", "rice", "pulao") to "🍚",
        listOf("chicken") to "🍗",
        listOf("mutton", "meat", "beef", "lamb") to "🍖",
        listOf("egg") to "🥚",
        listOf("fish", "prawn", "sea") to "🐟",
        listOf("salad", "healthy", "diet", "veg") to "🥗",
        listOf("soup", "rasam") to "🍲",
        listOf("spicy", "chilli", "chili") to "🌶️",
        listOf("cake", "bake", "cookie") to "🧁",
        listOf("pizza") to "🍕",
        listOf("noodle", "ramen") to "🍜",
        listOf("pasta") to "🍝",
        listOf("roti", "chapati", "paratha", "bread") to "🫓",
        listOf("tea", "coffee", "chai") to "☕",
        listOf("drink", "juice", "smoothie", "shake") to "🥤",
        listOf("lunch") to "🥘",
    )

    /** Words that suggest a recipe belongs in a group with this name ("Dinner" → dinner, curry, kulambu, …). */
    fun keywordsFor(name: String): Set<String> {
        val n = name.lowercase().trim()
        val own = setOf(n, n.removeSuffix("s"), n.removeSuffix("es")).filter { it.length >= 3 }
        val related = rules.filter { (keys, _) -> keys.any { it in n } }.flatMap { it.first }
        return (own + related).toSet()
    }

    fun guess(name: String): String {
        val n = name.lowercase()
        return rules.firstOrNull { (keys, _) -> keys.any { it in n } }?.second
            ?: choices[(n.hashCode() and Int.MAX_VALUE) % choices.size]
    }
}

data class Recipe(
    val id: String,
    val url: String,
    val title: String,
    val thumbPath: String?,
    val kind: SourceKind,
    val groupIds: List<String>,
    val notes: String,
    val savedAt: Long,
    val opened: Boolean,
    val status: FetchStatus,
)

data class CookbookData(
    val groups: List<Group> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
)

internal object Json {
    fun encode(data: CookbookData): String = JSONObject().apply {
        put("version", 1)
        put("groups", JSONArray().apply {
            data.groups.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id); put("name", g.name)
                    put("color", g.colorIndex); put("createdAt", g.createdAt); put("emoji", g.emoji)
                })
            }
        })
        put("recipes", JSONArray().apply {
            data.recipes.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("url", r.url); put("title", r.title)
                    put("thumb", r.thumbPath ?: JSONObject.NULL)
                    put("kind", r.kind.name)
                    put("groups", JSONArray(r.groupIds))
                    put("notes", r.notes); put("savedAt", r.savedAt)
                    put("opened", r.opened)
                    // A fetch that was interrupted by process death is treated as failed.
                    put("status", if (r.status == FetchStatus.LOADING) FetchStatus.FAILED.name else r.status.name)
                })
            }
        })
    }.toString()

    fun decode(text: String): CookbookData {
        val o = JSONObject(text)
        val groups = o.optJSONArray("groups")?.let { a ->
            (0 until a.length()).map { i ->
                val g = a.getJSONObject(i)
                val name = g.getString("name")
                Group(g.getString("id"), name, g.optInt("color"), g.optLong("createdAt"), g.optString("emoji").ifBlank { GroupEmoji.guess(name) })
            }
        }.orEmpty()
        val recipes = o.optJSONArray("recipes")?.let { a ->
            (0 until a.length()).map { i ->
                val r = a.getJSONObject(i)
                val gs = r.optJSONArray("groups")
                Recipe(
                    id = r.getString("id"),
                    url = r.getString("url"),
                    title = r.optString("title"),
                    thumbPath = if (r.isNull("thumb")) null else r.optString("thumb"),
                    kind = runCatching { SourceKind.valueOf(r.optString("kind")) }.getOrDefault(SourceKind.LINK),
                    groupIds = gs?.let { (0 until it.length()).map(it::getString) }.orEmpty(),
                    notes = r.optString("notes"),
                    savedAt = r.optLong("savedAt"),
                    opened = r.optBoolean("opened", true),
                    status = runCatching { FetchStatus.valueOf(r.optString("status")) }.getOrDefault(FetchStatus.DONE),
                )
            }
        }.orEmpty()
        return CookbookData(groups, recipes)
    }
}
