package ani.sanin.settings

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import ani.sanin.R
import ani.sanin.copyToClipboard
import ani.sanin.databinding.ActivitySettingsExtensionsBinding
import ani.sanin.databinding.DialogUserAgentBinding
import ani.sanin.databinding.ItemRepositoryBinding
import ani.sanin.initActivity
import ani.sanin.media.MediaType
import ani.sanin.navBarHeight
import ani.sanin.restartApp
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.util.FocusEffectUtil
import ani.sanin.themes.ThemeManager
import ani.sanin.util.customAlertDialog
import eu.kanade.domain.base.BasePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsExtensionsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsExtensionsBinding
    private val extensionInstaller = Injekt.get<BasePreferences>().extensionInstaller()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this
        binding = ActivitySettingsExtensionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            settingsExtensionsLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            extensionSettingsBack.isFocusable = true
            FocusEffectUtil.applyFocusListener(extensionSettingsBack)
            extensionSettingsBack.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
            fun setExtensionOutput(repoInventory: ViewGroup, type: MediaType) {
                repoInventory.removeAllViews()
                val prefName = PrefName.AnimeExtensionRepos
                PrefManager.getVal<Set<String>>(prefName).forEach { item ->
                    val view = ItemRepositoryBinding.inflate(
                        LayoutInflater.from(repoInventory.context), repoInventory, true
                    )
                    view.repositoryItem.text =
                        item.removePrefix("https://raw.githubusercontent.com/")

                    view.repositoryItem.setOnLongClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        copyToClipboard(item, true)
                        true
                    }
                }
                repoInventory.isVisible = repoInventory.childCount > 0
            }

            settingsRecyclerView.adapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = 1,
                        name = getString(R.string.anime_add_repository),
                        desc = getString(R.string.anime_add_repository_desc),
                        icon = R.drawable.ic_github,
                        onClick = {
                            val animeRepos =
                                PrefManager.getVal<Set<String>>(PrefName.AnimeExtensionRepos)
                            AddRepositoryBottomSheet.newInstance(
                                MediaType.ANIME,
                                animeRepos.toList(),
                                onRepositoryAdded = { input, mediaType ->
                                    AddRepositoryBottomSheet.addRepo(input, mediaType)
                                    setExtensionOutput(it.attachView, mediaType)
                                },
                                onRepositoryRemoved = { item, mediaType ->
                                    AddRepositoryBottomSheet.removeRepo(item, mediaType)
                                    setExtensionOutput(it.attachView, mediaType)
                                }
                            ).show(supportFragmentManager, "add_repo")
                        },
                        attach = {
                            setExtensionOutput(it.attachView, MediaType.ANIME)
                        }
                    ),
                    Settings(
                        type = 1,
                        name = getString(R.string.user_agent),
                        desc = getString(R.string.user_agent_desc),
                        icon = R.drawable.ic_round_video_settings_24,
                        onClick = {
                            val dialogView = DialogUserAgentBinding.inflate(layoutInflater)
                            val editText = dialogView.userAgentTextBox
                            editText.setText(PrefManager.getVal<String>(PrefName.DefaultUserAgent))
                            context.customAlertDialog().apply {
                                setTitle(R.string.user_agent)
                                setCustomView(dialogView.root)
                                setPosButton(R.string.ok) {
                                    PrefManager.setVal(
                                        PrefName.DefaultUserAgent,
                                        editText.text.toString()
                                    )
                                }
                                setNeutralButton(R.string.reset) {
                                    PrefManager.removeVal(PrefName.DefaultUserAgent)
                                    editText.setText("")
                                }
                                setNegButton(R.string.cancel)
                            }.show()
                        }
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.proxy),
                        desc = getString(R.string.proxy_desc),
                        icon = R.drawable.swap_horizontal_circle_24,
                        isChecked = PrefManager.getVal(PrefName.EnableSocks5Proxy),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.EnableSocks5Proxy, isChecked)
                            restartApp()
                        }
                    ),
                    Settings(
                        type = 1,
                        name = getString(R.string.proxy_setup),
                        desc = getString(R.string.proxy_setup_desc),
                        icon = R.drawable.lan_24,
                        onClick = {
                            ProxyDialogFragment().show(supportFragmentManager, "dialog")
                        }
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.force_legacy_installer),
                        desc = getString(R.string.force_legacy_installer_desc),
                        icon = R.drawable.ic_round_new_releases_24,
                        isChecked = extensionInstaller.get() == BasePreferences.ExtensionInstaller.LEGACY,
                        switch = { isChecked, _ ->
                            if (isChecked) {
                                extensionInstaller.set(BasePreferences.ExtensionInstaller.LEGACY)
                            } else {
                                extensionInstaller.set(BasePreferences.ExtensionInstaller.PACKAGEINSTALLER)
                            }
                        }

                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.skip_loading_extension_icons),
                        desc = getString(R.string.skip_loading_extension_icons_desc),
                        icon = R.drawable.ic_round_no_icon_24,
                        isChecked = PrefManager.getVal(PrefName.SkipExtensionIcons),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.SkipExtensionIcons, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.NSFWExtention),
                        desc = getString(R.string.NSFWExtention_desc),
                        icon = R.drawable.ic_round_nsfw_24,
                        isChecked = PrefManager.getVal(PrefName.NSFWExtension),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.NSFWExtension, isChecked)
                        }

                    )
                )
            )
            settingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }
        }

    }
}