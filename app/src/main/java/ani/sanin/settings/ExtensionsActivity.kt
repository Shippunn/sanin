package ani.sanin.settings

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import ani.sanin.R
import ani.sanin.databinding.ActivityExtensionsBinding
import ani.sanin.initActivity
import ani.sanin.util.FocusEffectUtil
import ani.sanin.media.MediaType
import ani.sanin.navBarHeight
import ani.sanin.others.AndroidBug5497Workaround
import ani.sanin.others.LanguageMapper
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.TvKeyboardUtil
import ani.sanin.util.customAlertDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.util.Locale

class ExtensionsActivity : AppCompatActivity() {
    lateinit var binding: ActivityExtensionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityExtensionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)
        AndroidBug5497Workaround.assistActivity(this) {
            if (it) {
                binding.searchView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = statusBarHeight
                }
            } else {
                binding.searchView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = statusBarHeight + navBarHeight
                }
            }
        }

        binding.searchView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = statusBarHeight + navBarHeight
        }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        viewPager.offscreenPageLimit = 1

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> InstalledAnimeExtensionsFragment()
                    1 -> AnimeExtensionsFragment()
                    else -> AnimeExtensionsFragment()
                }
            }

        }

        val searchView: AutoCompleteTextView = findViewById(R.id.searchViewText)

        tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    searchView.setText("")
                    searchView.clearFocus()
                    tabLayout.clearFocus()
                    if (tab.text?.contains("Installed") == true) binding.languageselect.visibility =
                        View.GONE
                    else binding.languageselect.visibility = View.VISIBLE
                    viewPager.updateLayoutParams<ViewGroup.LayoutParams> {
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                    }

                    if (tab.text?.contains("Anime") == true) {
                        generateRepositoryButton(MediaType.ANIME)
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {
                    viewPager.updateLayoutParams<ViewGroup.LayoutParams> {
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    tabLayout.clearFocus()
                }

                override fun onTabReselected(tab: TabLayout.Tab) {
                    viewPager.updateLayoutParams<ViewGroup.LayoutParams> {
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    // Do nothing
                }
            }
        )

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Installed Anime"
                1 -> "Available Anime"
                2 -> "Installed Manga"
                3 -> "Available Manga"
                4 -> "Installed Novels"
                5 -> "Available Novels"
                else -> null
            }
        }.attach()


        searchView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val currentFragment =
                    supportFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
                if (currentFragment is SearchQueryHandler) {
                    currentFragment.updateContentBasedOnQuery(s?.toString()?.trim())
                }
            }
        })

        TvKeyboardUtil.setupTvInput(binding.searchViewText)

        initActivity(this)
        binding.languageselect.setOnClickListener {
            val languageOptions =
                LanguageMapper.Companion.Language.entries.map { entry ->
                    entry.name.lowercase().replace("_", " ")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }.toTypedArray()
            val listOrder: String = PrefManager.getVal(PrefName.LangSort)
            val index = LanguageMapper.Companion.Language.entries.toTypedArray()
                .indexOfFirst { it.code == listOrder }
            customAlertDialog().apply {
                setTitle("Language")
                singleChoiceItems(languageOptions, index) { selected ->
                    PrefManager.setVal(
                        PrefName.LangSort,
                        LanguageMapper.Companion.Language.entries[selected].code
                    )
                    val currentFragment =
                        supportFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
                    if (currentFragment is SearchQueryHandler) {
                        currentFragment.notifyDataChanged()
                    }
                }
                show()
            }
        }
        binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }
    }

    private fun generateRepositoryButton(type: MediaType) {
        binding.openSettingsButton.setOnClickListener {
            val repos = PrefManager.getVal<Set<String>>(PrefName.AnimeExtensionRepos)
            AddRepositoryBottomSheet.newInstance(
                type,
                repos.toList(),
                AddRepositoryBottomSheet::addRepo,
                AddRepositoryBottomSheet::removeRepo

            ).show(supportFragmentManager, "add_repo")
        }
    }
}

interface SearchQueryHandler {
    fun updateContentBasedOnQuery(query: String?)
    fun notifyDataChanged()
}
