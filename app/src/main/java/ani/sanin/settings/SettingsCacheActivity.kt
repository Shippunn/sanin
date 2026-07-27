package ani.sanin.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import ani.sanin.R
import ani.sanin.connections.LogoApi
import ani.sanin.databinding.ActivitySettingsCacheBinding
import ani.sanin.initActivity
import ani.sanin.media.anime.ExoplayerView
import ani.sanin.navBarHeight
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsCacheActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsCacheBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)

        binding = ActivitySettingsCacheBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            settingsCacheLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            cacheSettingsBack.isFocusable = true
            cacheSettingsBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
            FocusEffectUtil.applyFocusListener(cacheSettingsBack)

            cacheClearButton.isFocusable = true
            FocusEffectUtil.applyFocusListener(cacheClearButton)
            cacheClearButton.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        Glide.get(this@SettingsCacheActivity).clearMemory()
                        LogoApi.clearCache()
                        withContext(Dispatchers.IO) {
                            this@SettingsCacheActivity.cacheDir.deleteRecursively()
                            this@SettingsCacheActivity.externalCacheDir?.deleteRecursively()
                            Glide.get(this@SettingsCacheActivity).clearDiskCache()
                            ExoplayerView.clearAllCaches()
                        }
                        snackString("Cache cleared")
                    } catch (e: Exception) {
                        snackString("Failed to clear cache: ${e.message}")
                    }
                }
            }

            cacheSmartTrim.isFocusable = true
            FocusEffectUtil.applyFocusListener(cacheSmartTrim)
            val isSmartTrimOn = PrefManager.getVal<Boolean>(PrefName.SmartTrim)
            cacheSmartTrim.isChecked = isSmartTrimOn
            cacheTrimSettings.visibility = if (isSmartTrimOn) View.VISIBLE else View.GONE
            cacheSmartTrim.setOnCheckedChangeListener { _, isChecked ->
                PrefManager.setVal(PrefName.SmartTrim, isChecked)
                cacheTrimSettings.visibility = if (isChecked) View.VISIBLE else View.GONE
            }

            cacheCapSlider.isFocusable = true
            val cap = PrefManager.getVal<Int>(PrefName.CacheCapMb)
            cacheCapSlider.progress = cap - 70
            cacheCapValue.text = cap.toString()
            cacheCapSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 70
                    cacheCapValue.text = value.toString()
                    PrefManager.setVal(PrefName.CacheCapMb, value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            cacheIntervalSlider.isFocusable = true
            val interval = PrefManager.getVal<Int>(PrefName.TrimIntervalMin)
            cacheIntervalSlider.progress = interval - 5
            cacheIntervalValue.text = interval.toString()
            cacheIntervalSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 5
                    cacheIntervalValue.text = value.toString()
                    PrefManager.setVal(PrefName.TrimIntervalMin, value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            cacheIntensitySlider.isFocusable = true
            val intensity = PrefManager.getVal<Int>(PrefName.TrimIntensity)
            cacheIntensitySlider.progress = intensity - 40
            cacheIntensityValue.text = intensity.toString()
            cacheIntensitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 40
                    cacheIntensityValue.text = value.toString()
                    PrefManager.setVal(PrefName.TrimIntensity, value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }
}
