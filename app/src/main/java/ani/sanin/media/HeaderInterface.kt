package ani.sanin.media

import android.text.TextWatcher
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import ani.sanin.R
import ani.sanin.databinding.ItemSearchHeaderBinding
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName

abstract class HeaderInterface(
    val activity: SearchActivity,
    protected val binding: ItemSearchHeaderBinding
) {
    var search: Runnable? = null
    var requestFocus: Runnable? = null
    protected var textWatcher: TextWatcher? = null
    protected lateinit var searchHistoryAdapter: SearchHistoryAdapter

    abstract fun bind()

    protected open fun imageSearchVisible(): Boolean = true

    fun setHistoryVisibility(visible: Boolean) {
        if (visible) {
            if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.SearchHeaderAnimations)) {
                binding.searchResultLayout.startAnimation(fadeOutAnimation())
                binding.searchHistoryList.startAnimation(fadeInAnimation())
            } else {
                binding.searchResultLayout.alpha = 0f
                binding.searchHistoryList.alpha = 1f
            }
            binding.searchResultLayout.visibility = View.GONE
            binding.searchHistoryList.visibility = View.VISIBLE
            binding.searchHistoryLabel.visibility = View.VISIBLE
            binding.searchByImage.visibility = if (imageSearchVisible()) View.VISIBLE else View.GONE
            updateClearHistoryVisibility()
        } else {
            if (binding.searchResultLayout.visibility != View.VISIBLE) {
                if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.SearchHeaderAnimations)) {
                    binding.searchResultLayout.startAnimation(fadeInAnimation())
                    binding.searchHistoryList.startAnimation(fadeOutAnimation())
                } else {
                    binding.searchResultLayout.alpha = 1f
                    binding.searchHistoryList.alpha = 0f
                }
            }

            binding.searchResultLayout.visibility = View.VISIBLE
            binding.searchHistoryList.visibility = View.GONE
            binding.searchHistoryLabel.visibility = View.GONE
            binding.clearHistory.visibility = View.GONE
            binding.searchByImage.visibility = View.GONE
        }
        updateActionRowFocusTargets()
    }

    protected fun updateActionRowFocusTargets() {
        val historyFocusable = binding.searchHistoryList.visibility == View.VISIBLE &&
            ::searchHistoryAdapter.isInitialized && searchHistoryAdapter.itemCount > 0
        val target =
            if (historyFocusable) R.id.searchHistoryTextView else R.id.searchRecyclerView
        binding.searchFilter.nextFocusDownId = target
        binding.clearHistory.nextFocusDownId = target
        binding.searchList.nextFocusDownId = target
        binding.searchAdultCheck.nextFocusDownId = target
        binding.searchByImage.nextFocusDownId = target
    }

    private fun fadeInAnimation(): Animation {
        return AlphaAnimation(0f, 1f).apply {
            duration = 150
        }
    }

    protected fun fadeOutAnimation(): Animation {
        return AlphaAnimation(1f, 0f).apply {
            duration = 150
        }
    }

    protected fun updateClearHistoryVisibility() {
        binding.clearHistory.visibility =
            if (::searchHistoryAdapter.isInitialized && searchHistoryAdapter.itemCount > 0) View.VISIBLE else View.GONE
        updateActionRowFocusTargets()
    }

    fun addHistory() {
        if (::searchHistoryAdapter.isInitialized && binding.searchBarText.text.toString()
                .isNotBlank()
        ) searchHistoryAdapter.add(binding.searchBarText.text.toString())
    }
}
