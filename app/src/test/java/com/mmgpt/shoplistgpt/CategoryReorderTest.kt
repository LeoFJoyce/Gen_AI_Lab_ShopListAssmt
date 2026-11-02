package com.mmgpt.shoplistgpt

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CategoryReorderTest {
    private lateinit var application: Application
    private lateinit var model: ShoppingListViewModel
    private val original = listOf(
        ShoppingCategory(41, "Duplicate", listOf(ShoppingItem(101, "Apples", isChecked = true))),
        ShoppingCategory(7, "Duplicate", listOf(ShoppingItem(102, "Milk", isFlagged = true))),
        ShoppingCategory(90, "Bread", listOf(ShoppingItem(103, "Rolls", isStruck = true)))
    )

    @Before fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("state", Context.MODE_PRIVATE).edit().clear().commit()
        model = ShoppingListViewModel(application)
        model.setCategories(original)
    }

    // Moving a category must preserve its identity, contents and purchase/flag state.
    @Test fun firstToLastMovesTheWholeCategoryWithoutChangingItsContents() {
        model.reorderCategory(0, 2)
        assertEquals(listOf(original[1], original[2], original[0]), model.categories.value)
    }

    // The reverse boundary catches implementations that only handle downward moves.
    @Test fun lastToFirstMovesTheWholeCategory() {
        model.reorderCategory(2, 0)
        assertEquals(listOf(original[2], original[0], original[1]), model.categories.value)
    }

    // Equal headings are valid: titles cannot be used as category identity.
    @Test fun adjacentCategoriesWithIdenticalHeadingsRemainDistinct() {
        model.reorderCategory(0, 1)
        assertEquals(listOf(original[1], original[0], original[2]), model.categories.value)
    }

    // Every move must use the latest authoritative order, with no loss or duplication.
    @Test fun successiveMovesComposeAndCanBeReversed() {
        model.reorderCategory(0, 2)
        model.reorderCategory(0, 1)
        assertEquals(listOf(original[2], original[1], original[0]), model.categories.value)
        model.reorderCategory(1, 0)
        model.reorderCategory(2, 0)
        assertEquals(original, model.categories.value)
    }

    // A no-op must preserve all business data, including the current persisted order.
    @Test fun movingToTheSamePositionIsANoOp() {
        original.indices.forEach { model.reorderCategory(it, it) }
        assertEquals(original, model.categories.value)
        assertEquals(original, ShoppingListViewModel(application).categories.value)
    }

    // RecyclerView.NO_POSITION, size (off-by-one), and extreme indices must be ignored.
    @Test fun invalidSourceOrDestinationDoesNotMutateOrPersistADifferentList() {
        listOf(-1, original.size, Int.MIN_VALUE, Int.MAX_VALUE).forEach { invalid ->
            model.reorderCategory(invalid, 0)
            assertEquals("Invalid source $invalid", original, model.categories.value)
            model.reorderCategory(0, invalid)
            assertEquals("Invalid destination $invalid", original, model.categories.value)
        }
        assertEquals(original, ShoppingListViewModel(application).categories.value)
    }

    // The summary-only state has no navigable categories and no valid move.
    @Test fun emptyListRejectsAllMoves() {
        model.setCategories(emptyList())
        model.reorderCategory(0, 0)
        model.reorderCategory(-1, 0)
        assertEquals(emptyList<ShoppingCategory>(), model.categories.value)
    }

    // A single category cannot be moved past either end.
    @Test fun singletonAllowsOnlyIdentityMove() {
        model.setCategories(listOf(original[0]))
        model.reorderCategory(0, 0)
        model.reorderCategory(0, 1)
        model.reorderCategory(1, 0)
        assertEquals(listOf(original[0]), model.categories.value)
    }

    // Repeated callbacks refer to the same IDs despite changes in model positions.
    @Test fun repeatedRelativeMoveIsIdempotentAndPersistsTheDesiredOrder() {
        repeat(3) { org.junit.Assert.assertTrue(model.moveCategory(41, 7, afterTarget = true)) }
        val expected = listOf(original[1], original[0], original[2])
        assertEquals(expected, model.categories.value)
        assertEquals(expected, ShoppingListViewModel(application).categories.value)
    }

    // Removing the source shifts the target: cover both directions and both sides.
    @Test fun relativePlacementHandlesEverySourceAndTargetPair() {
        for (from in original.indices) for (target in original.indices) {
            if (from == target) continue
            for (after in listOf(false, true)) {
                model.setCategories(original)
                org.junit.Assert.assertTrue(model.moveCategory(
                    original[from].id, original[target].id, after))
                val result = model.categories.value!!
                assertEquals(original.toSet(), result.toSet())
                assertEquals(original.size, result.size)
                val sourcePosition = result.indexOf(original[from])
                val targetPosition = result.indexOf(original[target])
                assertEquals("Source must be adjacent on the requested side",
                    targetPosition + if (after) 1 else -1, sourcePosition)
            }
        }
    }

    // Missing/deleted IDs and self-targets are rejected without changing persisted data.
    @Test fun missingOrIdenticalMoveTargetsAreRejected() {
        listOf(-1L to 7L, 41L to -1L, Long.MAX_VALUE to 7L, 41L to 41L).forEach { (id, target) ->
            org.junit.Assert.assertFalse(model.moveCategory(id, target, afterTarget = true))
            assertEquals(original, model.categories.value)
        }
        assertEquals(original, ShoppingListViewModel(application).categories.value)
    }

    // Shop must receive the same order after the planning state has been saved/reloaded.
    @Test fun reorderedCategoriesSurviveReloadWithAllItemStateIntact() {
        model.reorderCategory(2, 0)
        assertEquals(listOf(original[2], original[0], original[1]),
            ShoppingListViewModel(application).categories.value)
    }
}
