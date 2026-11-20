package io.github.gobi12b.cookbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.cookbook.data.CookbookData
import io.github.gobi12b.cookbook.data.FetchStatus
import io.github.gobi12b.cookbook.data.Group
import io.github.gobi12b.cookbook.data.Recipe
import io.github.gobi12b.cookbook.data.SourceKind

const val UNSORTED = "__unsorted__"
private const val NEW_WINDOW_MS = 24 * 60 * 60 * 1000L

fun Recipe.matchesFilter(filter: String?) = when (filter) {
    null -> true
    UNSORTED -> groupIds.isEmpty()
    else -> filter in groupIds
}

fun Recipe.isNew(now: Long = System.currentTimeMillis()) = !opened && now - savedAt < NEW_WINDOW_MS

/** Portrait for reels/shorts, landscape-ish for regular videos — gives the grid its rhythm. */
fun SourceKind.cardRatio() = when (this) {
    SourceKind.SHORT, SourceKind.REEL -> 0.8f
    SourceKind.POST -> 1f
    else -> 1.25f
}

/** First topic group of a recipe (source tags like "YouTube" only if there's nothing else). */
fun Recipe.mainGroup(groupsById: Map<String, Group>): Group? {
    val gs = groupIds.mapNotNull(groupsById::get)
    return gs.firstOrNull { !io.github.gobi12b.cookbook.data.AutoTag.isSourceName(it.name) } ?: gs.firstOrNull()
}

@Composable
fun RecipesScreen(
    data: CookbookData,
    filter: String?,
    onFilter: (String?) -> Unit,
    onOpen: (Recipe) -> Unit,
    onChangeGroup: (Recipe) -> Unit,
    onShare: (Recipe) -> Unit,
    onDelete: (Recipe) -> Unit,
    onRetry: (Recipe) -> Unit,
    onAdd: () -> Unit,
    igConnected: Boolean,
    onInstagram: () -> Unit,
    bottomPadding: Dp,
) {
    var query by remember { mutableStateOf("") }
    var topMenu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val groupsById = remember(data.groups) { data.groups.associateBy { it.id } }
    val hasUnsorted = data.recipes.any { it.groupIds.isEmpty() }
    val chipGroups = remember(data) { data.groupsByCount() }

    val visible = remember(data.recipes, filter, query) {
        val q = query.trim()
        data.recipes.filter { r ->
            r.matchesFilter(filter) && (q.isEmpty() || r.title.contains(q, true) || r.notes.contains(q, true) ||
                r.groupIds.any { groupsById[it]?.name?.contains(q, true) == true })
        }
    }

    androidx.activity.compose.BackHandler(enabled = query.isNotEmpty()) { query = ""; focus.clearFocus() }

    val gridState = rememberLazyStaggeredGridState()
    LaunchedEffect(filter) { gridState.scrollToItem(0) }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize().background(C.Bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding + 28.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalItemSpacing = 22.dp,
    ) {
        // ---- masthead
        item(key = "header", span = StaggeredGridItemSpan.FullLine) {
            Column(Modifier.statusBarsPadding().padding(top = 16.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 2.dp)) {
                        Tape("Gobi’s", modifier = Modifier.padding(start = 2.dp))
                        T("Cookbook", size = 46.sp, weight = FontWeight.SemiBold, display = true, lineHeight = 50.sp,
                            modifier = Modifier.padding(top = 2.dp))
                        T(
                            when (val n = data.recipes.size) {
                                0 -> "Nothing cooking yet 🥄"; 1 -> "1 tasty recipe saved 😋"; else -> "$n tasty recipes saved 😋"
                            },
                            size = 14.sp, weight = FontWeight.Medium, color = C.TextDim,
                        )
                    }
                    Sticker("🍳", 54.dp, rotation = 12f, fill = C.Sun,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 60.dp, top = 8.dp).bob(5.dp, 8f, 2600))
                    Box(Modifier.align(Alignment.TopEnd)) {
                        CircleBtn(Icons.More, "More options") { topMenu = true }
                        DropdownMenu(topMenu, { topMenu = false }, containerColor = C.Card, shape = RoundedCornerShape(18.dp)) {
                            DropdownMenuItem(
                                { T(if (igConnected) "📸  Disconnect Instagram" else "📸  Connect Instagram", weight = FontWeight.SemiBold) },
                                { topMenu = false; onInstagram() },
                            )
                        }
                    }
                }
                if (data.recipes.isNotEmpty()) SearchField(query, { query = it }, focus, Modifier.padding(top = 18.dp))
            }
        }

        // ---- group filters
        if (data.recipes.isNotEmpty()) item(key = "chips", span = StaggeredGridItemSpan.FullLine) {
            LazyRow(
                Modifier.bleed(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { FilterChip("All", filter == null, "🍽️") { onFilter(null) } }
                items(chipGroups, key = { it.id }) { g -> FilterChip(g.name, filter == g.id, g.emoji) { onFilter(g.id) } }
                if (hasUnsorted || filter == UNSORTED) item { FilterChip("Unsorted", filter == UNSORTED, "🫙") { onFilter(UNSORTED) } }
            }
        }

        if (visible.isEmpty()) item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
            EmptyState(
                when {
                    query.isNotBlank() -> "No crumbs match “${query.trim()}”"
                    data.recipes.isEmpty() -> null
                    else -> "This group is still hungry"
                },
                onAdd,
            )
        }

        itemsIndexed(visible, key = { _, r -> r.id }) { index, r ->
            Enter(delayMs = index.coerceAtMost(8) * 70, tilt = if (index % 2 == 0) -6f else 6f, modifier = Modifier.animateItem()) {
                RecipeCard(
                    recipe = r,
                    group = r.mainGroup(groupsById),
                    onOpen = { onOpen(r) }, onChangeGroup = { onChangeGroup(r) },
                    onShare = { onShare(r) }, onDelete = { onDelete(r) }, onRetry = { onRetry(r) },
                )
            }
        }
    }
}

