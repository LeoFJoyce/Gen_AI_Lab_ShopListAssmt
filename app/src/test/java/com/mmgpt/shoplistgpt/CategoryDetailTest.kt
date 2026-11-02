package com.mmgpt.shoplistgpt

import android.content.DialogInterface
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mmgpt.shoplistgpt.adapter.ItemAdapter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog

/** Behavior tests: real fragments, adapters and activity-scoped model; no mocked render hooks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class CategoryDetailTest {
    private lateinit var ui: CategoryUiFixture
    private var page: Fragment? = null
    private var hostId = 0
    private val categories = listOf(
        ShoppingCategory(10, "Same heading", listOf(ShoppingItem(100, "Apples"))),
        ShoppingCategory(20, "Same heading", listOf(
            ShoppingItem(200, "Section", isHeading = true),
            ShoppingItem(201, "Milk"),
            ShoppingItem(202, "Bread", isChecked = true)
        ))
    )

    @Before fun setUp() {
        ui = CategoryUiFixture()
        hostId = View.generateViewId()
        ui.activity.addContentView(FrameLayout(ui.activity).apply { id = hostId },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    @After fun tearDown() {
        page?.let { ui.activity.supportFragmentManager.beginTransaction().remove(it).commitNow() }
        ui.close()
    }

    private fun open(plan: Boolean, index: Int? = 1, data: List<ShoppingCategory> = categories) {
        page?.let { ui.activity.supportFragmentManager.beginTransaction().remove(it).commitNow() }
        ui.model.setCategories(data)
        page = if (index == null) {
            if (plan) PlanCategoryFragment() else CategoryFragment()
        } else {
            if (plan) PlanCategoryFragment.newInstance(index) else CategoryFragment.newInstance(index)
        }
        ui.activity.supportFragmentManager.beginTransaction().add(hostId, page!!).commitNow()
        ui.settle()
    }

    private fun <T : View> view(id: Int): T = page!!.requireView().findViewById(id)
    private fun recycler(): RecyclerView = view(R.id.recyclerView)
    private fun items(): List<ShoppingItem> = (recycler().adapter as ItemAdapter).currentList
    private fun row(position: Int, id: Int): View = ui.row(recycler(), position).itemView.findViewById(id)
    private fun text(id: Int) = view<TextView>(id).text.toString()
    private fun modelItems() = ui.model.categories.value!![1].items
    private fun bothModes(check: (Boolean) -> Unit) = listOf(false, true).forEach(check)

    // Duplicate titles ensure argument parsing selects by index, not title or first match.
    @Test fun explicitIndexRendersOnlyRequestedCategoryInBothModes() = bothModes { plan ->
        open(plan)
        assertEquals("Same heading", text(R.id.tvHeading))
        assertEquals(listOf(200L, 201L, 202L), items().filterNot { it.isPlaceholder }.map { it.id })
        assertTrue(recycler().layoutManager is LinearLayoutManager)
        assertEquals(categories, ui.model.categories.value)
    }

    // Framework-created fragments without arguments retain the existing first-category default.
    @Test fun missingArgumentsDefaultToFirstCategory() = bothModes { plan ->
        open(plan, index = null)
        assertEquals(listOf(100L), items().filterNot { it.isPlaceholder }.map { it.id })
    }

    // Invalid positions must not clamp to another category or expose its items.
    @Test fun invalidIndicesRenderNoCategoryAndNeverMutateTheModel() = bothModes { plan ->
        listOf(-1, categories.size, Int.MAX_VALUE).forEach { index ->
            open(plan, index)
            assertEquals("", text(R.id.tvHeading))
            assertTrue(items().isEmpty())
            assertEquals(categories, ui.model.categories.value)
        }
    }

    @Test fun emptyCategoryCollectionIsSafeInBothModes() = bothModes { plan ->
        open(plan, index = 0, data = emptyList())
        assertEquals("", text(R.id.tvHeading))
        assertTrue(items().isEmpty())
        assertEquals(emptyList<ShoppingCategory>(), ui.model.categories.value)
    }

    // Observation must update the heading and rows together without modifying incoming state.
    @Test fun modelUpdatesRefreshTheSelectedPage() = bothModes { plan ->
        open(plan)
        val updated = categories[1].copy(heading = "Updated", items = listOf(ShoppingItem(205, "Tea")))
        ui.model.setCategories(listOf(categories[0], updated))
        ui.settle()
        assertEquals("Updated", text(R.id.tvHeading))
        assertEquals(updated.items, items().filterNot { it.isPlaceholder })
        assertEquals(listOf(categories[0], updated), ui.model.categories.value)
    }

    // Counts exclude headings and update after a real checkbox action.
    @Test fun shopCheckUpdatesCountsAndOnlyTheClickedItem() {
        open(plan = false)
        assertEquals("1/2", text(R.id.tvCount))
        row(1, R.id.chkPurchased).performClick()
        ui.settle()
        assertEquals("2/2", text(R.id.tvCount))
        assertEquals(categories[1].items.map { if (it.id == 201L) it.copy(isChecked = true) else it }, modelItems())
        assertEquals(categories[0], ui.model.categories.value!![0])
        row(1, R.id.chkPurchased).performClick()
        ui.settle()
        assertEquals("1/2", text(R.id.tvCount))
        assertEquals(categories, ui.model.categories.value)
    }

    @Test fun shopFlagTogglesWithoutChangingChecksOrCounts() {
        open(plan = false)
        row(1, R.id.btnFlag).performClick()
        ui.settle()
        assertEquals(categories[1].items[1].copy(isFlagged = true), modelItems()[1])
        assertEquals("1/2", text(R.id.tvCount))
        row(1, R.id.btnFlag).performClick()
        ui.settle()
        assertEquals(categories, ui.model.categories.value)
    }

    // Mode policy must survive extraction; Shop never exposes destructive Plan controls.
    @Test fun shopHidesPlanControlsAndDisablesHeadingActions() {
        open(plan = false)
        assertEquals(View.GONE, view<View>(R.id.chkSelectAll).visibility)
        listOf(R.id.btnEdit, R.id.btnDelete, R.id.dragHandle).forEach {
            assertEquals(View.GONE, row(1, it).visibility)
        }
        assertEquals(View.VISIBLE, row(1, R.id.btnFlag).visibility)
        assertFalse(row(0, R.id.chkPurchased).isEnabled)
        assertFalse(row(0, R.id.btnFlag).isEnabled)
        assertEquals(3, items().size)
    }

    @Test fun emptyCategoryKeepsModeSpecificPresentation() = bothModes { plan ->
        open(plan, data = listOf(categories[0], categories[1].copy(items = emptyList())))
        assertEquals(if (plan) "" else "0/0", text(R.id.tvCount))
        assertEquals(if (plan) listOf(-1L, -2L) else emptyList<Long>(), items().map { it.id })
        if (plan) assertTrue(view<CheckBox>(R.id.chkSelectAll).isChecked)
    }

    // Programmatic checkbox synchronisation must not select unchecked items on initial render.
    @Test fun planSelectAllExcludesHeadingsAndOtherCategoriesAndCanBeReversed() {
        open(plan = true)
        assertEquals("", text(R.id.tvCount))
        assertFalse(view<CheckBox>(R.id.chkSelectAll).isChecked)
        assertEquals(categories, ui.model.categories.value)
        view<CheckBox>(R.id.chkSelectAll).performClick()
        ui.settle()
        assertEquals(categories[1].items.map { if (it.isHeading) it else it.copy(isChecked = true) }, modelItems())
        assertEquals(categories[0], ui.model.categories.value!![0])
        view<CheckBox>(R.id.chkSelectAll).performClick()
        ui.settle()
        assertEquals(categories[1].items.map { it.copy(isChecked = false) }, modelItems())
    }

    @Test fun planIndividualCheckUpdatesSelectAllWithoutCheckingHeading() {
        open(plan = true)
        row(2, R.id.chkPurchased).performClick()
        ui.settle()
        assertTrue(view<CheckBox>(R.id.chkSelectAll).isChecked)
        assertFalse(modelItems()[0].isChecked)
        assertEquals(View.GONE, row(2, R.id.btnFlag).visibility)
        listOf(R.id.btnEdit, R.id.btnDelete, R.id.dragHandle).forEach {
            assertEquals(View.VISIBLE, row(2, it).visibility)
        }
    }

    // Both Add rows use adapter offsets; whitespace must never create an empty item.
    @Test fun planAddRowsTrimRejectBlankAndInsertAtBothBoundaries() {
        open(plan = true)
        val before = ui.model.categories.value
        (row(0, R.id.etAdd) as EditText).setText("   ")
        row(0, R.id.btnAdd).performClick()
        assertEquals(before, ui.model.categories.value)
        (row(0, R.id.etAdd) as EditText).setText("  Eggs  ")
        row(0, R.id.btnAdd).performClick()
        ui.settle()
        assertEquals("Eggs", modelItems().first().name)
        assertEquals("", (row(0, R.id.etAdd) as EditText).text.toString())
        val last = items().lastIndex
        (row(last, R.id.etAdd) as EditText).setText("Coffee")
        row(last, R.id.btnAdd).performClick()
        ui.settle()
        assertEquals(listOf("Eggs", "Section", "Milk", "Bread", "Coffee"), modelItems().map { it.name })
        assertEquals(modelItems().size, modelItems().map { it.id }.distinct().size)
        assertEquals(categories[0], ui.model.categories.value!![0])
    }

    private fun edit(name: String, confirm: Boolean) {
        row(2, R.id.btnEdit).performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        fun input(v: View): EditText? {
            if (v is EditText) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) input(v.getChildAt(i))?.let { return it }
            return null
        }
        requireNotNull(input(dialog.window!!.decorView)).setText(name)
        dialog.getButton(if (confirm) DialogInterface.BUTTON_POSITIVE else DialogInterface.BUTTON_NEGATIVE).performClick()
        ui.settle()
    }

    @Test fun planEditRejectsBlankAndCancelThenTrimsValidName() {
        open(plan = true)
        edit("   ", confirm = true)
        assertEquals(categories, ui.model.categories.value)
        edit("Discarded", confirm = false)
        assertEquals(categories, ui.model.categories.value)
        edit("  Oat milk  ", confirm = true)
        assertEquals(categories[1].items[1].copy(name = "Oat milk"), modelItems()[1])
        assertEquals(categories[0], ui.model.categories.value!![0])
        assertEquals(listOf("Section", "Oat milk", "Bread"), items().filterNot { it.isPlaceholder }.map { it.name })
    }

    @Test fun planDeleteRemovesOnlyTargetAndRefreshesRows() {
        open(plan = true)
        row(2, R.id.btnDelete).performClick()
        ui.settle()
        assertEquals(listOf(200L, 202L), modelItems().map { it.id })
        assertEquals(listOf(-1L, 200L, 202L, -2L), items().map { it.id })
        assertEquals(categories[0], ui.model.categories.value!![0])
    }

    @Test fun planDragReordersRealItemsButRejectsAddRows() {
        open(plan = true)
        assertFalse(ui.drag(recycler(), 2, 0))
        assertFalse(ui.drag(recycler(), 4, 2))
        assertEquals(categories, ui.model.categories.value)
        assertTrue(ui.drag(recycler(), 2, 3))
        ui.settle()
        assertEquals(listOf(200L, 202L, 201L), modelItems().map { it.id })
        assertEquals(listOf(-1L, 200L, 202L, 201L, -2L), items().map { it.id })
        assertEquals(categories[0], ui.model.categories.value!![0])
    }

    // Recreating only the view catches observers/bindings that accidentally outlive it.
    @Test fun recreatedViewUsesLatestStateAndOldViewStopsObserving() = bothModes { plan ->
        open(plan)
        val fragment = page!!
        val oldRoot = fragment.requireView()
        ui.activity.supportFragmentManager.beginTransaction().detach(fragment).commitNow()
        val updated = categories[1].copy(heading = "While detached", items = listOf(ShoppingItem(210, "Rice")))
        ui.model.setCategories(listOf(categories[0], updated))
        ui.settle()
        assertEquals("Same heading", oldRoot.findViewById<TextView>(R.id.tvHeading).text.toString())
        ui.activity.supportFragmentManager.beginTransaction().attach(fragment).commitNow()
        ui.settle()
        assertNotSame(oldRoot, fragment.requireView())
        assertEquals("While detached", text(R.id.tvHeading))
        assertEquals(updated.items, items().filterNot { it.isPlaceholder })
        assertEquals(if (plan) "" else "0/1", text(R.id.tvCount))
    }

    // A fresh framework instance must recover the same index from the saved argument Bundle.
    @Test fun fragmentReinstantiationPreservesCategoryArgument() = bothModes { plan ->
        open(plan)
        val previous = page!!
        val restored = ui.activity.supportFragmentManager.fragmentFactory.instantiate(
            previous.javaClass.classLoader!!, previous.javaClass.name)
        restored.arguments = android.os.Bundle(previous.requireArguments())
        ui.activity.supportFragmentManager.beginTransaction().remove(previous).add(hostId, restored).commitNow()
        page = restored
        ui.settle()
        assertEquals(listOf(200L, 201L, 202L), items().filterNot { it.isPlaceholder }.map { it.id })
    }

    // Desired cleanup contract: a destroyed view must not retain an adapter and its fragment callbacks.
    @Test fun destroyingViewDisconnectsItsAdapter() = bothModes { plan ->
        open(plan)
        val oldRecycler = recycler()
        ui.activity.supportFragmentManager.beginTransaction().detach(page!!).commitNow()
        assertNull("Destroyed RecyclerView must release its fragment-owned adapter", oldRecycler.adapter)
    }
}
