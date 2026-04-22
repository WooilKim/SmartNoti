package com.smartnoti.app.data.categories

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartnoti.app.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.categoriesDataStore by preferencesDataStore(name = "smartnoti_categories")

enum class CategoryMoveDirection {
    UP,
    DOWN,
}

/**
 * Resolve the decoded Category list from a raw DataStore payload.
 *
 * Unlike [com.smartnoti.app.data.rules.resolveStoredRules], Categories have no
 * default seed — a never-written DataStore (payload == null) yields an empty
 * list. Categories come into existence either through the Task 3 migration
 * pass or through explicit user action in the 분류 tab.
 */
fun resolveStoredCategories(encodedPayload: String?): List<Category> {
    return when {
        encodedPayload == null -> emptyList()
        encodedPayload.isBlank() -> emptyList()
        else -> CategoryStorageCodec.decode(encodedPayload)
    }
}

/**
 * Reorder [categoryId] by swapping with its direct neighbor.
 *
 * Simpler than Phase C's `moveRule` because Categories have no override tier
 * — every Category sits in one flat ordering. Returns the list unchanged if
 * the id is missing or already at the boundary.
 *
 * Only the visible list ordering changes; callers are expected to refresh the
 * `order` field on all persisted Categories afterwards so the
 * `CategoryConflictResolver` tie-break in Task 6 keeps matching the UI.
 */
fun moveCategory(
    categories: List<Category>,
    categoryId: String,
    direction: CategoryMoveDirection,
): List<Category> {
    val currentIndex = categories.indexOfFirst { it.id == categoryId }
    if (currentIndex == -1) return categories

    val targetIndex = when (direction) {
        CategoryMoveDirection.UP -> currentIndex - 1
        CategoryMoveDirection.DOWN -> currentIndex + 1
    }
    if (targetIndex !in categories.indices) return categories

    val mutable = categories.toMutableList()
    val item = mutable.removeAt(currentIndex)
    mutable.add(targetIndex, item)
    return mutable.toList()
}

/**
 * DataStore-backed repository for the 분류 collection. Mirrors
 * [com.smartnoti.app.data.rules.RulesRepository] — same
 * `preferencesDataStore` + `stringPreferencesKey` shape so Android Studio
 * diffs between the two read the same.
 */
class CategoriesRepository private constructor(
    private val context: Context,
) {
    fun observeCategories(): Flow<List<Category>> {
        return context.categoriesDataStore.data.map { prefs ->
            resolveStoredCategories(prefs[CATEGORIES])
        }
    }

    suspend fun currentCategories(): List<Category> = observeCategories().first()

    /**
     * Create or update a Category. Upsert is keyed by [Category.id] — callers
     * are responsible for producing a stable id (e.g. `cat-from-rule-<ruleId>`
     * from the Task 3 migration). A new Category is appended to the end of
     * the list; an existing Category's `order` is preserved so
     * drag-to-reorder state is not clobbered by an unrelated edit.
     */
    suspend fun upsertCategory(category: Category) {
        val existing = currentCategories().toMutableList()
        val index = existing.indexOfFirst { it.id == category.id }
        if (index >= 0) {
            existing[index] = category.copy(order = existing[index].order)
        } else {
            existing += category
        }
        persist(existing)
    }

    suspend fun deleteCategory(categoryId: String) {
        persist(currentCategories().filterNot { it.id == categoryId })
    }

    /**
     * Swap [categoryId] with its direct neighbor. After the swap the stored
     * `order` field on every Category is rewritten to match the new list
     * index, so a later read reflects the drag in both the flat list order
     * and the tie-break semantics used by [CategoryConflictResolver].
     */
    suspend fun moveCategory(categoryId: String, direction: CategoryMoveDirection) {
        val reordered = moveCategory(currentCategories(), categoryId, direction)
        val reindexed = reordered.mapIndexed { index, category -> category.copy(order = index) }
        persist(reindexed)
    }

    suspend fun replaceAllCategories(categories: List<Category>) {
        persist(categories)
    }

    private suspend fun persist(categories: List<Category>) {
        context.categoriesDataStore.edit { prefs ->
            prefs[CATEGORIES] = CategoryStorageCodec.encode(categories)
        }
    }

    companion object {
        private val CATEGORIES = stringPreferencesKey("categories_payload")

        @Volatile private var instance: CategoriesRepository? = null

        fun getInstance(context: Context): CategoriesRepository {
            return instance ?: synchronized(this) {
                instance ?: CategoriesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
