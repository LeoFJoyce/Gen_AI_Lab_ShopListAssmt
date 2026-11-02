package com.mmgpt.shoplistgpt

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class CategoryNavigationTest {
    private lateinit var ui: CategoryUiFixture
    private val categories = listOf(
        // Plan marks items as struck on entry; seed that state so reorder assertions
        // isolate ordering rather than the unrelated mode-entry transformation.
        ShoppingCategory(41, "Produce", listOf(ShoppingItem(101, "Apples", isStruck = true))),
        ShoppingCategory(7, "Dairy", listOf(ShoppingItem(102, "Milk", isStruck = true))),
        ShoppingCategory(90, "Bakery", listOf(ShoppingItem(103, "Rolls", isStruck = true)))
    )

    @Before fun setUp() { ui = CategoryUiFixture() }
    @After fun tearDown() { if (::ui.isInitialized) ui.close() }

    // The leading summary and leading Add row must not introduce an off-by-one error.
    @Test fun planDoubleTapFirstCategoryOpensItsItems() {
        val recycler = ui.start(categories, plan = true)
        assertEquals(0, ui.pager.currentItem)
        ui.doubleTapRow(recycler, 1)
        ui.assertPage(categories[0], plan = true)
    }

    // Interior positions catch constant/first-page destinations.
    @Test fun planDoubleTapMiddleCategoryOpensItsItems() {
        val recycler = ui.start(categories, plan = true)
        ui.doubleTapRow(recycler, 2)
        ui.assertPage(categories[1], plan = true)
    }

    // The trailing Add row is not a category, and must not affect the last page index.
    @Test fun planDoubleTapLastCategoryOpensItsItems() {
        val recycler = ui.start(categories, plan = true)
        ui.doubleTapRow(recycler, 3)
        ui.assertPage(categories[2], plan = true)
    }

    // Shop's summary is last; adding Plan's +1 offset here would open the wrong page.
    @Test fun shopDoubleTapFirstCategoryOpensItsItems() {
        val recycler = ui.start(categories, plan = false)
        assertEquals(categories.size, ui.pager.currentItem)
        ui.doubleTapRow(recycler, 0)
        ui.assertPage(categories[0], plan = false)
    }

    @Test fun shopDoubleTapMiddleCategoryOpensItsItems() {
        val recycler = ui.start(categories, plan = false)
        ui.doubleTapRow(recycler, 1)
        ui.assertPage(categories[1], plan = false)
    }

    @Test fun shopDoubleTapLastCategoryDoesNotReopenSummary() {
        val recycler = ui.start(categories, plan = false)
        ui.doubleTapRow(recycler, 2)
        ui.assertPage(categories[2], plan = false)
    }

    // A valid title may be repeated. Verify item contents, not just the displayed heading.
    @Test fun duplicateHeadingsNavigateToTheTappedIdentity() {
        val duplicates = categories.map { it.copy(heading = "Same heading") }
        val recycler = ui.start(duplicates, plan = true)
        ui.doubleTapRow(recycler, 2)
        ui.assertPage(duplicates[1], plan = true)
    }

    // Neither placeholder may be interpreted as a category or trigger a clamped pager jump.
    @Test fun addRowsDoNotNavigate() {
        val recycler = ui.start(categories, plan = true)
        ui.doubleTapRow(recycler, 0)
        assertEquals(0, ui.pager.currentItem)
        ui.settle()
        ui.doubleTapRow(recycler, categories.size + 1)
        assertEquals(0, ui.pager.currentItem)
    }

    // Hit testing must reject space outside any row instead of reusing a prior selection.
    @Test fun doubleTapOutsideRowsDoesNotNavigate() {
        val recycler = ui.start(categories, plan = false)
        ui.doubleTap(recycler, -20f, -20f)
        assertEquals(categories.size, ui.pager.currentItem)
    }

    // Both modes need a useful summary-only state when all categories have been removed.
    @Test fun emptyPlanContainsOnlySummaryAndRejectsPlaceholderDrag() {
        val recycler = ui.start(emptyList(), plan = true)
        assertEquals(1, ui.pager.adapter!!.itemCount)
        assertFalse(ui.drag(recycler, 0, 1))
        assertEquals(emptyList<ShoppingCategory>(), ui.model.categories.value)
        assertEquals(0, ui.pager.currentItem)
    }

    @Test fun emptyShopContainsOnlySummary() {
        val recycler = ui.start(emptyList(), plan = false)
        assertEquals(1, ui.pager.adapter!!.itemCount)
        assertEquals(0, recycler.adapter!!.itemCount)
        assertEquals(0, ui.pager.currentItem)
    }

    // The single category shares both first and last boundaries.
    @Test fun singletonCategoryStillNavigatesPastPlanSummary() {
        val recycler = ui.start(listOf(categories[0]), plan = true)
        ui.doubleTapRow(recycler, 1)
        ui.assertPage(categories[0], plan = true)
    }

    // A placeholder drag must not silently change the category order.
    @Test fun draggingARealCategoryOntoAnAddRowIsRejected() {
        val recycler = ui.start(categories, plan = true)
        assertFalse(ui.drag(recycler, 1, 0))
        assertFalse(ui.drag(recycler, 3, 4))
        ui.settle()
        assertEquals(categories, ui.model.categories.value)
        assertEquals(categories.map { it.id }, ui.displayedIds(recycler))
    }

    // Stable control: once a reorder is committed, navigation must use the new order.
    @Test fun doubleTapAfterCommittedReorderOpensTheDisplayedCategory() {
        val recycler = ui.start(categories, plan = true)
        assertTrue(ui.drag(recycler, 1, 2))
        ui.settle()
        assertEquals(listOf(categories[1], categories[0], categories[2]), ui.model.categories.value)
        assertEquals(ui.model.categories.value!!.map { it.id }, ui.displayedIds(recycler))
        ui.doubleTapRow(recycler, 1)
        ui.assertPage(categories[1], plan = true)
    }

    // Scrolling changes the summary's screen origin; raw/local coordinates must agree.
    @Test fun shopDoubleTapAfterScrollingOpensTheVisibleCategory() {
        val many = (0 until 50).map {
            ShoppingCategory(100L + it, "Category $it", listOf(ShoppingItem(1000L + it, "Item $it")))
        }
        val recycler = ui.start(many, plan = false)
        val scroll = recycler.parent.parent as androidx.core.widget.NestedScrollView
        scroll.scrollTo(0, recycler.bottom)
        ui.settle()
        assertTrue("Fixture must actually scroll", scroll.scrollY > 0)
        ui.doubleTapRow(recycler, many.lastIndex)
        ui.assertPage(many.last(), plan = false)
    }

    // A stale row may still be visible during deletion; it must not open its replacement.
    @Test fun doubleTapOnDeletedCategoryDoesNotNavigateToAnotherCategory() {
        val recycler = ui.start(categories, plan = true)
        ui.model.deleteCategory(0)
        assertEquals(categories.map { it.id }, ui.displayedIds(recycler))
        ui.doubleTapRow(recycler, 1)
        assertEquals("A deleted target must leave the user on the summary", 0, ui.pager.currentItem)
    }

    // Regression: the model has changed but the old row is still visible.
    // A tap on Produce must open Produce, even while its new row position is pending.
    @Test fun doubleTapWhileReorderDiffIsPendingUsesDisplayedCategoryIdentity() {
        val recycler = ui.start(categories, plan = true)
        assertTrue(ui.drag(recycler, 1, 2))
        assertEquals(listOf(categories[1], categories[0], categories[2]), ui.model.categories.value)
        assertEquals(categories.map { it.id }, ui.displayedIds(recycler))
        ui.doubleTapRow(recycler, 1)
        ui.assertPage(categories[0], plan = true)
    }

    // Regression: a second callback arrives before the first diff is committed.
    // The final UI and model must converge regardless of that legal interleaving.
    @Test fun rapidDragCallbacksCannotLeaveDisplayAndModelInDifferentOrders() {
        val recycler = ui.start(categories, plan = true)
        assertTrue(ui.drag(recycler, 1, 2))
        assertTrue(ui.drag(recycler, 1, 2))
        ui.settle()
        assertEquals("Repeated callbacks must not undo the intended move",
            listOf(categories[1], categories[0], categories[2]), ui.model.categories.value)
        assertEquals("Displayed category IDs must match the authoritative page order",
            ui.model.categories.value!!.map { it.id }, ui.displayedIds(recycler))
    }

    // Downward multi-row movement must keep its intent even before any diff is applied.
    @Test fun rapidMovesPastMultipleTargetsPreserveTheDraggedCategory() {
        val recycler = ui.start(categories, plan = true)
        assertTrue(ui.drag(recycler, 1, 2))
        assertTrue(ui.drag(recycler, 1, 3))
        ui.settle()
        assertEquals(listOf(categories[1], categories[2], categories[0]), ui.model.categories.value)
        assertEquals(ui.model.categories.value!!.map { it.id }, ui.displayedIds(recycler))
        ui.doubleTapRow(recycler, 3)
        ui.assertPage(categories[0], plan = true)
    }

    // Repeated upward callbacks must also be idempotent, with no duplicated/lost category.
    @Test fun repeatedUpwardMovesPreserveTheirIntendedOrder() {
        val recycler = ui.start(categories, plan = true)
        assertTrue(ui.drag(recycler, 3, 1))
        assertTrue(ui.drag(recycler, 3, 1))
        ui.settle()
        assertEquals(listOf(categories[2], categories[0], categories[1]), ui.model.categories.value)
        assertEquals(ui.model.categories.value!!.map { it.id }, ui.displayedIds(recycler))
        ui.doubleTapRow(recycler, 1)
        ui.assertPage(categories[2], plan = true)
    }

    // Shop has its own handler; fix identity lookup there as well as in Plan.
    @Test fun shopPendingReorderOpensTheDisplayedIdentity() {
        val recycler = ui.start(categories, plan = false)
        ui.model.setCategories(listOf(categories[2], categories[0], categories[1]))
        assertEquals(categories.map { it.id }, ui.displayedIds(recycler))
        ui.doubleTapRow(recycler, 0)
        ui.assertPage(categories[0], plan = false)
    }

    // Action buttons capture category identity too, even while their rows are stale.
    @Test fun deleteWhileReorderIsPendingRemovesTheDisplayedCategory() {
        val recycler = ui.start(categories, plan = true)
        ui.model.moveCategory(categories[0].id, categories[1].id, afterTarget = true)
        ui.row(recycler, 1).itemView.findViewById<android.view.View>(R.id.btnDelete).performClick()
        assertEquals(listOf(categories[1], categories[2]), ui.model.categories.value)
        ui.settle()
        assertEquals(listOf(categories[1].id, categories[2].id), ui.displayedIds(recycler))
    }

    // Reordering while an edit dialog is open must not rename the category now at its old index.
    @Test fun editDialogAppliesToItsOriginalCategoryAfterReorder() {
        val recycler = ui.start(categories, plan = true)
        ui.row(recycler, 1).itemView.findViewById<android.view.View>(R.id.btnEdit).performClick()
        ui.model.moveCategory(categories[0].id, categories[1].id, afterTarget = true)
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        assertEquals(listOf(categories[1], categories[0], categories[2]), ui.model.categories.value)
    }

    // Entering Shop after reorganising Plan must preserve the order and page contents.
    @Test fun planReorderThenShopNavigationPreservesCategoryIdentity() {
        val recycler = ui.start(categories, plan = true)
        assertTrue(ui.drag(recycler, 3, 1))
        ui.settle()
        ui.activity.onBackPressed()
        val shop = ui.start(ui.model.categories.value!!, plan = false)
        ui.doubleTapRow(shop, 0)
        ui.assertPage(categories[2], plan = false)
    }
}
