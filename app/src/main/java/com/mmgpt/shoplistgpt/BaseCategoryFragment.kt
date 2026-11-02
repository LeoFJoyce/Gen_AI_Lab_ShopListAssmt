package com.mmgpt.shoplistgpt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.mmgpt.shoplistgpt.adapter.ItemAdapter
import com.mmgpt.shoplistgpt.databinding.FragmentCategoryBinding

/**
 * Owns the view lifecycle and category selection shared by Plan and Shop detail pages.
 * Mode-specific controls and rows are configured through hooks scoped to the current view.
 */
abstract class BaseCategoryFragment : Fragment() {
    protected val viewModel: ShoppingListViewModel by activityViewModels()
    protected var index: Int = 0
        private set

    private var binding: FragmentCategoryBinding? = null

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = arguments?.getInt(ARG_INDEX) ?: 0
    }

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val viewBinding = FragmentCategoryBinding.inflate(inflater, container, false)
        binding = viewBinding
        return viewBinding.root
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = checkNotNull(binding)
        viewBinding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = configureAdapter(viewBinding)
        viewBinding.recyclerView.adapter = adapter

        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            // Preserve existing behavior: unavailable indices do not select a different category.
            categories.getOrNull(index)?.let { category ->
                viewBinding.tvHeading.text = category.heading
                renderCategory(viewBinding, adapter, category)
            }
        }
    }

    protected abstract fun configureAdapter(binding: FragmentCategoryBinding): ItemAdapter

    protected abstract fun renderCategory(
        binding: FragmentCategoryBinding,
        adapter: ItemAdapter,
        category: ShoppingCategory
    )

    /** Release mode-specific listeners/helpers before discarding the view. */
    protected open fun releaseView(binding: FragmentCategoryBinding) {}

    final override fun onDestroyView() {
        binding?.let {
            releaseView(it)
            it.recyclerView.adapter = null
        }
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_INDEX = "index"

        internal fun <T : BaseCategoryFragment> withIndex(fragment: T, index: Int): T =
            fragment.apply { arguments = Bundle().apply { putInt(ARG_INDEX, index) } }
    }
}
