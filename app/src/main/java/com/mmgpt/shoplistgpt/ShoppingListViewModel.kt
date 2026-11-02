package com.mmgpt.shoplistgpt

import android.app.Application
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.time.LocalDate

/**
 * Holds the shopping categories loaded from the spreadsheet. Each category
 * represents a page in the UI containing a heading and the items beneath it.
 */

class ShoppingListViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs =
        getApplication<Application>().getSharedPreferences("state", Context.MODE_PRIVATE)
    private val _categories = MutableLiveData<List<ShoppingCategory>>(emptyList())
    private val _planDate = MutableLiveData<LocalDate?>(null)

    init {
        loadState()
    }

    /**
     * The date when planning started. Null if no plan exists yet.
     */
    val planDate: LiveData<LocalDate?> = _planDate

    /**
     * Categories exposed to the UI. Each category will be displayed as a page
     * containing a heading and its items.
     */
    val categories: LiveData<List<ShoppingCategory>> = _categories

    fun setPlanDate(date: LocalDate?) {
        _planDate.value = date
        saveState()
    }

    fun setCategories(list: List<ShoppingCategory>) {
        _categories.value = list
        saveState()
    }

    fun checkItem(item: ShoppingItem) {
        updateItem(item)
    }

    fun setAllChecked(categoryIndex: Int, checked: Boolean) {
        val current = _categories.value?.toMutableList() ?: return
        if (categoryIndex !in current.indices) return
        val items = current[categoryIndex].items.map { item ->
            if (item.isHeading) item else item.copy(isChecked = checked)
        }
        current[categoryIndex] = current[categoryIndex].copy(items = items)
        _categories.value = current
        saveState()
    }

    fun flagItem(item: ShoppingItem) {
        // Toggle the flagged state of the selected item. When called again on a
        // flagged item, it will be cleared.
        updateItem(item.copy(isFlagged = !item.isFlagged))
    }

    fun reorderItem(categoryIndex: Int, fromIndex: Int, toIndex: Int) {
        val current = _categories.value?.toMutableList() ?: return
        if (categoryIndex !in current.indices) return
        val items = current[categoryIndex].items.toMutableList()
        if (fromIndex !in items.indices || toIndex !in items.indices) return
        val item = items.removeAt(fromIndex)
        items.add(toIndex, item)
        current[categoryIndex] = current[categoryIndex].copy(items = items)
        _categories.value = current
        saveState()
    }

    fun addItem(categoryIndex: Int, position: Int, name: String) {
        val current = _categories.value?.toMutableList() ?: return
        if (categoryIndex !in current.indices) return
        val items = current[categoryIndex].items.toMutableList()
        val newId = current.flatMap { it.items }.maxOfOrNull { it.id }?.plus(1) ?: 0L
        val newItem = ShoppingItem(id = newId, name = name)
        val pos = position.coerceIn(0, items.size)
        items.add(pos, newItem)
        current[categoryIndex] = current[categoryIndex].copy(items = items)
        _categories.value = current
        saveState()
    }

    fun editItem(item: ShoppingItem, newName: String) {
        updateItem(item.copy(name = newName))
    }

    fun deleteItem(item: ShoppingItem) {
        _categories.value = _categories.value?.map { cat ->
            cat.copy(items = cat.items.filterNot { it.id == item.id })
        }
        saveState()
    }

    private fun updateItem(updated: ShoppingItem) {
        _categories.value = _categories.value?.map { category ->
            category.copy(
                items = category.items.map { existing ->
                    if (existing.id == updated.id) updated else existing
                }
            )
        }
        saveState()
    }

    fun addCategory(position: Int, heading: String) {
        val current = _categories.value?.toMutableList() ?: mutableListOf()
        val newId = current.maxOfOrNull { it.id }?.plus(1) ?: 0L
        val pos = position.coerceIn(0, current.size)
        current.add(pos, ShoppingCategory(newId, heading, emptyList()))
        _categories.value = current
        saveState()
    }

    fun editCategory(index: Int, newHeading: String) {
        val current = _categories.value?.toMutableList() ?: return
        if (index !in current.indices) return
        current[index] = current[index].copy(heading = newHeading)
        _categories.value = current
        saveState()
    }

    fun deleteCategory(index: Int) {
        val current = _categories.value?.toMutableList() ?: return
        if (index !in current.indices) return
        current.removeAt(index)
        _categories.value = current
        saveState()
    }

    fun reorderCategory(fromIndex: Int, toIndex: Int) {
        val current = _categories.value?.toMutableList() ?: return
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val cat = current.removeAt(fromIndex)
        current.add(toIndex, cat)
        _categories.value = current
        saveState()
    }

    /**
     * Places a category beside another by identity. The displayed positions may be
     * from an older ListAdapter snapshot, so repeated drag callbacks must describe
     * the same relative placement instead of swapping current model indices.
     */
    fun moveCategory(categoryId: Long, targetId: Long, afterTarget: Boolean): Boolean {
        val current = _categories.value?.toMutableList() ?: return false
        val from = current.indexOfFirst { it.id == categoryId }
        val target = current.indexOfFirst { it.id == targetId }
        if (from < 0 || target < 0 || from == target) return false
        val destination = target - (if (from < target) 1 else 0) +
            (if (afterTarget) 1 else 0)
        if (from == destination) return true
        val category = current.removeAt(from)
        current.add(destination, category)
        _categories.value = current
        saveState()
        return true
    }

    fun clearAllCategories() {
        _categories.value = emptyList()
        saveState()
    }

    fun saveState() {
        val obj = JSONObject()
        _planDate.value?.let { obj.put("planDate", it.toString()) }
        val cats = JSONArray()
        _categories.value?.forEach { cat ->
            val catObj = JSONObject()
            catObj.put("id", cat.id)
            catObj.put("heading", cat.heading)
            val itemsArr = JSONArray()
            cat.items.forEach { item ->
                val itemObj = JSONObject()
                itemObj.put("id", item.id)
                itemObj.put("name", item.name)
                itemObj.put("isChecked", item.isChecked)
                itemObj.put("isFlagged", item.isFlagged)
                itemObj.put("isHeading", item.isHeading)
                itemObj.put("isStruck", item.isStruck)
                itemObj.put("isPlaceholder", item.isPlaceholder)
                itemsArr.put(itemObj)
            }
            catObj.put("items", itemsArr)
            cats.put(catObj)
        }
        obj.put("categories", cats)
        prefs.edit().putString("state", obj.toString()).apply()
    }

    private fun loadState() {
        val json = prefs.getString("state", null) ?: return
        val obj = JSONObject(json)
        val plan = obj.optString("planDate", null)
        _planDate.value = plan?.let { LocalDate.parse(it) }
        val catsArr = obj.optJSONArray("categories") ?: JSONArray()
        val catsList = mutableListOf<ShoppingCategory>()
        for (i in 0 until catsArr.length()) {
            val catObj = catsArr.getJSONObject(i)
            val itemsArr = catObj.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<ShoppingItem>()
            for (j in 0 until itemsArr.length()) {
                val itObj = itemsArr.getJSONObject(j)
                items.add(
                    ShoppingItem(
                        id = itObj.getLong("id"),
                        name = itObj.getString("name"),
                        isChecked = itObj.optBoolean("isChecked"),
                        isFlagged = itObj.optBoolean("isFlagged"),
                        isHeading = itObj.optBoolean("isHeading"),
                        isStruck = itObj.optBoolean("isStruck"),
                        isPlaceholder = itObj.optBoolean("isPlaceholder")
                    )
                )
            }
            catsList.add(
                ShoppingCategory(
                    id = catObj.getLong("id"),
                    heading = catObj.getString("heading"),
                    items = items
                )
            )
        }
        _categories.value = catsList
    }
}