/** Lets a full-width row extend past the grid's side padding to the screen edges. */
private fun Modifier.bleed(by: Dp) = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, focus: FocusManager, modifier: Modifier) {
    Chunky(modifier.fillMaxWidth().height(54.dp), radius = 18.dp, shadow = 3.dp) {
        BasicTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            textStyle = TextStyle(color = C.Text, fontSize = 16.sp, fontFamily = Fonts.Sans, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(C.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.fillMaxSize(),
            decorationBox = { inner ->
                Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔎", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) T("Craving something?", size = 16.sp, color = C.TextDim, maxLines = 1)
                        inner()
                    }
                    if (query.isNotEmpty()) IconBtn(Icons.Close, "Clear search", 18.dp) { onQuery(""); focus.clearFocus() }
                }
            },
        )
    }
}

@Composable
private fun EmptyState(message: String?, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (message != null) {
            Sticker("🔍", 64.dp, rotation = -8f, fill = C.Sun, modifier = Modifier.bob(4.dp, 8f))
            Spacer(Modifier.height(16.dp))
            T(message, size = 18.sp, weight = FontWeight.Medium, display = true, color = C.TextDim)
            return@Column
        }
        Box(Modifier.size(220.dp, 150.dp)) {
            Sticker("🥑", 70.dp, -14f, C.tile(2), Modifier.align(Alignment.CenterStart).bob(6.dp, 8f, 2800, 0f))
            Sticker("🍳", 92.dp, 6f, C.Sun, Modifier.align(Alignment.Center).bob(5.dp, 6f, 2400, .33f))
            Sticker("🌶️", 66.dp, 16f, C.tile(0), Modifier.align(Alignment.CenterEnd).bob(6.dp, 10f, 3000, .66f))
        }
        Spacer(Modifier.height(18.dp))
        T("Your cookbook is hungry!", size = 26.sp, weight = FontWeight.SemiBold, display = true)
        Spacer(Modifier.height(10.dp))
        Text(
            "Spotted something yummy on YouTube or Instagram? Tap Share → Gobi’s Cookbook, or paste a link.",
            fontSize = 15.sp, color = C.TextDim, textAlign = TextAlign.Center, lineHeight = 22.sp, fontFamily = Fonts.Sans,
        )
        Spacer(Modifier.height(24.dp))
        Pill("Paste a link", emoji = "🔗", primary = true, onClick = onAdd)
    }
}

