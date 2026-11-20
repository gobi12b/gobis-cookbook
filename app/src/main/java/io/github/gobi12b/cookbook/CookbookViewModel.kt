package io.github.gobi12b.cookbook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gobi12b.cookbook.data.AutoTag
import io.github.gobi12b.cookbook.data.CookbookData
import io.github.gobi12b.cookbook.data.FetchStatus
import io.github.gobi12b.cookbook.data.Group
import io.github.gobi12b.cookbook.data.GroupEmoji
import io.github.gobi12b.cookbook.data.InstagramSession
import io.github.gobi12b.cookbook.data.LinkMeta
import io.github.gobi12b.cookbook.data.LinkMetadata
import io.github.gobi12b.cookbook.data.Recipe
import io.github.gobi12b.cookbook.data.RecipeStore
import io.github.gobi12b.cookbook.data.SourceKind
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/** A link that is being saved from the share sheet (or the in-app "+" button). */
data class Draft(
    val id: String,
    val url: String,
    val sharedTitle: String?,
    val kind: SourceKind,
    val status: FetchStatus,
    val meta: LinkMeta? = null,
    val thumbPath: String? = null,
)

data class SnackMessage(val id: Long, val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

class CookbookViewModel(app: Application) : AndroidViewModel(app) {
    private val store = RecipeStore(app)
    private val prefs = app.getSharedPreferences("cookbook", 0)

    /** Groups picked on the previous save; pre-selected next time. */
    val lastGroupIds: List<String>
        get() = prefs.getString("lastGroups", "").orEmpty().split(',').filter { it.isNotEmpty() }

    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<CookbookData> = _data.asStateFlow()

    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft.asStateFlow()
    private var draftJob: Deferred<Pair<LinkMeta?, String?>>? = null

    /** True when the save sheet was opened from the "+" button and needs a URL field. */
    private val _composing = MutableStateFlow(false)
    val composing: StateFlow<Boolean> = _composing.asStateFlow()

    /** Bumped on every save; the UI fires a confetti burst when it changes. */
    private val _celebrate = MutableStateFlow(0)
    val celebrate: StateFlow<Int> = _celebrate.asStateFlow()

    private val _snack = MutableStateFlow<SnackMessage?>(null)
    val snack: StateFlow<SnackMessage?> = _snack.asStateFlow()

    /** Recipe whose groups are being edited in the group picker sheet. */
    private val _groupPickerFor = MutableStateFlow<String?>(null)
    val groupPickerFor: StateFlow<String?> = _groupPickerFor.asStateFlow()

    /** Recipe the UI should navigate to (e.g. after tapping "Open" on a duplicate). */
    private val _openRequest = MutableStateFlow<String?>(null)
    val openRequest: StateFlow<String?> = _openRequest.asStateFlow()

    private val _igConnected = MutableStateFlow(InstagramSession.isConnected())
    val igConnected: StateFlow<Boolean> = _igConnected.asStateFlow()
    private val _igLogin = MutableStateFlow(false)
    val igLogin: StateFlow<Boolean> = _igLogin.asStateFlow()

    init {
        // Delete thumbnails that no longer belong to any recipe.
        viewModelScope.launch(Dispatchers.IO) {
            val keep = _data.value.recipes.mapNotNull { it.thumbPath }.toSet()
            store.thumbDir.listFiles()?.filter { it.absolutePath !in keep }?.forEach { it.delete() }
            // One-time: strip letterbox bars from thumbnails saved before trimming existed.
            if (!prefs.getBoolean("thumbsTrimmed", false)) {
                keep.forEach { path ->
                    runCatching {
                        val raw = android.graphics.BitmapFactory.decodeFile(path) ?: return@runCatching
                        val trimmed = LinkMetadata.trimLetterbox(raw)
                        if (trimmed !== raw) java.io.File(path).outputStream().use {
                            trimmed.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
                        }
                    }
                }
                prefs.edit().putBoolean("thumbsTrimmed", true).apply()
            }
        }
        // One-time: strip hashtags from titles saved before titles were cleaned.
        if (!prefs.getBoolean("titlesCleaned", false)) {
            if (_data.value.recipes.any { '#' in it.title }) mutate { d ->
                d.copy(recipes = d.recipes.map { r ->
                    if ('#' in r.title) r.copy(title = LinkMetadata.cleanTitle(r.title) ?: r.title) else r
                })
            }
            prefs.edit().putBoolean("titlesCleaned", true).apply()
        }
        // Retry links whose metadata never arrived (offline at save time, process killed, …).
        _data.value.recipes.filter {
            (it.status == FetchStatus.FAILED && it.thumbPath == null) ||
                (InstagramSession.isInstagram(it.url) && (it.thumbPath == null || LinkMetadata.isGenericTitle(it.title)))
        }.forEach { refetch(it.id) }
    }

    // ---------------------------------------------------------------- share / save

    fun handleSharedText(text: String?, subject: String?) {
        val url = LinkMetadata.extractUrl(text)
        if (url == null) {
            showSnack("No link found in what was shared")
            return
        }
        _composing.value = false
        startDraft(url, cleanSubject(subject) ?: text?.replace(url, "")?.trim()?.takeIf { it.length in 3..120 })
    }

    fun startCompose() {
        cancelDraft()
        _composing.value = true
    }

    /** Called as the user types/pastes a URL in compose mode. */
    fun setComposeUrl(text: String) {
        val url = LinkMetadata.extractUrl(text)
        if (url == _draft.value?.url) return
        draftJob?.cancel()
        if (url == null) { _draft.value = null; return }
        startDraft(url, null)
    }

    private fun startDraft(url: String, sharedTitle: String?) {
        _data.value.recipes.firstOrNull { it.url == url }?.let { existing ->
            _composing.value = false
            _draft.value = null
            showSnack("Already in your cookbook", "Open") { _openRequest.value = existing.id }
            return
        }
        draftJob?.cancel()
        val id = RecipeStore.newId()
        _draft.value = Draft(id, url, sharedTitle, SourceKind.fromUrl(url), FetchStatus.LOADING)
        draftJob = viewModelScope.async {
            val result = fetchInto(id, url)
            _draft.update { d -> if (d?.id == id) d.copy(meta = result.first, thumbPath = result.second, status = statusOf(result)) else d }
            result
        }
    }

    fun cancelDraft() {
        val d = _draft.value
        draftJob?.cancel()
        draftJob = null
        _draft.value = null
        _composing.value = false
        if (d != null) viewModelScope.launch(Dispatchers.IO) { store.thumbFile(d.id).delete() }
    }

    fun saveDraft(title: String, chosenIds: List<String>) {
        val d = _draft.value ?: return
        // Source tags ("src:YouTube") become real groups the first time they're used.
        val groupIds = chosenIds.mapNotNull { id ->
            if (id.startsWith(AutoTag.SOURCE_PREFIX)) {
                AutoTag.sourceFor(d.url)?.let { (name, emoji) -> createGroup(name, emoji)?.id }
            } else id
        }.distinct()
        val job = draftJob
        _draft.value = null
        _composing.value = false
        draftJob = null

        val done = d.status != FetchStatus.LOADING
        val recipe = Recipe(
            id = d.id,
            url = d.url,
            title = title.trim().ifBlank { if (done) fallbackTitle(d) else "" },
            thumbPath = d.thumbPath,
            kind = d.kind,
            groupIds = groupIds,
            notes = "",
            savedAt = System.currentTimeMillis(),
            opened = false,
            status = if (done) d.status else FetchStatus.LOADING,
        )
        mutate { it.copy(recipes = listOf(recipe) + it.recipes) }
        prefs.edit().putString("lastGroups", groupIds.joinToString(",")).apply()

        if (!done && job != null) viewModelScope.launch {
            val result = runCatching { job.await() }.getOrDefault(null to null)
            updateRecipe(recipe.id) { r ->
                r.copy(
                    title = r.title.ifBlank { result.first?.title ?: fallbackTitle(d) },
                    thumbPath = result.second,
                    status = statusOf(result),
                )
            }
        }

        // Name the topic group in the snackbar ("Dinner"), not the source tag ("YouTube"), when there is one.
        val chosen = groupIds.mapNotNull { gid -> _data.value.groups.firstOrNull { it.id == gid } }
        val group = chosen.firstOrNull { !AutoTag.isSourceName(it.name) } ?: chosen.firstOrNull()
        _celebrate.value++
        showSnack(if (group != null) "Yum! Saved to ${group.emoji} ${group.name}" else "Yum! Saved to your cookbook", if (group != null) "Change" else "Add group") {
            _groupPickerFor.value = recipe.id
        }
    }

    fun refetch(recipeId: String) {
        val r = _data.value.recipes.firstOrNull { it.id == recipeId } ?: return
        updateRecipe(recipeId) { it.copy(status = FetchStatus.LOADING) }
        viewModelScope.launch {
            val result = fetchInto(r.id, r.url)
            updateRecipe(recipeId) {
                it.copy(
                    title = if (it.title.isBlank() || it.title == hostOf(it.url) || LinkMetadata.isGenericTitle(it.title)) result.first?.title ?: it.title.ifBlank { hostOf(it.url) } else it.title,
                    thumbPath = result.second ?: it.thumbPath,
                    status = statusOf(result),
                )
            }
        }
    }

    // ---------------------------------------------------------------- instagram

    fun openInstagramLogin() { _igLogin.value = true }
    fun closeInstagramLogin() { _igLogin.value = false }

    fun onInstagramConnected() {
        _igLogin.value = false
        _igConnected.value = true
        showSnack("Instagram connected")
        // Retry the open draft and any Instagram recipe saved without a preview.
        _draft.value?.takeIf { InstagramSession.isInstagram(it.url) && it.status != FetchStatus.LOADING }?.let { retryDraft() }
        _data.value.recipes
            .filter { InstagramSession.isInstagram(it.url) && (it.thumbPath == null || LinkMetadata.isGenericTitle(it.title)) }
            .forEach { refetch(it.id) }
    }

    fun disconnectInstagram() {
        InstagramSession.disconnect()
        _igConnected.value = false
        showSnack("Instagram disconnected")
    }

    fun retryDraft() {
        val d = _draft.value ?: return
        draftJob?.cancel()
        _draft.value = d.copy(status = FetchStatus.LOADING)
        draftJob = viewModelScope.async {
            val result = fetchInto(d.id, d.url)
            _draft.update { cur -> if (cur?.id == d.id) cur.copy(meta = result.first, thumbPath = result.second ?: cur.thumbPath, status = statusOf(result)) else cur }
            result
        }
    }

    private suspend fun fetchInto(id: String, url: String): Pair<LinkMeta?, String?> {
        val meta = runCatching {
            if (InstagramSession.isInstagram(url)) InstagramSession.fetch(getApplication(), url)
            else LinkMetadata.fetch(url)
        }.getOrNull()
        val thumb = meta?.thumbnailUrl?.let { img ->
            val f = store.thumbFile(id)
            if (LinkMetadata.downloadThumbnail(img, f)) f.absolutePath else null
        }
        return meta to thumb
    }

    private fun statusOf(r: Pair<LinkMeta?, String?>) =
        if (r.first?.title != null || r.second != null) FetchStatus.DONE else FetchStatus.FAILED

    private fun fallbackTitle(d: Draft) = d.meta?.title ?: d.sharedTitle ?: hostOf(d.url)

    private fun hostOf(url: String): String {
        val host = runCatching { URI(url).host?.removePrefix("www.")?.removePrefix("m.") }.getOrNull() ?: return "Saved link"
        return when {
            "instagram.com" in host -> "Instagram ${SourceKind.fromUrl(url).label.lowercase()}"
            "tiktok.com" in host -> "TikTok video"
            "facebook.com" in host || "fb.watch" in host -> "Facebook ${SourceKind.fromUrl(url).label.lowercase()}"
            else -> host
        }
    }

    private fun cleanSubject(s: String?): String? {
        if (s.isNullOrBlank()) return null
        Regex("""^Watch "(.+)" on YouTube$""").find(s.trim())?.let { return it.groupValues[1] }
        return s.trim().takeIf { LinkMetadata.extractUrl(it) == null }
    }

    // ---------------------------------------------------------------- recipe edits

    fun markOpened(id: String) = updateRecipe(id) { if (it.opened) it else it.copy(opened = true) }

    fun setTitle(id: String, title: String) = updateRecipe(id) { it.copy(title = title) }

    fun setNotes(id: String, notes: String) = updateRecipe(id) { it.copy(notes = notes) }

    fun setGroups(id: String, groupIds: List<String>) = updateRecipe(id) { it.copy(groupIds = groupIds) }

    fun openGroupPicker(id: String) { _groupPickerFor.value = id }
    fun closeGroupPicker() { _groupPickerFor.value = null }
    fun consumeOpenRequest() { _openRequest.value = null }

    fun deleteRecipe(id: String) {
        val before = _data.value
        val index = before.recipes.indexOfFirst { it.id == id }
        if (index < 0) return
        val removed = before.recipes[index]
        mutate { it.copy(recipes = it.recipes.filterNot { r -> r.id == id }) }
        showSnack("Recipe tossed 🗑️", "Undo") {
            mutate { cur ->
                val list = cur.recipes.toMutableList()
                list.add(index.coerceAtMost(list.size), removed)
                cur.copy(recipes = list)
            }
        }
    }

    // ---------------------------------------------------------------- groups

    fun createGroup(name: String, emoji: String? = null): Group? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        _data.value.groups.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
        val g = Group(RecipeStore.newId(), trimmed, nextColor(), System.currentTimeMillis(), emoji ?: GroupEmoji.guess(trimmed))
        mutate { it.copy(groups = it.groups + g) }
        return g
    }

    fun renameGroup(id: String, name: String, emoji: String) {
        if (name.isBlank()) return
        mutate { d -> d.copy(groups = d.groups.map { if (it.id == id) it.copy(name = name.trim(), emoji = emoji) else it }) }
    }

    fun deleteGroup(id: String) {
        val before = _data.value
        val g = before.groups.firstOrNull { it.id == id } ?: return
        mutate { d ->
            d.copy(
                groups = d.groups.filterNot { it.id == id },
                recipes = d.recipes.map { r -> if (id in r.groupIds) r.copy(groupIds = r.groupIds - id) else r },
            )
        }
        showSnack("Bye-bye ${g.emoji} ${g.name}", "Undo") {
            mutate { cur ->
                val members = before.recipes.filter { id in it.groupIds }.map { it.id }.toSet()
                cur.copy(
                    groups = (cur.groups + g).sortedBy { it.createdAt },
                    recipes = cur.recipes.map { r -> if (r.id in members && id !in r.groupIds) r.copy(groupIds = r.groupIds + id) else r },
                )
            }
        }
    }

    private fun nextColor(): Int {
        val used = _data.value.groups.map { it.colorIndex }
        return (0 until 6).minByOrNull { c -> used.count { it == c } } ?: 0
    }

    // ---------------------------------------------------------------- snackbar

    fun showSnack(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _snack.value = SnackMessage(System.nanoTime(), text, actionLabel, action)
    }

    fun dismissSnack(id: Long) = _snack.update { if (it?.id == id) null else it }

    // ---------------------------------------------------------------- plumbing

    private fun updateRecipe(id: String, f: (Recipe) -> Recipe) =
        mutate { d -> d.copy(recipes = d.recipes.map { if (it.id == id) f(it) else it }) }

    private fun mutate(f: (CookbookData) -> CookbookData) {
        _data.update(f)
        viewModelScope.launch { withContext(Dispatchers.IO) { store.save(_data.value) } }
    }
}
