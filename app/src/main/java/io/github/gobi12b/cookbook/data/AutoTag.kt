package io.github.gobi12b.cookbook.data

/**
 * Automatic group picks for a new recipe:
 *  - a source tag ("▶️ YouTube", "📸 Instagram", …) from the link itself, and
 *  - topic groups whose keywords appear in the caption / title / hashtags.
 */
object AutoTag {
    /** Prefix for a source tag that doesn't exist as a group yet; created on save. */
    const val SOURCE_PREFIX = "src:"

    private val sources = listOf(
        listOf("youtube.com", "youtu.be") to ("YouTube" to "▶️"),
        listOf("instagram.com") to ("Instagram" to "📸"),
        listOf("tiktok.com") to ("TikTok" to "🎵"),
        listOf("facebook.com", "fb.watch") to ("Facebook" to "👍"),
    )

    /** (name, emoji) of the source tag for a link, if it comes from a known site. */
    fun sourceFor(url: String): Pair<String, String>? {
        val u = url.lowercase()
        return sources.firstOrNull { (hosts, _) -> hosts.any { it in u } }?.second
    }

    fun isSourceName(name: String) = sources.any { it.second.first.equals(name, ignoreCase = true) }

    /** Ids of existing topic groups that the text mentions. */
    fun suggest(text: String?, groups: List<Group>): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val t = text.lowercase()
        return groups.filter { g ->
            !isSourceName(g.name) && GroupEmoji.keywordsFor(g.name).any { it in t }
        }.map { it.id }
    }
}
