package ani.sanin.themes

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import ani.sanin.R
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions


class ThemeManager(private val context: Activity) {
    fun applyTheme(fromImage: Bitmap? = null) {
        applyUIScale()
        val darkTheme = isDarkThemeActive(context)
        val oledMode: Int = if (darkTheme) PrefManager.getVal(PrefName.OledMode) else 0
        val useOLED = oledMode >= 1
        val useCustomTheme: Boolean = PrefManager.getVal(PrefName.UseCustomTheme)
        val customTheme: Int = PrefManager.getVal(PrefName.CustomThemeInt)
        val useSource: Boolean = PrefManager.getVal(PrefName.UseSourceTheme)
        val useMaterial: Boolean = PrefManager.getVal(PrefName.UseMaterialYou)
        val accentColorIndex: Int = PrefManager.getVal(PrefName.AccentColor)

        val effectiveCustom = if (accentColorIndex > 0) {
            accentColorToInt(accentColorIndex)
        } else if (useCustomTheme) {
            customTheme
        } else {
            null
        }

        if (useSource) {
            val returnedEarly = applyDynamicColors(
                useMaterial,
                context,
                useOLED,
                fromImage,
                useCustom = effectiveCustom
            )
            if (!returnedEarly && effectiveCustom == null) return
        } else if (accentColorIndex > 0 && !useCustomTheme) {
            // Accent color only — skip DynamicColors (no-op or interfering on TV),
            // the mapped hardcoded theme is applied below via setTheme()
        } else if (useCustomTheme) {
            val returnedEarly =
                applyDynamicColors(useMaterial, context, useOLED, useCustom = effectiveCustom)
            if (!returnedEarly && effectiveCustom == null) return
        } else {
            val returnedEarly = applyDynamicColors(useMaterial, context, useOLED, useCustom = null)
            if (!returnedEarly) return
        }
        val theme: String = if (accentColorIndex > 0 && !useSource && !useCustomTheme) {
            accentColorToTheme(accentColorIndex)
        } else {
            PrefManager.getVal(PrefName.Theme)
        }

        val themeToApply = when (theme) {
            "SANIN" -> if (useOLED) R.style.Theme_Sanin_SaninOLED else R.style.Theme_Sanin_Sanin
            "OCEAN" -> if (useOLED) R.style.Theme_Sanin_OceanOLED else R.style.Theme_Sanin_Ocean
            "BLOOD" -> if (useOLED) R.style.Theme_Sanin_BloodOLED else R.style.Theme_Sanin_Blood
            "LIME" -> if (useOLED) R.style.Theme_Sanin_LimeOLED else R.style.Theme_Sanin_Lime
            "SUN" -> if (useOLED) R.style.Theme_Sanin_SunOLED else R.style.Theme_Sanin_Sun
            "KURAMA" -> if (useOLED) R.style.Theme_Sanin_KuramaOLED else R.style.Theme_Sanin_Kurama
            "SAIKOU" -> if (useOLED) R.style.Theme_Sanin_SaikouOLED else R.style.Theme_Sanin_Saikou
            "INDIGO" -> if (useOLED) R.style.Theme_Sanin_IndigoOLED else R.style.Theme_Sanin_Indigo
            "MONOCHROME" -> if (useOLED) R.style.Theme_Sanin_MonochromeOLED else R.style.Theme_Sanin_Monochrome
            else -> if (useOLED) R.style.Theme_Sanin_SaninOLED else R.style.Theme_Sanin_Sanin
        }

        val window = context.window
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = 0x00000000
        context.setTheme(themeToApply)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR

        if (oledMode == 2 || oledMode == 3 || oledMode == 4) {
            val tv = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
            val gradientDir: Int = PrefManager.getVal(PrefName.GradientDirection)
            OledBackgroundManager.apply(context, oledMode, tv.data, gradientDir)
        } else {
            OledBackgroundManager.remove(context)
        }
    }

    private fun applyUIScale() {
        val scale = PrefManager.getVal<Float>(PrefName.UIScale)
        if (scale == 1.0f) return
        val metrics: DisplayMetrics = context.resources.displayMetrics
        metrics.scaledDensity = metrics.density * scale
    }

    fun setWindowFlag(activity: Activity, bits: Int, on: Boolean) {
        val win: Window = activity.window
        val winParams: WindowManager.LayoutParams = win.attributes
        if (on) {
            winParams.flags = winParams.flags or bits
        } else {
            winParams.flags = winParams.flags and bits.inv()
        }
        win.attributes = winParams
    }

    private fun applyDynamicColors(
        useMaterialYou: Boolean,
        context: Context,
        useOLED: Boolean,
        bitmap: Bitmap? = null,
        useCustom: Int? = null
    ): Boolean {
        val builder = DynamicColorsOptions.Builder()
        var needMaterial = true

        if (bitmap != null) {
            builder.setContentBasedSource(bitmap)
            needMaterial = false
        } else if (useCustom != null) {
            builder.setContentBasedSource(useCustom)
            needMaterial = false
        }

        if (useOLED) {
            builder.setThemeOverlay(R.style.AppTheme_Amoled)
        }
        if (needMaterial && !useMaterialYou) return true

        val options = builder.build()

        val activity = context as Activity
        DynamicColors.applyToActivityIfAvailable(activity, options)

        return false
    }

    private fun isDarkThemeActive(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            Configuration.UI_MODE_NIGHT_NO -> false
            Configuration.UI_MODE_NIGHT_UNDEFINED -> false
            else -> false
        }
    }

    companion object {
        fun accentColorToInt(index: Int): Int = when (index) {
            0 -> Color.parseColor("#005B96")
            1 -> Color.parseColor("#00BCD4")
            2 -> Color.parseColor("#8B0000")
            3 -> Color.parseColor("#8BC34A")
            4 -> Color.parseColor("#FFD700")
            5 -> Color.parseColor("#FF4D00")
            6 -> Color.parseColor("#FF007F")
            7 -> Color.parseColor("#4B0082")
            else -> Color.parseColor("#005B96")
        }

        fun accentColorToTheme(index: Int): String = when (index) {
            0 -> "SANIN"
            1 -> "OCEAN"
            2 -> "BLOOD"
            3 -> "LIME"
            4 -> "SUN"
            5 -> "KURAMA"
            6 -> "SAIKOU"
            7 -> "INDIGO"
            8 -> "MONOCHROME"
            else -> "SANIN"
        }

        fun applyUIScale(activity: Activity) {
            val scale = PrefManager.getVal<Float>(PrefName.UIScale)
            if (scale == 1.0f) return
            val metrics: DisplayMetrics = activity.resources.displayMetrics
            metrics.scaledDensity = metrics.density * scale
        }

        enum class Theme(val theme: String) {
            SANIN("SANIN"),
            OCEAN("OCEAN"),
            BLOOD("BLOOD"),
            LIME("LIME"),
            SUN("SUN"),
            KURAMA("KURAMA"),
            SAIKOU("SAIKOU"),
            INDIGO("INDIGO"),
            MONOCHROME("MONOCHROME");

            companion object {
                fun fromString(value: String): Theme {
                    return entries.find { it.theme == value } ?: SANIN
                }
            }
        }
    }
}
