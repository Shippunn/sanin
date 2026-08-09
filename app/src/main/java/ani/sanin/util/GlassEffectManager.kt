package ani.sanin.util

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName

enum class GlassComponent {
    NavPills, SideRail, ServerSheet, ListEditor, SourceSelector, EpisodeDrawer, SubtitleSync, Keyboard
}

object GlassEffectManager {

    private val activeDrawables = mutableMapOf<View, GlassEffectDrawable>()

    fun isEnabled(): Boolean = PrefManager.getVal(PrefName.GlassEffectEnabled)

    fun isComponentEnabled(component: GlassComponent): Boolean {
        if (!isEnabled()) return false
        return when (component) {
            GlassComponent.NavPills -> PrefManager.getVal(PrefName.GlassEffectNavPills)
            GlassComponent.SideRail -> PrefManager.getVal(PrefName.GlassEffectSideRail)
            GlassComponent.ServerSheet -> PrefManager.getVal(PrefName.GlassEffectServerSheet)
            GlassComponent.ListEditor -> PrefManager.getVal(PrefName.GlassEffectListEditor)
            GlassComponent.SourceSelector -> PrefManager.getVal(PrefName.GlassEffectSourceSelector)
            GlassComponent.EpisodeDrawer -> PrefManager.getVal(PrefName.GlassEffectEpisodeDrawer)
            GlassComponent.SubtitleSync -> PrefManager.getVal(PrefName.GlassEffectSubtitleSync)
            GlassComponent.Keyboard -> PrefManager.getVal(PrefName.GlassEffectKeyboard)
        }
    }

    fun getBlurRadius(): Float = PrefManager.getVal(PrefName.GlassEffectBlurRadius)
    fun getTintAlpha(): Float = PrefManager.getVal(PrefName.GlassEffectTintOpacity)
    fun getVibrancy(): Float = PrefManager.getVal(PrefName.GlassEffectVibrancy)
    fun getChromaticAberration(): Float = PrefManager.getVal(PrefName.GlassEffectChromaticAberration)
    fun getRefractionHeight(): Float = PrefManager.getVal(PrefName.GlassEffectRefractionHeight)
    fun getRefractionAmount(): Float = PrefManager.getVal(PrefName.GlassEffectRefractionAmount)
    fun isDepthEnabled(): Boolean = PrefManager.getVal(PrefName.GlassEffectDepth)
    fun getSurfaceTintColor(): Int = PrefManager.getVal(PrefName.GlassEffectSurfaceTint)
    fun getGlassTextColor(): Int = PrefManager.getVal(PrefName.GlassEffectTextColor)

    fun getAverageBrightness(component: GlassComponent): Float {
        for ((view, drawable) in activeDrawables) {
            if (view.tag == component) return drawable.averageBrightness
        }
        return 0.5f
    }

    fun getTintColor(): Int {
        val alpha = (getTintAlpha() * 255).toInt().coerceIn(0, 255)
        val base = getSurfaceTintColor()
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }

    fun applyParams(drawable: GlassEffectDrawable) {
        drawable.setVibrancy(getVibrancy())
        drawable.setChromaticAberration(getChromaticAberration())
        drawable.setRefractionHeight(getRefractionHeight())
        drawable.setRefractionAmount(getRefractionAmount())
        drawable.setDepthEnabled(isDepthEnabled())
    }

    fun refreshAll() {
        for ((view, drawable) in activeDrawables) {
            drawable.setTintColor(getTintColor())
            applyParams(drawable)
            drawable.invalidateCache()
            drawable.invalidateSelf()
        }
    }

    fun applyGlass(
        view: View,
        component: GlassComponent,
        cornerRadiusDp: Float = 16f,
        tintColor: Int = 0x66000000
    ): GlassEffectDrawable? {
        if (!isComponentEnabled(component)) {
            removeGlass(view)
            return null
        }
        removeGlass(view)
        view.tag = component
        val drawable = GlassEffectDrawable.applyToView(
            view = view,
            cornerRadiusDp = cornerRadiusDp,
            blurRadius = getBlurRadius(),
            tintColor = tintColor
        )
        applyParams(drawable)
        activeDrawables[view] = drawable
        view.post { wireScrollInvalidator(view, drawable) }
        return drawable
    }

    private fun wireScrollInvalidator(view: View, drawable: GlassEffectDrawable) {
        val root = view.rootView
        if (root is ViewGroup) {
            findScrollableDescendant(root)?.let { drawable.invalidateOnScroll(it) }
        }
    }

    private fun findScrollableDescendant(group: ViewGroup): View? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is RecyclerView || child is NestedScrollView ||
                child is android.widget.ScrollView || child is android.widget.ListView
            ) return child
            if (child is ViewGroup) {
                findScrollableDescendant(child)?.let { return it }
            }
        }
        return null
    }

    fun removeGlass(view: View) {
        activeDrawables.remove(view)?.destroy()
    }

    fun removeAllGlass() {
        activeDrawables.values.forEach { it.destroy() }
        activeDrawables.clear()
    }

    fun applyGlassToSheet(
        rootView: View,
        component: GlassComponent,
        cornerRadiusDp: Float = 16f
    ): GlassEffectDrawable? {
        if (!isComponentEnabled(component)) return null
        rootView.setBackgroundColor(Color.TRANSPARENT)
        (rootView as? ViewGroup)?.let { group ->
            for (i in 0 until group.childCount) {
                group.getChildAt(i).background = null
            }
        }
        val tint = getTintColor()
        val drawable = applyGlass(rootView, component, cornerRadiusDp, tint)
        if (drawable != null) {
            val activity = rootView.context as? Activity
            if (activity != null) {
                drawable.setCaptureRootView(activity.window.decorView)
            }
        }
        return drawable
    }
}

@Composable
fun rememberGlassBackground(
    component: GlassComponent,
    cornerRadiusDp: Float = 16f,
    enabled: Boolean = true
) {
    val view = LocalView.current
    val isEnabled = remember(component, enabled) {
        enabled && GlassEffectManager.isComponentEnabled(component)
    }

    DisposableEffect(isEnabled, view) {
        if (isEnabled) {
            view.post {
                val tint = GlassEffectManager.getTintColor()
                GlassEffectManager.applyGlass(view, component, cornerRadiusDp, tint)
            }
        } else {
            view.post {
                GlassEffectManager.removeGlass(view)
            }
        }
        onDispose {
            view.post { GlassEffectManager.removeGlass(view) }
        }
    }
}
