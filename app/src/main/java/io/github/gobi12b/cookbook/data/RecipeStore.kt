package io.github.gobi12b.cookbook.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Persists the whole cookbook as one small JSON file, written atomically. */
class RecipeStore(context: Context) {
    private val file = File(context.filesDir, "cookbook.json")
    private val tmp = File(context.filesDir, "cookbook.json.tmp")
    private val writeLock = Mutex()

    val thumbDir: File = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun load(): CookbookData {
        if (!file.exists()) return seed()
        return runCatching { Json.decode(file.readText()) }.getOrElse { seed() }
    }

    suspend fun save(data: CookbookData) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            tmp.writeText(Json.encode(data))
            if (!tmp.renameTo(file)) {
                file.delete(); tmp.renameTo(file)
            }
        }
    }

    fun thumbFile(recipeId: String) = File(thumbDir, "$recipeId.jpg")

    private fun seed(): CookbookData {
        val now = System.currentTimeMillis()
        val groups = listOf("Dinner", "Tiffin", "Quick", "Sweets", "Snacks").mapIndexed { i, name ->
            Group(id = newId(), name = name, colorIndex = i, createdAt = now + i)
        }
        return CookbookData(groups = groups)
    }

    companion object {
        fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
    }
}
