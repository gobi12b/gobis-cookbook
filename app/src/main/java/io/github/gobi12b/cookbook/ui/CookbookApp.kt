package io.github.gobi12b.cookbook.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gobi12b.cookbook.CookbookViewModel
import io.github.gobi12b.cookbook.data.InstagramSession
import io.github.gobi12b.cookbook.data.Recipe
import kotlinx.coroutines.delay


@Composable
fun CookbookApp(vm: CookbookViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val composing by vm.composing.collectAsStateWithLifecycle()
    val snack by vm.snack.collectAsStateWithLifecycle()
    val pickerFor by vm.groupPickerFor.collectAsStateWithLifecycle()
    val openRequest by vm.openRequest.collectAsStateWithLifecycle()
    val igConnected by vm.igConnected.collectAsStateWithLifecycle()
    val igLogin by vm.igLogin.collectAsStateWithLifecycle()
    val celebrate by vm.celebrate.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var tab by rememberSaveable { mutableStateOf(Tab.Recipes) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    /** Stack of open recipe ids; Detail → "More from" → Detail pushes onto it. */
    val detailStack = rememberSaveable(saver = listSaver({ it.toList() }, { mutableStateListOf<String>().apply { addAll(it) } })) {
        mutableStateListOf<String>()
    }
    val detail = detailStack.lastOrNull()?.let { id -> data.recipes.firstOrNull { it.id == id } }

    fun open(r: Recipe) {
        vm.markOpened(r.id)
        detailStack.add(r.id)
    }

    LaunchedEffect(openRequest) {
        openRequest?.let { id -> data.recipes.firstOrNull { it.id == id }?.let(::open); vm.consumeOpenRequest() }
    }
    // A recipe that was deleted while open closes its page.
    LaunchedEffect(detailStack.lastOrNull(), detail) { if (detailStack.isNotEmpty() && detail == null) detailStack.removeAt(detailStack.lastIndex) }

    BackHandler(enabled = detailStack.isNotEmpty()) { detailStack.removeAt(detailStack.lastIndex) }
    BackHandler(enabled = detailStack.isEmpty() && tab == Tab.Groups) { tab = Tab.Recipes }

    val navBottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val bottomPadding = NavBarHeight + navBottom

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        AnimatedContent(
            targetState = detail?.id,
            transitionSpec = {
                if (targetState != null) (slideInHorizontally(tween(350, easing = Emphasized)) { it / 4 } + fadeIn(tween(250)))
                    .togetherWith(fadeOut(tween(150)))
                else fadeIn(tween(250)).togetherWith(slideOutHorizontally(tween(300, easing = Emphasized)) { it / 4 } + fadeOut(tween(200)))
            },
            label = "screen",
        ) { id ->
            val r = id?.let { rid -> data.recipes.firstOrNull { it.id == rid } }
            if (r != null) {
                DetailScreen(
                    recipe = r, data = data,
                    onBack = { detailStack.removeAt(detailStack.lastIndex) },
                    onWatch = { openLink(context, r.url) },
                    onGroup = { vm.openGroupPicker(r.id) },
                    onShare = { share(context, r) },
                    onOpenOther = ::open,
                    onNotes = { vm.setNotes(r.id, it) },
                    onTitle = { vm.setTitle(r.id, it) },
                    onDelete = { vm.deleteRecipe(r.id) },
                )
            } else Box(Modifier.fillMaxSize()) {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(120))) }, label = "tab") { t ->
                    when (t) {
                        Tab.Recipes -> RecipesScreen(
                            data = data, filter = filter, onFilter = { filter = it },
                            onOpen = ::open,
                            onChangeGroup = { vm.openGroupPicker(it.id) },
                            onShare = { share(context, it) },
                            onDelete = { vm.deleteRecipe(it.id) },
                            onRetry = { vm.refetch(it.id) },
                            onAdd = vm::startCompose,
                            igConnected = igConnected,
                            onInstagram = { if (igConnected) vm.disconnectInstagram() else vm.openInstagramLogin() },
                            bottomPadding = bottomPadding,
                        )
                        Tab.Groups -> GroupsScreen(
                            data = data,
                            onOpenGroup = { filter = it; tab = Tab.Recipes },
                            onCreate = { n, e -> vm.createGroup(n, e) },
                            onRename = vm::renameGroup,
                            onDelete = { id -> if (filter == id) filter = null; vm.deleteGroup(id) },
                            bottomPadding = bottomPadding,
                        )
                    }
                }
                BottomNav(tab, { tab = it }, vm::startCompose, Modifier.align(Alignment.BottomCenter))
            }
        }

        Confetti(celebrate)

        // ---- snackbar (sits above the bottom nav, like the design)
        val current = snack
        LaunchedEffect(current?.id) {
            if (current != null) { delay(if (current.action != null) 5000 else 3000); vm.dismissSnack(current.id) }
        }
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(tween(500, easing = Emphasized)) { it } + fadeIn(),
            exit = slideOutVertically(tween(250)) { it / 2 } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = if (detail == null) bottomPadding + 14.dp else navBottom + 16.dp),
        ) {
            var shown by remember { mutableStateOf(current) }
            if (current != null) shown = current
            shown?.let { s ->
                CookbookSnack(s.text, s.actionLabel, onAction = { vm.dismissSnack(s.id); s.action?.invoke() })
            }
        }
    }

    // ---- sheets
    if (draft != null || composing) {
        SaveSheet(
            draft = draft, composing = composing, groups = data.groupsByCount(),
            defaultGroupIds = filter?.takeIf { f -> data.groups.any { it.id == f } }?.let(::listOf).orEmpty(),
            onUrl = vm::setComposeUrl,
            onCreateGroup = { n, e -> vm.createGroup(n, e) },
            onSave = { title, groups -> vm.saveDraft(title, groups); detailStack.clear(); tab = Tab.Recipes },
            onCancel = vm::cancelDraft,
            igConnected = igConnected,
            onConnectInstagram = vm::openInstagramLogin,
        )
    }
    if (igLogin) InstagramLoginHost(vm)
    pickerFor?.let { id ->
        val r = data.recipes.firstOrNull { it.id == id }
        if (r == null) vm.closeGroupPicker()
        else GroupPickerSheet(
            groups = data.groupsByCount(), initial = r.groupIds,
            onCreateGroup = { n, e -> vm.createGroup(n, e) },
            onDone = { vm.setGroups(id, it); vm.closeGroupPicker() },
            onDismiss = vm::closeGroupPicker,
        )
    }
}

@Composable
private fun InstagramLoginHost(vm: CookbookViewModel) {
    InstagramLoginDialog(onConnected = vm::onInstagramConnected, onDismiss = vm::closeInstagramLogin)
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun share(context: Context, r: Recipe) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, r.title)
        putExtra(Intent.EXTRA_TEXT, if (r.notes.isBlank()) "${r.title}\n${r.url}" else "${r.title}\n${r.url}\n\n${r.notes}")
    }
    context.startActivity(Intent.createChooser(send, null))
}
