package io.github.gobi12b.cookbook.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.cookbook.data.CookbookData
import io.github.gobi12b.cookbook.data.Recipe
import io.github.gobi12b.cookbook.data.SourceKind
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    recipe: Recipe,
    data: CookbookData,
    onBack: () -> Unit,
    onWatch: () -> Unit,
    onGroup: () -> Unit,
    onShare: () -> Unit,
    onOpenOther: (Recipe) -> Unit,
    onNotes: (String) -> Unit,
    onTitle: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val groupsById = remember(data.groups) { data.groups.associateBy { it.id } }
    val main = recipe.mainGroup(groupsById)
    val groups = recipe.groupIds.mapNotNull(groupsById::get)
    var editing by rememberSaveable(recipe.id) { mutableStateOf(false) }

    // Notes are edited locally and written back after a short pause in typing.
    var notes by rememberSaveable(recipe.id) { mutableStateOf(recipe.notes) }
    LaunchedEffect(notes) { if (notes != recipe.notes) { delay(400); onNotes(notes) } }
    var title by rememberSaveable(recipe.id) { mutableStateOf(recipe.title) }
    LaunchedEffect(recipe.title) { if (!editing) title = recipe.title }

    val more = remember(data.recipes, recipe.id, main) {
        data.recipes.filter { it.id != recipe.id && it.matchesFilter(main?.id ?: UNSORTED) }.take(8)
    }
    val src = sourceOf(recipe.url)
    val tall = recipe.kind == SourceKind.SHORT || recipe.kind == SourceKind.REEL

    Column(Modifier.fillMaxSize().background(C.Bg).verticalScroll(rememberScrollState()).imePadding()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()) {
            CircleBtn(Icons.Back, "Back", onClick = onBack)
            Spacer(Modifier.weight(1f))
            CircleBtn(Icons.Share, "Share", onClick = onShare)
        }

        // ---- framed hero photo with a group sticker
        Enter(rise = 30.dp, tilt = -4f) {
            Box(Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp)) {
                Chunky(Modifier.fillMaxWidth(), radius = 28.dp, shadow = 6.dp, onClick = onWatch) {
                    Thumb(
                        recipe.thumbPath, main?.colorIndex,
                        Modifier.padding(8.dp).fillMaxWidth().aspectRatio(if (tall) 0.85f else 1.3f)
                            .clip(RoundedCornerShape(20.dp)).border(2.dp, C.Ink, RoundedCornerShape(20.dp)),
                        emoji = main?.emoji ?: "🍳", emojiSize = 72.sp,
                    ) {
                        Box(Modifier.align(Alignment.Center)) {
                            Chunky(Modifier.size(64.dp), shape = androidx.compose.foundation.shape.CircleShape, fill = C.Accent, shadow = 3.dp,
                                contentAlignment = Alignment.Center) {
                                Ic(Icons.Play, 30.dp, C.OnAccent, Modifier.offset(x = 2.dp))
                            }
                        }
                        SourceTag(recipe.url, recipe.kind, Modifier.align(Alignment.BottomStart).padding(10.dp))
                    }
                }
                Sticker(main?.emoji ?: "🍳", 58.dp, rotation = 14f, fill = C.Sun,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-14).dp).bob(3.dp, 6f, 2800))
            }
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
            Enter(80) {
                Column {
                    T(recipe.title, size = 30.sp, weight = FontWeight.SemiBold, display = true, lineHeight = 35.sp)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("🗓️ Saved ${shortDate(recipe.savedAt)}", size = 13.sp, weight = FontWeight.Medium, color = C.TextDim, modifier = Modifier.weight(1f))
                        T(
                            if (editing) "Done ✅" else "Edit ✏️", size = 14.sp, weight = FontWeight.SemiBold, color = C.Accent, display = true,
                            modifier = Modifier.clip(RoundedCornerShape(50)).pressable { editing = !editing }.padding(horizontal = 10.dp, vertical = 10.dp),
                        )
                    }
                    // Group stickers (tap to change)
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        groups.forEach { g -> FilterChip(g.name, false, g.emoji, onGroup) }
                        FilterChip(if (groups.isEmpty()) "Add to group" else "Change", true, if (groups.isEmpty()) "➕" else "🗂️", onGroup)
                    }
                }
            }

            // Edit panel: title + link + remove.
            AnimatedVisibility(editing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Chunky(Modifier.padding(top = 10.dp).fillMaxWidth(), radius = 22.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FieldLabel("✏️ Title")
                        OutlinedInput(title, { title = it }, "Recipe title", onIme = { if (title.isNotBlank()) onTitle(title.trim()) })
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Ic(Icons.Link, 16.dp, C.TextDim)
                            T(recipe.url, size = 12.sp, color = C.TextDim, maxLines = 1)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BigButton("🗑️ Remove", false, modifier = Modifier.weight(1f), onClick = onDelete)
                            BigButton("Save", true, enabled = title.isNotBlank() && title.trim() != recipe.title, modifier = Modifier.weight(1f)) {
                                onTitle(title.trim()); editing = false
                            }
                        }
                    }
                }
            }

            // ---- watch
            Enter(140) {
                Pill(
                    "Watch on ${src?.site ?: "the web"}", emoji = src?.emoji ?: "🌐", primary = true,
                    modifier = Modifier.padding(top = 18.dp).fillMaxWidth(), onClick = onWatch,
                )
            }

            // ---- sticky-note notes with a strip of tape
            Enter(200, tilt = 3f) {
                Box(Modifier.padding(top = 30.dp)) {
                    Chunky(Modifier.fillMaxWidth(), radius = 18.dp, fill = C.SunSoft) {
                        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 16.dp)) {
                            T("📝 My notes", size = 20.sp, weight = FontWeight.SemiBold, display = true)
                            val rule = C.Ink.copy(alpha = .15f)
                            BasicTextField(
                                value = notes, onValueChange = { notes = it },
                                textStyle = TextStyle(color = C.Text, fontSize = 16.sp, lineHeight = 30.sp, fontFamily = Fonts.Sans, fontWeight = FontWeight.Medium),
                                cursorBrush = SolidColor(C.Accent),
                                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(min = 90.dp)
                                    .drawBehind {
                                        val step = 30.sp.toPx()
                                        var y = step - 4.dp.toPx()
                                        while (y < size.height) {
                                            drawLine(rule, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                                            y += step
                                        }
                                    },
                                decorationBox = { inner ->
                                    Box {
                                        if (notes.isEmpty()) T("Extra chilli? Swap the oil? Jot it here…", size = 16.sp, color = C.TextDim, lineHeight = 30.sp)
                                        inner()
                                    }
                                },
                            )
                        }
                    }
                    Box(
                        Modifier.align(Alignment.TopCenter).offset(y = (-10).dp).rotate(-3f).size(86.dp, 22.dp)
                            .background(C.tile(2).copy(alpha = .85f)).border(1.5.dp, C.Ink.copy(alpha = .5f))
                    )
                }
            }
        }

        // ---- more from the group, as a shelf
        if (more.isNotEmpty()) Enter(260) {
            Column(Modifier.padding(top = 30.dp)) {
                T("More ${main?.emoji ?: "🫙"} ${main?.name ?: "Unsorted"}", size = 22.sp, weight = FontWeight.SemiBold, display = true,
                    modifier = Modifier.padding(horizontal = 20.dp))
                LazyRow(
                    Modifier.padding(top = 12.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(more, key = { it.id }) { r ->
                        val g = r.mainGroup(groupsById)
                        Chunky(Modifier.width(150.dp), radius = 18.dp, onClick = { onOpenOther(r) }) {
                            Column {
                                Thumb(
                                    r.thumbPath, g?.colorIndex,
                                    Modifier.padding(6.dp).fillMaxWidth().aspectRatio(.85f).clip(RoundedCornerShape(13.dp))
                                        .border(2.dp, C.Ink, RoundedCornerShape(13.dp)),
                                    emoji = g?.emoji ?: "🍳", emojiSize = 34.sp,
                                )
                                T(r.title, size = 14.sp, weight = FontWeight.SemiBold, display = true, lineHeight = 18.sp, maxLines = 2,
                                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(36.dp))
    }
}