@Composable
fun RecipeCard(
    recipe: Recipe,
    group: Group?,
    onOpen: () -> Unit,
    onChangeGroup: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    if (recipe.status == FetchStatus.LOADING && recipe.title.isBlank()) {
        LoadingCard(recipe.kind.cardRatio())
        return
    }
    var menu by remember { mutableStateOf(false) }

    Box {
        Chunky(Modifier.fillMaxWidth(), radius = 22.dp, onClick = onOpen) {
            Column {
                Box(Modifier.padding(8.dp)) {
                    Thumb(
                        recipe.thumbPath, group?.colorIndex,
                        Modifier.fillMaxWidth().aspectRatio(recipe.kind.cardRatio()).clip(RoundedCornerShape(16.dp))
                            .border(2.dp, C.Ink, RoundedCornerShape(16.dp)),
                        emoji = group?.emoji ?: "🍳",
                    ) {
                        if (recipe.status == FetchStatus.LOADING) Shimmer(Modifier.fillMaxSize())
                        SourceTag(recipe.url, recipe.kind, Modifier.align(Alignment.BottomStart).padding(7.dp))
                    }
                }
                T(
                    recipe.title, size = 17.sp, weight = FontWeight.SemiBold, display = true, lineHeight = 21.sp, maxLines = 3,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp),
                )
                Row(Modifier.padding(start = 12.dp, end = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(
                        "${group?.emoji ?: "🫙"} ${group?.name ?: "Unsorted"} · ${relativeTime(recipe.savedAt)}",
                        size = 12.sp, weight = FontWeight.Medium, color = C.TextDim, maxLines = 1, modifier = Modifier.weight(1f),
                    )
                    Box {
                        Box(Modifier.size(40.dp).pressable { menu = true }, contentAlignment = Alignment.Center) {
                            Ic(Icons.More, 18.dp, C.TextDim)
                        }
                        CardMenu(menu, { menu = false }, onChangeGroup, onShare, onDelete)
                    }
                }
                if (recipe.status != FetchStatus.LOADING && recipe.thumbPath == null) {
                    T(
                        "🔁 Preview missing · Retry", size = 12.sp, weight = FontWeight.Bold, color = C.Accent,
                        modifier = Modifier.padding(start = 12.dp, bottom = 10.dp).pressable(onClick = onRetry),
                    )
                }
            }
        }
        if (recipe.isNew()) NewBurst(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-10).dp))
    }
}

@Composable
fun CardMenu(expanded: Boolean, onDismiss: () -> Unit, onChangeGroup: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    DropdownMenu(expanded, onDismiss, containerColor = C.Card, shape = RoundedCornerShape(18.dp)) {
        DropdownMenuItem({ T("🗂️  Move to group", weight = FontWeight.SemiBold) }, { onDismiss(); onChangeGroup() })
        DropdownMenuItem({ T("📤  Share", weight = FontWeight.SemiBold) }, { onDismiss(); onShare() })
        DropdownMenuItem({ T("🗑️  Remove", weight = FontWeight.SemiBold, color = C.Accent) }, { onDismiss(); onDelete() })
    }
}

@Composable
fun LoadingCard(ratio: Float = 1f) {
    Chunky(Modifier.fillMaxWidth(), radius = 22.dp) {
        Column(Modifier.padding(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(16.dp))) {
                Shimmer(Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🍳", fontSize = 30.sp, modifier = Modifier.bob(4.dp, 14f, 1200))
                    T("Cooking up a preview…", size = 12.sp, weight = FontWeight.SemiBold, color = C.TextDim)
                }
            }
            Shimmer(Modifier.padding(top = 12.dp, start = 4.dp).fillMaxWidth(.85f).height(14.dp).clip(RoundedCornerShape(7.dp)))
            Shimmer(Modifier.padding(top = 8.dp, start = 4.dp, bottom = 6.dp).fillMaxWidth(.5f).height(12.dp).clip(RoundedCornerShape(6.dp)))
        }
    }
}
