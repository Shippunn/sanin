package ani.sanin.media

import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.databinding.ItemSearchHeaderBinding
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName

abstract class HeaderInterface : RecyclerView.Adapter<HeaderInterface.SearchHeaderViewHolder>() {
    private val itemViewType = 6969
    var search: Runnable? = null
    var requestFocus: Runnable? = null
    protected var textWatcher: TextWatcher? = null
    protected lateinit var searchHistoryAdapter: SearchHistoryAdapter
    protected lateinit var binding: ItemSearchHeaderBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchHeaderViewHolder {
        val binding =
            ItemSearchHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchHeaderViewHolder(binding)
    }

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
            binding.searchByImage.visibility = View.VISIBLE
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
            binding.clearHistory.visibility = View.GONE
            binding.searchHistoryList.visibility = View.GONE
            binding.searchByImage.visibility = View.GONE
        }
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
            if (searchHistoryAdapter.itemCount > 0) View.VISIBLE else View.GONE
    }

    fun addHistory() {
        if (::searchHistoryAdapter.isInitialized && binding.searchBarText.text.toString()
                .isNotBlank()
        ) searchHistoryAdapter.add(binding.searchBarText.text.toString())
    }

    inner class SearchHeaderViewHolder(val binding: ItemSearchHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int = 1

    override fun getItemViewType(position: Int): Int {
        return itemViewType
    }
}