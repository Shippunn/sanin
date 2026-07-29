package ani.sanin.home

import android.content.Context.INPUT_METHOD_SERVICE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import ani.sanin.R
import ani.sanin.Refresh
import ani.sanin.connections.anilist.Anilist
import ani.sanin.databinding.FragmentLibraryBinding
import ani.sanin.getThemeColor
import ani.sanin.media.user.ListFragment
import ani.sanin.media.user.ListViewPagerAdapter
import ani.sanin.media.user.ListViewModel
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.TvKeyboardUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryFragment : Fragment() {
    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private var selectedTabIdx = 0
    private var viewPagerAttached = false

    private val model: ListViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val primaryColor = requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
        val primaryTextColor = requireContext().getThemeColor(com.google.android.material.R.attr.colorPrimary)
        val secondaryTextColor = requireContext().getThemeColor(com.google.android.material.R.attr.colorOutline)

        binding.listTabLayout.setBackgroundColor(primaryColor)
        binding.listAppBar.setBackgroundColor(primaryColor)
        binding.listTitle.setTextColor(primaryTextColor)
        binding.listTabLayout.setTabTextColors(secondaryTextColor, primaryTextColor)
        binding.listTabLayout.setSelectedTabIndicatorColor(primaryTextColor)

        binding.listTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedTabIdx = tab?.position ?: 0
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        val defaultKeys = listOf(
            "Reading", "Watching", "Completed", "Paused", "Dropped", "Planning",
            "Favourites", "Rewatching", "Rereading", "All"
        )
        val userKeys: Array<String> = resources.getStringArray(R.array.keys)

        model.getLists().observe(viewLifecycleOwner) { it ->
            if (it != null) {
                binding.listProgressBar.visibility = View.GONE
                if (!viewPagerAttached) {
                    binding.listViewPager.adapter = ListViewPagerAdapter(it.size, false, requireActivity())
                    val keys = it.keys.toList()
                        .map { key -> userKeys.getOrNull(defaultKeys.indexOf(key)) ?: key }
                    val values = it.values.toList()
                    TabLayoutMediator(binding.listTabLayout, binding.listViewPager) { tab, position ->
                        tab.text = "${keys[position]} (${values[position].size})"
                    }.attach()
                    viewPagerAttached = true
                    binding.listViewPager.setCurrentItem(selectedTabIdx, false)
                }
            }
        }

        val live = Refresh.activity.getOrPut(this.hashCode()) { MutableLiveData(true) }
        live.observe(viewLifecycleOwner) {
            if (it) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        model.loadLists(true, Anilist.userid ?: 0)
                    }
                    live.postValue(false)
                }
            }
        }

        TvKeyboardUtil.setupTvInput(binding.searchViewText)

        if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
            binding.listSort.visibility = View.GONE
        }
        binding.listSort.setOnClickListener {
            val popup = PopupMenu(requireContext(), it)
            popup.setOnMenuItemClickListener { item ->
                val sort = when (item.itemId) {
                    R.id.score -> "score"
                    R.id.title -> "title"
                    R.id.updated -> "updatedAt"
                    R.id.release -> "release"
                    else -> null
                }
                PrefManager.setVal(PrefName.AnimeListSortOrder, sort ?: "")
                binding.listProgressBar.visibility = View.VISIBLE
                binding.listViewPager.adapter = null
                viewPagerAttached = false
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        model.loadLists(true, Anilist.userid ?: 0, sort)
                    }
                }
                true
            }
            popup.inflate(R.menu.list_sort_menu)
            popup.show()
        }

        binding.filter.setOnClickListener {
            val genres = PrefManager.getVal<Set<String>>(PrefName.GenresList).toMutableSet().sorted()
            val popup = PopupMenu(requireContext(), it)
            popup.menu.add("All")
            genres.forEach { genre -> popup.menu.add(genre) }
            popup.setOnMenuItemClickListener { menuItem ->
                model.filterLists(menuItem.title.toString())
                true
            }
            popup.show()
        }

        binding.random.setOnClickListener {
            val currentTab = binding.listTabLayout.getTabAt(binding.listTabLayout.selectedTabPosition)
            val tag = "f" + currentTab?.position.toString()
            val currentFragment = requireActivity().supportFragmentManager.findFragmentByTag(tag) as? ListFragment
            currentFragment?.randomOptionClick()
        }

        binding.search.setOnClickListener {
            toggleSearchView(binding.searchView.isVisible)
            if (!binding.searchView.isVisible) {
                model.unfilterLists()
            }
        }

        binding.searchViewText.addTextChangedListener {
            model.searchLists(binding.searchViewText.text.toString())
        }
    }

    private fun toggleSearchView(isVisible: Boolean) {
        if (isVisible) {
            binding.searchView.visibility = View.GONE
            binding.searchViewText.text.clear()
        } else {
            binding.searchView.visibility = View.VISIBLE
            binding.searchViewText.requestFocus()
            TvKeyboardUtil.showKeyboardDelayed(binding.searchViewText)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        viewPagerAttached = false
    }
}
