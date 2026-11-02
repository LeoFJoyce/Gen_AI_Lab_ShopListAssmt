package com.mmgpt.shoplistgpt

import com.mmgpt.shoplistgpt.adapter.ItemAdapter
import com.mmgpt.shoplistgpt.databinding.FragmentCategoryBinding

class CategoryFragment : BaseCategoryFragment() {

    override fun configureAdapter(binding: FragmentCategoryBinding): ItemAdapter =
        ItemAdapter(onAction = { item, action ->
            when (action) {
                ItemAdapter.Action.CHECK -> viewModel.checkItem(item)
                ItemAdapter.Action.FLAG -> viewModel.flagItem(item)
                // EDIT and DELETE actions are not applicable in Shop.
                ItemAdapter.Action.EDIT, ItemAdapter.Action.DELETE -> {}
            }
        })

    override fun renderCategory(
        binding: FragmentCategoryBinding,
        adapter: ItemAdapter,
        category: ShoppingCategory
    ) {
        binding.tvCount.text = "${category.remainingCount}/${category.totalCount}"
        adapter.submitList(category.items)
    }

    companion object {
        fun newInstance(index: Int): CategoryFragment =
            withIndex(CategoryFragment(), index)
    }
}
