package io.github.gobi12b.cookbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.cookbook.data.CookbookData
import io.github.gobi12b.cookbook.data.Group

/** Tap the sort label to cycle through these; most recipes first is the default. */
private enum class GroupSort(val label: String) {
    MostRecipes("🔥 Most recipes"), Recent("🕒 Recently added"), Name("🔤 A–Z")
}

/** Groups ordered by how many recipes they hold (most first); ties keep creation order. */
fun CookbookData.groupsByCount(): List<Group> {
    val counts = recipes.flatMap { it.groupIds }.groupingBy { it }.eachCount()
    return groups.sortedByDescending { counts[it.id] ?: 0 }
}

private data class GroupRow(val id: String, val name: String, val emoji: String, val group: Group?, val count: Int, val updated: Long?, val thumbs: List<String>)

@Composable
fun GroupsScreen(
    data: CookbookData,
    onOpenGroup: (String) -> Unit,
    onCreate: (String, String) -> Unit,
    onRename: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    bottomPadding: Dp,
) {
    var sort by rememberSaveable { mutableStateOf(GroupSort.MostRecipes) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Group?>(null) }

    val rows = remember(data, sort) {
        val grouped = data.groups.map { g ->
            val rs = data.recipes.filter { g.id in it.groupIds }
            GroupRow(g.id, g.name, g.emoji, g, rs.size, rs.maxOfOrNull { it.savedAt }, rs.mapNotNull { it.thumbPath }.take(3))
        }
        val sorted = when (sort) {
            GroupSort.MostRecipes -> grouped.sortedByDescending { it.count }
            GroupSort.Recent -> grouped.sortedWith(compareByDescending<GroupRow> { it.updated ?: Long.MIN_VALUE }.thenBy { it.group?.createdAt })
            GroupSort.Name -> grouped.sortedBy { it.name.lowercase() }
        }
        val unsorted = data.recipes.filter { it.groupIds.isEmpty() }
        if (unsorted.isEmpty()) sorted
        else sorted + GroupRow(UNSORTED, "Unsorted", "🫙", null, unsorted.size, null, unsorted.mapNotNull { it.thumbPath }.take(3))
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(C.Bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding + 28.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.statusBarsPadding().padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f).padding(start = 2.dp)) {
                        Tape("Your shelves", color = C.tile(2), rotation = 3f)
                        T("Groups", size = 46.sp, weight = FontWeight.SemiBold, display = true, lineHeight = 50.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Pill("New", emoji = "✨", modifier = Modifier.padding(bottom = 6.dp)) { creating = true }
                }
                Box(Modifier.padding(top = 10.dp)) {
                    FilterChip(sort.label, selected = false) { sort = GroupSort.entries[(sort.ordinal + 1) % GroupSort.entries.size] }
                }
            }
        }
        itemsIndexed(rows, key = { _, r -> r.id }) { i, row ->
            Enter(delayMs = i.coerceAtMost(8) * 60, tilt = if (i % 2 == 0) -5f else 5f, modifier = Modifier.animateItem()) {
                GroupTile(
                    row,
                    onClick = { onOpenGroup(row.id) },
                    onRename = row.group?.let { g -> { renaming = g } },
                    onDelete = row.group?.let { g -> { onDelete(g.id) } },
                )
            }
        }
    }

    if (creating) NameDialog("New group", "", null, "Create", { creating = false }) { n, e -> onCreate(n, e); creating = false }
    renaming?.let { g ->
        NameDialog("Edit group", g.name, g.emoji, "Save", { renaming = null }) { n, e -> onRename(g.id, n, e); renaming = null }
    }
}

@Composable
private fun GroupTile(row: GroupRow, onClick: () -> Unit, onRename: (() -> Unit)?, onDelete: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    val ci = row.group?.colorIndex
    Box {
        Chunky(Modifier.fillMaxWidth(), radius = 22.dp, onClick = onClick) {
            Column {
                // Collage: one large photo with two small ones beside it, or a big bobbing emoji.
                Row(
                    Modifier.padding(8.dp).fillMaxWidth().aspectRatio(1.05f).clip(RoundedCornerShape(16.dp))
                        .background(C.tile(ci)).border(2.dp, C.Ink, RoundedCornerShape(16.dp)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    when (row.thumbs.size) {
                        0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(row.emoji, fontSize = 54.sp, modifier = Modifier.bob(5.dp, 8f, 2600))
                        }
                        1 -> Thumb(row.thumbs[0], ci, Modifier.fillMaxSize())
                        else -> {
                            Thumb(row.thumbs[0], ci, Modifier.weight(1.6f).fillMaxHeight())
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Thumb(row.thumbs[1], ci, Modifier.weight(1f).fillMaxWidth())
                                if (row.thumbs.size > 2) Thumb(row.thumbs[2], ci, Modifier.weight(1f).fillMaxWidth())
                                else Thumb(null, ci, Modifier.weight(1f).fillMaxWidth(), emoji = row.emoji, emojiSize = 26.sp)
                            }
                        }
                    }
                }
                Row(Modifier.padding(start = 12.dp, end = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(row.name, size = 18.sp, weight = FontWeight.SemiBold, display = true, maxLines = 1)
                        val sub = buildString {
                            append(if (row.count == 0) "Empty for now" else "${row.count} recipe${if (row.count == 1) "" else "s"}")
                            if (row.group == null) append(" · no group")
                            else if (row.updated != null) append(" · " + updatedLabel(row.updated).removePrefix("Updated ").replaceFirstChar { it.lowercase() })
                        }
                        T(sub, size = 12.sp, weight = FontWeight.Medium, color = C.TextDim, maxLines = 1)
                    }
                    if (onRename != null && onDelete != null) Box {
                        Box(Modifier.size(40.dp).pressable { menu = true }, contentAlignment = Alignment.Center) { Ic(Icons.More, 18.dp, C.TextDim) }
                        DropdownMenu(menu, { menu = false }, containerColor = C.Card, shape = RoundedCornerShape(18.dp)) {
                            DropdownMenuItem({ T("✏️  Edit name & emoji", weight = FontWeight.SemiBold) }, { menu = false; onRename() })
                            DropdownMenuItem({ T("🗑️  Delete group", weight = FontWeight.SemiBold, color = C.Accent) }, { menu = false; onDelete() })
                        }
                    }
                }
            }
        }
        if (row.thumbs.isNotEmpty()) Sticker(
            row.emoji, 44.dp, rotation = -12f, fill = C.Sun,
            modifier = Modifier.align(Alignment.TopStart).offset(x = (-8).dp, y = (-10).dp),
        )
    }
}

