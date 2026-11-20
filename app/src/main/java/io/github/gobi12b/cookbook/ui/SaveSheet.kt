package io.github.gobi12b.cookbook.ui

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.cookbook.Draft
import io.github.gobi12b.cookbook.data.AutoTag
import io.github.gobi12b.cookbook.data.FetchStatus
import io.github.gobi12b.cookbook.data.Group
import io.github.gobi12b.cookbook.data.InstagramSession
import io.github.gobi12b.cookbook.data.LinkMetadata
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SaveSheet(
    draft: Draft?,
    composing: Boolean,
    groups: List<Group>,
    defaultGroupIds: List<String>,
    onUrl: (String) -> Unit,
    onCreateGroup: (String, String) -> Group?,
    onSave: (title: String, groupIds: List<String>) -> Unit,
    onCancel: () -> Unit,
    igConnected: Boolean,
    onConnectInstagram: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var urlText by remember { mutableStateOf("") }
    if (composing) LaunchedEffect(Unit) {
        // Pre-fill from the clipboard when it holds a link.
        val clip = runCatching {
            context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
        LinkMetadata.extractUrl(clip)?.let { urlText = it; onUrl(it) }
    }

    var title by remember(draft?.id) { mutableStateOf(draft?.sharedTitle.orEmpty()) }
    var edited by remember(draft?.id) { mutableStateOf(false) }
    LaunchedEffect(draft?.meta) {
        val t = draft?.meta?.title
        if (!edited && t != null) title = t
    }

    // ---- group selection: source tag + caption-based picks + anything the user taps
    val source = draft?.let { AutoTag.sourceFor(it.url) }
    val sourceGroup = source?.let { (name, _) -> groups.firstOrNull { it.name.equals(name, ignoreCase = true) } }
    val sourceChipId = sourceGroup?.id ?: source?.let { AutoTag.SOURCE_PREFIX + it.first }
    val selected = remember(draft?.id) {
        mutableStateListOf<String>().apply {
            addAll(defaultGroupIds.filter { id -> groups.any { it.id == id } })
            sourceChipId?.let { if (it !in this) add(it) }
        }
    }
    val removedByUser = remember(draft?.id) { mutableStateListOf<String>() }
    val autoPicked = remember(draft?.id) { mutableStateListOf<String>() }
    LaunchedEffect(draft?.id, draft?.meta, groups.size) {
        val text = listOfNotNull(draft?.meta?.caption, draft?.meta?.title, draft?.sharedTitle).joinToString("\n")
        AutoTag.suggest(text, groups).forEach { id ->
            if (id !in removedByUser && id !in selected) { selected.add(id); autoPicked.add(id) }
        }
    }
    fun toggle(id: String) {
        if (id in selected) { selected.remove(id); removedByUser.add(id) } else { selected.add(id); removedByUser.remove(id) }
    }

    var naming by remember { mutableStateOf(false) }
    val urlFocus = remember { FocusRequester() }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(draft?.status) {
        if (draft?.status == FetchStatus.FAILED && title.isBlank()) { delay(250); runCatching { titleFocus.requestFocus() } }
    }

    ModalBottomSheet(
        onDismissRequest = onCancel, sheetState = sheet, containerColor = C.Bg, scrimColor = C.Scrim,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            T("Save this recipe 😋", size = 28.sp, weight = FontWeight.SemiBold, display = true)

            if (composing) Column {
                FieldLabel("🔗 Link")
                OutlinedInput(
                    urlText, { urlText = it; onUrl(it) }, "Paste a YouTube or Instagram link",
                    Modifier.padding(top = 8.dp), imeAction = ImeAction.Next, focusRequester = urlFocus,
                )
                if (urlText.isNotBlank() && draft == null) {
                    T("Hmm, that doesn’t look like a link 🤔", size = 13.sp, weight = FontWeight.SemiBold, color = C.Accent, modifier = Modifier.padding(top = 6.dp))
                }
            }

            if (draft != null) Enter(100, tilt = -6f) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Chunky(radius = 18.dp, shadow = 3.dp) {
                        Thumb(
                            draft.thumbPath, 1,
                            Modifier.padding(5.dp).size(92.dp, 116.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, C.Ink, RoundedCornerShape(14.dp)),
                            emoji = if (draft.status == FetchStatus.LOADING) null else "🍳", emojiSize = 36.sp,
                        ) {
                            if (draft.status == FetchStatus.LOADING) {
                                Shimmer(Modifier.fillMaxSize())
                                Text("🍳", fontSize = 30.sp, modifier = Modifier.align(Alignment.Center).bob(4.dp, 16f, 1100))
                            }
                        }
                    }
                    AnimatedContent(draft.status, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") { s ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (s) {
                                FetchStatus.LOADING -> T("Sniffing out the title… 👃", size = 16.sp, weight = FontWeight.Medium, display = true, color = C.TextDim)
                                FetchStatus.DONE -> T(
                                    if (draft.thumbPath != null && draft.meta?.title != null) "Got it! Title & photo found 🎯"
                                    else if (draft.meta?.title != null) "Found the title 🎯" else "Found a photo 📸",
                                    size = 16.sp, weight = FontWeight.Medium, display = true,
                                )
                                FetchStatus.FAILED -> {
                                    if (InstagramSession.isInstagram(draft.url)) {
                                        T("Couldn’t peek at this reel — it may be private 🔒", size = 15.sp, weight = FontWeight.Medium, display = true, color = C.TextDim)
                                        if (!igConnected) T("Connect Instagram →", size = 15.sp, weight = FontWeight.SemiBold, display = true, color = C.Accent,
                                            modifier = Modifier.pressable(onClick = onConnectInstagram))
                                    } else T("Couldn’t read this link — type a title ✍️", size = 15.sp, weight = FontWeight.Medium, display = true, color = C.TextDim)
                                }
                            }
                        }
                    }
                }
            }

            Column {
                FieldLabel("✏️ Title")
                OutlinedInput(
                    title, { title = it; edited = true },
                    if (draft?.status == FetchStatus.LOADING) "Fetching title…" else "What’s cooking?",
                    Modifier.padding(top = 8.dp), focusRequester = titleFocus,
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel("🗂️ Groups")
                    if (autoPicked.any { it in selected }) T("  ✨ auto-picked from the caption", size = 12.sp, weight = FontWeight.SemiBold, color = C.TextDim)
                }
                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Source tag first ("▶️ YouTube"), even before it exists as a group.
                    if (source != null && sourceChipId != null) {
                        GroupToggleChip(source.first, sourceChipId in selected, emoji = source.second) { toggle(sourceChipId) }
                    }
                    groups.filter { it.id != sourceGroup?.id }.forEach { g ->
                        GroupToggleChip(g.name, g.id in selected, emoji = g.emoji, auto = g.id in autoPicked) { toggle(g.id) }
                    }
                    GroupToggleChip("New group", false, special = true) { naming = true }
                }
            }

            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Cancel", false, modifier = Modifier.weight(1f), onClick = onCancel)
                BigButton("Save it! 🎉", true, enabled = draft != null, modifier = Modifier.weight(1.3f)) {
                    onSave(title, selected.toList())
                }
            }
        }
    }

    if (naming) NameDialog("New group", "", null, "Create", { naming = false }) { name, emoji ->
        onCreateGroup(name, emoji)?.let { if (it.id !in selected) selected.add(it.id) }
        naming = false
    }

    if (composing && urlText.isEmpty()) LaunchedEffect(Unit) { delay(300); runCatching { urlFocus.requestFocus() } }
}

