package ani.sanin.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import ani.sanin.R
import ani.sanin.databinding.ActivitySettingsAnimeBinding
import ani.sanin.initActivity
import ani.sanin.media.MediaType
import ani.sanin.navBarHeight
import ani.sanin.restartApp
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.customAlertDialog
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsAnimeActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsAnimeBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this
        binding = ActivitySettingsAnimeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {

            settingsAnimeLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            animeSettingsBack.isFocusable = true
            animeSettingsBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
            FocusEffectUtil.applyFocusListener(animeSettingsBack)

            settingsRecyclerView.adapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = 1,
                        name = getString(R.string.player_settings),
                        desc = getString(R.string.player_settings_desc),
                        icon = R.drawable.ic_round_video_settings_24,
                        onClick = {
                            startActivity(Intent(context, PlayerSettingsActivity::class.java))
                        },
                        isActivity = true
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.prefer_dub),
                        desc = getString(R.string.prefer_dub_desc),
                        icon = R.drawable.ic_round_audiotrack_24,
                        isChecked = PrefManager.getVal(PrefName.PreferDub),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.PreferDub, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.include_list),
                        desc = getString(R.string.include_list_anime_desc),
                        icon = R.drawable.view_list_24,
                        isChecked = PrefManager.getVal(PrefName.IncludeAnimeList),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.IncludeAnimeList, isChecked)
                            restartApp()
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Pause Overlay",
                        desc = "Show pause overlay when video is paused",
                        icon = R.drawable.ic_round_pause_24,
                        isChecked = PrefManager.getVal(PrefName.PauseOverlay),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.PauseOverlay, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Gesture Sliders",
                        desc = "Brightness/volume sliders via vertical gestures",
                        icon = R.drawable.ic_round_swipe_vertical_24,
                        isChecked = PrefManager.getVal(PrefName.GestureSliders),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.GestureSliders, isChecked)
                        }
                    ),
                    Settings(
                        type = 1,
                        name = "Auto-Hide Timeout",
                        desc = "Seconds before player controls auto-hide",
                        icon = R.drawable.ic_round_history_24,
                        onClick = {
                            context.customAlertDialog().apply {
                                setTitle("Auto-Hide Timeout")
                                val values = arrayOf("2s", "3s", "4s", "5s", "6s", "8s", "10s")
                                singleChoiceItems(
                                    values,
                                    PrefManager.getVal<Int>(PrefName.AutoHideTimeout) / 2 - 1
                                ) { index ->
                                    PrefManager.setVal(PrefName.AutoHideTimeout, (index + 1) * 2)
                                }
                                show()
                            }
                        }
                    ),
                    Settings(
                        type = 1,
                        name = "Buffer Size",
                        desc = "Video buffer size in MB",
                        icon = R.drawable.ic_round_sd_card_24,
                        onClick = {
                            context.customAlertDialog().apply {
                                setTitle("Buffer Size")
                                val values = arrayOf("16 MB", "32 MB", "64 MB", "128 MB")
                                singleChoiceItems(
                                    values,
                                    when (PrefManager.getVal<Int>(PrefName.BufferSize)) {
                                        16 -> 0; 32 -> 1; 64 -> 2; 128 -> 3; else -> 1
                                    }
                                ) { index ->
                                    PrefManager.setVal(PrefName.BufferSize, intArrayOf(16, 32, 64, 128)[index])
                                }
                                show()
                            }
                        }
                    ),
                    Settings(
                        type = 1,
                        name = "Decoding Mode",
                        desc = "Choose video decoder (restart player to apply)",
                        icon = R.drawable.ic_round_brightness_high_24,
                        onClick = {
                            context.customAlertDialog().apply {
                                setTitle("Decoding Mode")
                                val values = arrayOf("Hardware (MediaCodec)", "Software (FFmpeg)")
                                singleChoiceItems(
                                    values,
                                    PrefManager.getVal<Int>(PrefName.DecodingMode)
                                ) { index ->
                                    PrefManager.setVal(PrefName.DecodingMode, index)
                                }
                                show()
                            }
                        }
                    ),
                    Settings(
                        type = 1,
                        name = "Subtitle Render Mode",
                        desc = "Canvas=CPU (better for TV), OpenGL=GPU (better for phone)",
                        icon = R.drawable.ic_round_text_fields_24,
                        onClick = {
                            context.customAlertDialog().apply {
                                setTitle("Subtitle Render Mode")
                                val values = arrayOf("Canvas (TV default)", "OpenGL (Phone default)")
                                singleChoiceItems(
                                    values,
                                    PrefManager.getVal<Int>(PrefName.SubtitleRenderMode)
                                ) { index ->
                                    PrefManager.setVal(PrefName.SubtitleRenderMode, index)
                                }
                                show()
                            }
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Smart Source Persistence",
                        desc = "Remember source selection across sessions",
                        icon = R.drawable.ic_round_source_24,
                        isChecked = PrefManager.getVal(PrefName.SmartSourcePersistence),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.SmartSourcePersistence, isChecked)
                        }
                    ),
                )
            )
            settingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }

            var previousEp: View = when (PrefManager.getVal<Int>(PrefName.AnimeDefaultView)) {
                0 -> settingsEpList
                1 -> settingsEpGrid
                2 -> settingsEpCompact
                else -> settingsEpList
            }
            previousEp.alpha = 1f
            fun uiEp(mode: Int, current: View) {
                previousEp.alpha = 0.33f
                previousEp = current
                current.alpha = 1f
                PrefManager.setVal(PrefName.AnimeDefaultView, mode)
            }

            settingsEpList.isFocusable = true
            FocusEffectUtil.applyFocusListener(settingsEpList)
            settingsEpList.setOnClickListener {
                uiEp(0, it)
            }

            settingsEpGrid.isFocusable = true
            FocusEffectUtil.applyFocusListener(settingsEpGrid)
            settingsEpGrid.setOnClickListener {
                uiEp(1, it)
            }

            settingsEpCompact.isFocusable = true
            FocusEffectUtil.applyFocusListener(settingsEpCompact)
            settingsEpCompact.setOnClickListener {
                uiEp(2, it)
            }

        }
    }
}
