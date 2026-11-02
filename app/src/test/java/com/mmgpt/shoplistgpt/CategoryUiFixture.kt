package com.mmgpt.shoplistgpt

import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.viewpager2.widget.ViewPager2
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import java.util.ArrayDeque
import java.util.concurrent.Executor

/**
 * Runs real app components. Only diff scheduling is controlled: there are no sleeps,
 * mocked navigation outcomes, or copies of production reorder/navigation algorithms.
 *
 * Two AndroidX 1.3.0 internals are accessed here because the app exposes no scheduling
 * seam or drag callback. Keep this compatibility code here when upgrading RecyclerView.
 */
internal class CategoryUiFixture : AutoCloseable {
    private val tasks = ArrayDeque<Runnable>()
    private val previousExecutor: Executor? =
        ReflectionHelpers.getStaticField(AsyncDifferConfig.Builder::class.java, "sDiffExecutor")
    private val controller: ActivityController<MainActivity>
    val activity: MainActivity
    val model: ShoppingListViewModel
    val pager: ViewPager2

    init {
        ReflectionHelpers.setStaticField(AsyncDifferConfig.Builder::class.java, "sDiffExecutor",
            Executor { tasks.addLast(it) })
        val application = ApplicationProvider.getApplicationContext<Application>()
        application.getSharedPreferences("state", Context.MODE_PRIVATE).edit().clear().commit()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        activity = controller.get()
        model = ViewModelProvider(activity)[ShoppingListViewModel::class.java]
        pager = activity.findViewById(R.id.viewPager)
    }

    fun start(categories: List<ShoppingCategory>, plan: Boolean): RecyclerView {
        model.setCategories(categories)
        onView(withId(if (plan) R.id.btnPlan else R.id.btnShop)).perform(click())
        if (!plan) pager.setCurrentItem(categories.size, false)
        settle()
        return summaryRecycler(plan)
    }

    fun summaryRecycler(plan: Boolean): RecyclerView {
        val fragment = activity.supportFragmentManager.fragments.single {
            if (plan) it is PlanCategoriesFragment else it is ShopCategoriesFragment
        }
        return fragment.requireView().findViewById(R.id.recyclerView)
    }

    fun settle() {
        repeat(30) {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            val decor = activity.window.decorView
            decor.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 1080, 1920)
            if (tasks.isEmpty() && pager.scrollState == ViewPager2.SCROLL_STATE_IDLE) return
        }
        error("UI/diff queue did not settle")
    }

    fun row(recycler: RecyclerView, position: Int): RecyclerView.ViewHolder =
        requireNotNull(recycler.findViewHolderForAdapterPosition(position)) {
            "Row $position was not laid out"
        }

    fun drag(recycler: RecyclerView, from: Int, to: Int): Boolean {
        val helper = (0 until recycler.itemDecorationCount)
            .map { recycler.getItemDecorationAt(it) }.filterIsInstance<ItemTouchHelper>().single()
        val callback = ReflectionHelpers.getField<ItemTouchHelper.Callback>(helper, "mCallback")
        return callback.onMove(recycler, row(recycler, from), row(recycler, to))
    }

    fun doubleTapRow(recycler: RecyclerView, position: Int) {
        val holder = row(recycler, position)
        val label = holder.itemView.findViewById<TextView>(R.id.tvName) ?: holder.itemView
        val screen = IntArray(2)
        val origin = IntArray(2)
        label.getLocationOnScreen(screen)
        recycler.getLocationOnScreen(origin)
        doubleTap(recycler, screen[0] - origin[0] + label.width / 2f,
            screen[1] - origin[1] + label.height / 2f)
    }

    fun doubleTap(recycler: RecyclerView, x: Float, y: Float) {
        val start = SystemClock.uptimeMillis()
        listOf(0L to MotionEvent.ACTION_DOWN, 20L to MotionEvent.ACTION_UP,
            80L to MotionEvent.ACTION_DOWN, 100L to MotionEvent.ACTION_UP).forEach { (delay, action) ->
            val down = if (delay < 80) start else start + 80
            val origin = IntArray(2)
            recycler.getLocationOnScreen(origin)
            // Preserve raw screen coordinates while delivering RecyclerView-local x/y.
            val event = MotionEvent.obtain(down, start + delay, action,
                x + origin[0], y + origin[1], 0)
            event.offsetLocation(-origin[0].toFloat(), -origin[1].toFloat())
            try { recycler.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }

    fun displayedIds(recycler: RecyclerView): List<Long> =
        (0 until recycler.adapter!!.itemCount).map { recycler.adapter!!.getItemId(it) }
            .filter { it != -1L && it != -2L }

    fun assertPage(expected: ShoppingCategory, plan: Boolean) {
        val expectedIndex = model.categories.value!!.indexOfFirst { it.id == expected.id }
        org.junit.Assert.assertEquals("Tapped category ID must determine the destination",
            expectedIndex + if (plan) 1 else 0, pager.currentItem)
        settle()
        val page = activity.supportFragmentManager.findFragmentByTag("f" + pager.currentItem)!!
        org.junit.Assert.assertEquals(expected.heading,
            page.requireView().findViewById<TextView>(R.id.tvHeading).text.toString())
        val items = page.requireView().findViewById<RecyclerView>(R.id.recyclerView)
        val actualNames = (0 until items.adapter!!.itemCount).mapNotNull { position ->
            row(items, position).itemView.findViewById<TextView>(R.id.tvName)?.text?.toString()
        }
        org.junit.Assert.assertEquals("Page must contain the tapped category's items",
            expected.items.map { it.name }, actualNames)
    }

    override fun close() {
        try {
            settle()
            controller.pause().stop().destroy()
            tasks.clear()
        } finally {
            ReflectionHelpers.setStaticField(AsyncDifferConfig.Builder::class.java, "sDiffExecutor", previousExecutor)
        }
    }
}
