package ani.sanin.settings

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.updateLayoutParams
import ani.sanin.R
import ani.sanin.databinding.ActivitySettingsAppearanceBinding
import ani.sanin.initActivity
import ani.sanin.navBarHeight
import ani.sanin.restartApp
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.customAlertDialog
import ani.sanin.util.NavPillCustomizer

class SettingsAppearanceActivity : AppCompatActivity() {

    lateinit var binding: ActivitySettingsAppearanceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        binding = ActivitySettingsAppearanceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)
        binding.appearanceContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }
        binding.appearanceBack.isFocusable = true
        binding.appearanceBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        FocusEffectUtil.applyFocusListener(
            binding.appearanceBack,
            binding.appearanceCardSize,
            binding.appearanceCardStyle,
            binding.appearanceCardOrientation,
            binding.appearanceCardTitlePosition,
            binding.appearanceHideRedDot,
            binding.appearanceBlurBanners,
            binding.appearancePersistSideRail,
            binding.appearanceAutoSideRail,
            binding.appearanceBannerBrightness,
            binding.appearanceCardGradientIntensity,
            binding.appearanceStandardCardRoundness,
            binding.appearanceContinueWatchingCardRoundness,
            binding.appearanceBlurRadius,
            binding.appearanceBlurSampling,
            binding.appearanceUiScale,
            binding.appearanceGlassMaster,
            binding.appearanceGlassNavPills,
            binding.appearanceGlassSideRail,
            binding.appearanceGlassServerSheet,
            binding.appearanceGlassListEditor,
            binding.appearanceGlassSourceSelector,
            binding.appearanceGlassEpisodeDrawer,
            binding.appearanceGlassKeyboard,
            binding.appearanceGlassBlurRadius,
            binding.appearanceGlassTintOpacity,
            binding.appearanceGlassVibrancy,
            binding.appearanceGlassChromaticAberration,
            binding.appearanceGlassRefractionHeight,
            binding.appearanceGlassRefractionAmount,
            binding.appearanceGlassDepth,
            binding.appearanceGlassSurfaceTint,
            binding.appearanceGlassTextColor,
            binding.appearanceNavPillHeight,
            binding.appearanceNavPillWidth,
            binding.appearanceNavPillSpacing,
            binding.appearanceNavPillIconSize,
            binding.appearanceNavPillIconColor,
            binding.appearanceNavPillCornerRadius,
            binding.appearanceThemeMode,
            binding.appearanceAccentColor,
            binding.appearanceOledMode,
            binding.appearanceHomeBannerMode,
            binding.appearanceHeroCardImage,
            binding.appearanceShowContinueWatching,
            binding.appearanceShowPlanned,
            binding.appearanceShowRecommendations,
            binding.appearanceShowTrending,
            binding.appearanceShowPopular,
            binding.appearanceShowRecent,
            binding.appearanceFocusEffect,
            binding.themeSectionHeader,
            binding.homeSectionHeader,
            binding.displaySectionHeader,
            binding.blurSectionHeader,
            binding.bannerSectionHeader,
            binding.sideRailSectionHeader,
            binding.glassSectionHeader,
        )

        binding.appearanceCardSize.isFocusable = true
        binding.appearanceCardSize.setOnClickListener {
            customAlertDialog().apply {
                setTitle("Card Size")
                val labels = arrayOf("Small (0.5x)", "Medium (0.75x)", "Normal (1x)", "Large (1.25x)", "X-Large (1.5x)", "XX-Large (1.75x)", "XXX-Large (2.0x)")
                val values = arrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                val current = PrefManager.getVal<Float>(PrefName.CardSize)
                val currentIdx = values.indexOfFirst { it == current }.coerceAtLeast(0)
                singleChoiceItems(labels, currentIdx) { index ->
                    PrefManager.setVal(PrefName.CardSize, values[index])
                    restartApp()
                }
                show()
            }
        }

        binding.appearanceCardStyle.isFocusable = true
        binding.appearanceCardStyle.setOnClickListener {
            customAlertDialog().apply {
                setTitle("Card Style")
                val labels = arrayOf("Rounded", "Compact")
                singleChoiceItems(labels, PrefManager.getVal<Int>(PrefName.CardStyle).coerceAtMost(1)) { index ->
                    PrefManager.setVal(PrefName.CardStyle, index)
                    restartApp()
                }
                show()
            }
        }

        binding.appearanceCardOrientation.isFocusable = true
        binding.appearanceCardOrientation.setOnClickListener {
            customAlertDialog().apply {
                setTitle("Card Orientation")
                val labels = arrayOf("Landscape", "Portrait")
                val current = PrefManager.getVal<Int>(PrefName.CardOrientation)
                singleChoiceItems(labels, current) { index ->
                    PrefManager.setVal(PrefName.CardOrientation, index)
                    restartApp()
                }
                show()
            }
        }

        binding.appearanceCardTitlePosition.isFocusable = true
        binding.appearanceCardTitlePosition.setOnClickListener {
            customAlertDialog().apply {
                setTitle("Card Title Position")
                val labels = arrayOf("Bottom Overlay", "Below Card", "Hidden")
                val current = PrefManager.getVal<Int>(PrefName.CardTitlePosition)
                singleChoiceItems(labels, current) { index ->
                    PrefManager.setVal(PrefName.CardTitlePosition, index)
                    restartApp()
                }
                show()
            }
        }

        binding.appearanceCardGradientIntensity.isFocusable = true
        binding.appearanceCardGradientIntensity.value = PrefManager.getVal(PrefName.CardGradientIntensity)
        binding.appearanceCardGradientIntensity.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.CardGradientIntensity, value)
        }

        binding.appearanceStandardCardRoundness.isFocusable = true
        binding.appearanceStandardCardRoundness.value =
            PrefManager.getVal<Int>(PrefName.StandardCardRoundness).toFloat()
        binding.appearanceStandardCardRoundness.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.StandardCardRoundness, value.toInt())
        }

        binding.appearanceContinueWatchingCardRoundness.isFocusable = true
        binding.appearanceContinueWatchingCardRoundness.value =
            PrefManager.getVal<Int>(PrefName.ContinueWatchingCardRoundness).toFloat()
        binding.appearanceContinueWatchingCardRoundness.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.ContinueWatchingCardRoundness, value.toInt())
        }

        binding.appearanceHideRedDot.isChecked =
            !PrefManager.getVal<Boolean>(PrefName.ShowNotificationRedDot)
        binding.appearanceHideRedDot.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ShowNotificationRedDot, !isChecked)
        }

        binding.appearanceBlurBanners.isChecked = PrefManager.getVal(PrefName.BlurBanners)
        binding.appearanceBlurBanners.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.BlurBanners, isChecked)
        }
        binding.appearanceBlurRadius.isFocusable = true
        binding.appearanceBlurRadius.value = PrefManager.getVal(PrefName.BlurRadius) as Float
        binding.appearanceBlurRadius.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.BlurRadius, value)
        }
        binding.appearanceBlurSampling.isFocusable = true
        binding.appearanceBlurSampling.value = PrefManager.getVal(PrefName.BlurSampling) as Float
        binding.appearanceBlurSampling.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.BlurSampling, value)
        }

        binding.appearanceUiScale.isFocusable = true
        binding.appearanceUiScale.value = PrefManager.getVal(PrefName.UIScale)
        binding.appearanceUiScale.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.UIScale, value)
        }

        binding.appearanceBannerBrightness.isFocusable = true
        binding.appearanceBannerBrightness.value = PrefManager.getVal(PrefName.BannerBrightness)
        binding.appearanceBannerBrightness.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.BannerBrightness, value)
        }

        binding.appearancePersistSideRail.isChecked = PrefManager.getVal(PrefName.SideRailPersist)
        binding.appearancePersistSideRail.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.SideRailPersist, isChecked)
        }

        binding.appearanceAutoSideRail.isChecked = PrefManager.getVal(PrefName.SideRailAutoOrientation)
        binding.appearanceAutoSideRail.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.SideRailAutoOrientation, isChecked)
            if (isChecked) {
                val orient = resources.configuration.orientation
                val persist = orient != Configuration.ORIENTATION_LANDSCAPE
                PrefManager.setVal(PrefName.SideRailPersist, persist)
                binding.appearancePersistSideRail.isChecked = persist
            }
            binding.appearancePersistSideRail.isEnabled = !isChecked
            binding.appearancePersistSideRail.alpha = if (isChecked) 0.4f else 1f
        }
        val autoOn = PrefManager.getVal<Boolean>(PrefName.SideRailAutoOrientation)
        binding.appearancePersistSideRail.isEnabled = !autoOn
        binding.appearancePersistSideRail.alpha = if (autoOn) 0.4f else 1f

        binding.appearanceGlassMaster.isChecked = PrefManager.getVal(PrefName.GlassEffectEnabled)
        binding.appearanceGlassMaster.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectEnabled, isChecked)
        }
        binding.appearanceGlassNavPills.isChecked = PrefManager.getVal(PrefName.GlassEffectNavPills)
        binding.appearanceGlassNavPills.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectNavPills, isChecked)
        }
        binding.appearanceGlassSideRail.isChecked = PrefManager.getVal(PrefName.GlassEffectSideRail)
        binding.appearanceGlassSideRail.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectSideRail, isChecked)
        }
        binding.appearanceGlassServerSheet.isChecked = PrefManager.getVal(PrefName.GlassEffectServerSheet)
        binding.appearanceGlassServerSheet.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectServerSheet, isChecked)
        }
        binding.appearanceGlassListEditor.isChecked = PrefManager.getVal(PrefName.GlassEffectListEditor)
        binding.appearanceGlassListEditor.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectListEditor, isChecked)
        }
        binding.appearanceGlassSourceSelector.isChecked = PrefManager.getVal(PrefName.GlassEffectSourceSelector)
        binding.appearanceGlassSourceSelector.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectSourceSelector, isChecked)
        }
        binding.appearanceGlassEpisodeDrawer.isChecked = PrefManager.getVal(PrefName.GlassEffectEpisodeDrawer)
        binding.appearanceGlassEpisodeDrawer.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectEpisodeDrawer, isChecked)
        }
        binding.appearanceGlassSubtitleSync.isChecked = PrefManager.getVal(PrefName.GlassEffectSubtitleSync)
        binding.appearanceGlassSubtitleSync.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectSubtitleSync, isChecked)
        }
        binding.appearanceGlassKeyboard.isChecked = PrefManager.getVal(PrefName.GlassEffectKeyboard)
        binding.appearanceGlassKeyboard.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectKeyboard, isChecked)
        }

        binding.appearanceGlassBlurRadius.isFocusable = true
        binding.appearanceGlassBlurRadius.value = PrefManager.getVal(PrefName.GlassEffectBlurRadius) as Float
        binding.appearanceGlassBlurRadius.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.GlassEffectBlurRadius, value)
        }

        binding.appearanceGlassTintOpacity.isFocusable = true
        binding.appearanceGlassTintOpacity.value = PrefManager.getVal(PrefName.GlassEffectTintOpacity) as Float
        binding.appearanceGlassTintOpacity.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.GlassEffectTintOpacity, value)
        }

        binding.appearanceGlassVibrancy.isFocusable = true
        binding.appearanceGlassVibrancy.value = PrefManager.getVal(PrefName.GlassEffectVibrancy) as Float
        binding.appearanceGlassVibrancy.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.GlassEffectVibrancy, value)
        }

        binding.appearanceGlassChromaticAberration.isFocusable = true
        binding.appearanceGlassChromaticAberration.value = PrefManager.getVal(PrefName.GlassEffectChromaticAberration) as Float
        binding.appearanceGlassChromaticAberration.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.GlassEffectChromaticAberration, value)
        }

        binding.appearanceGlassRefractionHeight.isFocusable = true
        binding.appearanceGlassRefractionHeight.value = PrefManager.getVal(PrefName.GlassEffectRefractionHeight) as Float
        binding.appearanceGlassRefractionHeight.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.GlassEffectRefractionHeight, value)
        }

        binding.appearanceGlassRefractionAmount.isFocusable = true
        binding.appearanceGlassRefractionAmount.value = PrefManager.getVal(PrefName.GlassEffectRefractionAmount) as Float
        binding.appearanceGlassRefractionAmount.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.GlassEffectRefractionAmount, value)
        }

        binding.appearanceGlassDepth.isChecked = PrefManager.getVal(PrefName.GlassEffectDepth)
        binding.appearanceGlassDepth.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectDepth, isChecked)
        }

        binding.appearanceGlassSurfaceTint.isFocusable = true
        binding.appearanceGlassSurfaceTint.setOnClickListener {
            showColorPicker(
                originalColor = PrefManager.getVal(PrefName.GlassEffectSurfaceTint),
                title = "Surface Tint Color"
            ) { color: Int ->
                PrefManager.setVal(PrefName.GlassEffectSurfaceTint, color)
            }
        }

        binding.appearanceGlassTextColor.isFocusable = true
        binding.appearanceGlassTextColor.setOnClickListener {
            showColorPicker(
                originalColor = PrefManager.getVal(PrefName.GlassEffectTextColor),
                title = "Glass Text Color"
            ) { color: Int ->
                PrefManager.setVal(PrefName.GlassEffectTextColor, color)
            }
        }

        binding.appearanceNavPillHeight.value = PrefManager.getVal<Int>(PrefName.NavPillHeight).toFloat()
        binding.appearanceNavPillHeight.addOnChangeListener { _, v, _ ->
            PrefManager.setVal(PrefName.NavPillHeight, v.toInt())
            updateNavPillPreview()
        }

        binding.appearanceNavPillWidth.value = PrefManager.getVal<Int>(PrefName.NavPillSpacing).toFloat()
        binding.appearanceNavPillWidth.addOnChangeListener { _, v, _ ->
            PrefManager.setVal(PrefName.NavPillSpacing, v.toInt())
            updateNavPillPreview()
        }

        binding.appearanceNavPillSpacing.value = PrefManager.getVal<Int>(PrefName.NavPillWidth).toFloat()
        binding.appearanceNavPillSpacing.addOnChangeListener { _, v, _ ->
            PrefManager.setVal(PrefName.NavPillWidth, v.toInt())
            updateNavPillPreview()
        }

        val iconSize = PrefManager.getVal<Int>(PrefName.NavPillIconSize).coerceIn(8, 28)
        PrefManager.setVal(PrefName.NavPillIconSize, iconSize)
        binding.appearanceNavPillIconSize.value = iconSize.toFloat()
        binding.appearanceNavPillIconSize.addOnChangeListener { _, v, _ ->
            PrefManager.setVal(PrefName.NavPillIconSize, v.toInt())
            updateNavPillPreview()
        }

        val cornerRadius = PrefManager.getVal<Int>(PrefName.NavPillCornerRadius).coerceIn(0, 48)
        PrefManager.setVal(PrefName.NavPillCornerRadius, cornerRadius)
        binding.appearanceNavPillCornerRadius.value = cornerRadius.toFloat()
        binding.appearanceNavPillCornerRadius.addOnChangeListener { _, v, _ ->
            PrefManager.setVal(PrefName.NavPillCornerRadius, v.toInt())
            updateNavPillPreview()
        }

        binding.appearanceNavPillIconColor.isFocusable = true
        binding.appearanceNavPillIconColor.setOnClickListener {
            showColorPicker(
                originalColor = PrefManager.getVal(PrefName.NavPillIconColor),
                title = "NavPill Icon Color"
            ) { color: Int ->
                PrefManager.setVal(PrefName.NavPillIconColor, color)
                updateNavPillPreview()
            }
        }

        binding.appearanceThemeMode.isFocusable = true
        binding.appearanceThemeMode.setOnClickListener {
            customAlertDialog().apply {
                setTitle("Theme Mode")
                val themeModes = arrayOf("Light", "Dark")
                singleChoiceItems(themeModes, PrefManager.getVal<Int>(PrefName.DarkMode)) { index ->
                    PrefManager.setVal(PrefName.DarkMode, index)
                    AppCompatDelegate.setDefaultNightMode(
                        if (index == 1) AppCompatDelegate.MODE_NIGHT_YES
                        else AppCompatDelegate.MODE_NIGHT_NO
                    )
                    recreate()
                }
                show()
            }
        }

        binding.appearanceAccentColor.isFocusable = true
        binding.appearanceAccentColor.setOnClickListener {
            val accentColors = arrayOf(
                0 to "Sanin", 1 to "Ocean", 2 to "Blood", 3 to "Lime",
                4 to "Sun", 5 to "Kurama", 6 to "Saikou", 7 to "Indigo",
                8 to "Monochrome"
            )
            customAlertDialog().apply {
                setTitle("Accent Color")
                val labels = accentColors.map { it.second }.toTypedArray()
                singleChoiceItems(labels, PrefManager.getVal<Int>(PrefName.AccentColor)) { index ->
                    PrefManager.setVal(PrefName.AccentColor, accentColors[index].first)
                    restartApp()
                }
                show()
            }
        }

        binding.appearanceOledMode.isFocusable = true
        binding.appearanceOledMode.setOnClickListener {
            customAlertDialog().apply {
                setTitle("OLED Background Mode")
                val labels = arrayOf(
                    "Off\nNormal theme background",
                    "Pure AMOLED\nPure black background",
                    "Glow Spots\nBlack + radial glow orbs",
                    "Gradient\nBlack + primary color gradient",
                    "Vignette\nColored vignette from edges"
                )
                singleChoiceItems(labels, PrefManager.getVal<Int>(PrefName.OledMode)) { index ->
                    PrefManager.setVal(PrefName.OledMode, index)
                    restartApp()
                }
                show()
            }
        }

        binding.appearanceHomeBannerMode.isFocusable = true
        binding.appearanceHomeBannerMode.setOnClickListener {
            val homeBannerModes = arrayOf(
                getString(R.string.home_banner_carousel),
                getString(R.string.home_banner_profile),
                getString(R.string.home_banner_navigating),
                getString(R.string.home_banner_off)
            )
            customAlertDialog().apply {
                setTitle(getString(R.string.home_banner_mode))
                singleChoiceItems(
                    homeBannerModes,
                    PrefManager.getVal<Int>(PrefName.HomeBannerMode)
                ) { index ->
                    PrefManager.setVal(PrefName.HomeBannerMode, index)
                }
                show()
            }
        }

        binding.appearanceHeroCardImage.isChecked = PrefManager.getVal(PrefName.HeroCardImage)
        binding.appearanceHeroCardImage.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.HeroCardImage, isChecked)
        }

        binding.appearanceShowContinueWatching.isChecked = PrefManager.getVal(PrefName.ShowContinueWatching)
        binding.appearanceShowContinueWatching.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ShowContinueWatching, isChecked)
            restartApp()
        }

        binding.appearanceShowPlanned.isChecked = PrefManager.getVal(PrefName.ShowPlanned)
        binding.appearanceShowPlanned.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ShowPlanned, isChecked)
            restartApp()
        }

        binding.appearanceShowRecommendations.isChecked = PrefManager.getVal(PrefName.ShowRecommendations)
        binding.appearanceShowRecommendations.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ShowRecommendations, isChecked)
            restartApp()
        }

        binding.appearanceShowTrending.isChecked = PrefManager.getVal(PrefName.ShowTrending)
        binding.appearanceShowTrending.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ShowTrending, isChecked)
            restartApp()
        }

        binding.appearanceShowPopular.isChecked = PrefManager.getVal(PrefName.ShowPopular)
        binding.appearanceShowPopular.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ShowPopular, isChecked)
            restartApp()
        }

        binding.appearanceShowRecent.isChecked = PrefManager.getVal(PrefName.ShowRecent)
        binding.appearanceShowRecent.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ShowRecent, isChecked)
            restartApp()
        }

        binding.appearanceFocusEffect.isFocusable = true
        binding.appearanceFocusEffect.setOnClickListener {
            customAlertDialog().apply {
                setTitle("Focus Effect")
                val labels = arrayOf("Glow", "Breathing", "Pulse", "Shaking", "None")
                singleChoiceItems(labels, PrefManager.getVal<Int>(PrefName.FocusEffect)) { index ->
                    PrefManager.setVal(PrefName.FocusEffect, index)
                }
                show()
            }
        }

        updateNavPillPreview()
        setupCollapsibleSections()
    }

    private fun updateNavPillPreview() {
        NavPillCustomizer.applyToPillPreview(binding.appearanceNavPillPreview)
    }

    private fun showColorPicker(
        originalColor: Int,
        title: String,
        callback: (Int) -> Unit,
    ) {
        val colors = intArrayOf(
            Color.WHITE, Color.BLACK, Color.RED, Color.parseColor("#FF9800"),
            Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE,
            Color.parseColor("#9C27B0"), Color.parseColor("#E91E63"),
            Color.GRAY, Color.parseColor("#607D8B"), Color.parseColor("#795548"),
            Color.parseColor("#4CAF50"), Color.parseColor("#03A9F4"),
            Color.parseColor("#FF5722"), originalColor
        )
        val names = arrayOf(
            "White", "Black", "Red", "Orange",
            "Yellow", "Green", "Cyan", "Blue",
            "Purple", "Pink",
            "Grey", "Blue Grey", "Brown",
            "Green 500", "Light Blue",
            "Deep Orange", "Current"
        )

        val cols = 4
        val rows = (colors.size + cols - 1) / cols
        val dp = resources.displayMetrics.density
        val chipSize = (48 * dp).toInt()
        val margin = (4 * dp).toInt()

        val grid = GridLayout(this).apply {
            columnCount = cols
            rowCount = rows
            setPadding(margin, margin, margin, margin)
        }

        for (i in colors.indices) {
            val chip = ImageButton(this).apply {
                val gd = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colors[i])
                    setStroke(if (colors[i] == Color.BLACK) 2 else 0,
                        if (colors[i] == Color.BLACK) Color.GRAY else Color.TRANSPARENT)
                    setSize(chipSize, chipSize)
                }
                background = gd
                layoutParams = GridLayout.LayoutParams().apply {
                    width = chipSize
                    height = chipSize
                    setMargins(margin, margin, margin, margin)
                }
                contentDescription = names[i]
                isFocusable = true
            }
            grid.addView(chip)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(grid)
            .setNegativeButton("Cancel", null)
            .show()
            .also { dialog ->
                for (i in 0 until grid.childCount) {
                    grid.getChildAt(i).setOnClickListener {
                        callback(colors[i])
                        dialog.dismiss()
                    }
                }
            }
    }

    private fun setupCollapsibleSections() {
        val sections = listOf(
            binding.themeSectionHeader to binding.themeSectionBody,
            binding.homeSectionHeader to binding.homeSectionBody,
            binding.displaySectionHeader to binding.displaySectionBody,
            binding.blurSectionHeader to binding.blurSectionBody,
            binding.bannerSectionHeader to binding.bannerSectionBody,
            binding.sideRailSectionHeader to binding.sideRailSectionBody,
            binding.glassSectionHeader to binding.glassSectionBody,
        )
        for ((header, body) in sections) {
            header.isFocusable = true
            header.setOnClickListener {
                body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
            FocusEffectUtil.applyFocusListener(header)
        }
    }
}
