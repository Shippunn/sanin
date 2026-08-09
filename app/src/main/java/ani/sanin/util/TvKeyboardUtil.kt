package ani.sanin.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.app.UiModeManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName

object TvKeyboardUtil {

    private val TAG_KEYBOARD = "tv_custom_keyboard"
    private val TAG_KEYBOARD_COMPACT = "tv_custom_keyboard_compact"

    private fun resolveActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun keyboardMode(): Int = PrefManager.getVal(PrefName.KeyboardMode)

    private fun tvkLog(message: String) {
        Logger.log(Log.INFO, message, "TvKeyboard")
    }

    private fun describe(view: View): String =
        view.javaClass.simpleName + "#" + view.id + "(" + view.isAttachedToWindow + ")"

    /** For mode 0 (System keyboard): show system keyboard on focus */
    fun setupSystemKeyboard(view: View) {
        tvkLog("setupSystemKeyboard(mode0) ${describe(view)}")
        retainWindowFocus(view)
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                showKeyboardDelayed(v)
                applyFocusBorder(v)
            } else {
                removeFocusBorder(v)
            }
        }
        if (view.isFocused) {
            showKeyboardDelayed(view)
            applyFocusBorder(view)
        }
    }

    /** For mode 1 (Toggle button): attach toggle button, show/hide custom keyboard on click */
    fun setupEditTextWithToggle(editText: EditText, toggleButton: View) {
        tvkLog("setupEditTextWithToggle(mode1) ${describe(editText)}")
        retainWindowFocus(editText)
        editText.showSoftInputOnFocus = false
        attachKeyboardToWindow(editText)
        val activity = resolveActivity(editText.context) ?: return
        toggleButton.visibility = View.VISIBLE
        toggleButton.setOnClickListener {
            val keyboard = getOrCreateKeyboard(activity)
            tvkLog("toggle clicked, keyboardVisible=${keyboard.isKeyboardVisible()}")
            if (keyboard.isKeyboardVisible()) {
                keyboard.hide()
                editText.clearFocus()
            } else {
                keyboard.target = editText
                keyboard.show()
            }
        }
        editText.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                applyFocusBorder(v)
            } else {
                removeFocusBorder(v)
            }
        }
        if (editText.isFocused) applyFocusBorder(editText)
    }

    /** For mode 2 (Always visible): show compact keyboard at bottom-left, set target on focus */
    fun setupEditTextForAlwaysVisible(editText: EditText) {
        tvkLog("setupEditTextForAlwaysVisible(mode2) ${describe(editText)}")
        retainWindowFocus(editText)
        editText.showSoftInputOnFocus = false
        ensureCompactKeyboardVisible(editText)
        editText.setOnFocusChangeListener { v, hasFocus ->
            tvkLog("mode2 focus change ${describe(v)} hasFocus=$hasFocus")
            if (hasFocus) {
                val keyboard = getCompactKeyboard(editText)
                if (keyboard?.isAutoShowSuppressed() == true) {
                    tvkLog("mode2 focus change suppressed (keyboard was just hidden)")
                } else {
                    keyboard?.apply {
                        target = editText
                        show()
                    }
                }
                applyFocusBorder(v)
            } else {
                removeFocusBorder(v)
            }
        }
        if (editText.isFocused) {
            getCompactKeyboard(editText)?.apply {
                target = editText
                show()
            }
            applyFocusBorder(editText)
        }
    }

    /** Unified setup that delegates based on keyboard mode */
    fun setupTvInput(view: View) {
        tvkLog("setupTvInput mode=${keyboardMode()} ${describe(view)}")
        when (keyboardMode()) {
            0 -> setupSystemKeyboard(view)
            1 -> {
                // Default toggle behavior when no explicit toggle button provided
                retainWindowFocus(view)
                if (view is EditText) {
                    view.showSoftInputOnFocus = false
                    attachKeyboardToWindow(view)
                    view.setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus && v is EditText) showCustomKeyboard(v)
                        else if (!hasFocus) hideCustomKeyboard(v)
                    }
                    if (view.isFocused && view is EditText) showCustomKeyboard(view)
                }
            }
            2 -> {
                if (view is EditText) setupEditTextForAlwaysVisible(view)
            }
        }
    }

    fun ensureKeyboardOnFocus(editText: EditText) {
        when (keyboardMode()) {
            0 -> {
                retainWindowFocus(editText)
                editText.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) showKeyboardDelayed(v)
                }
                if (editText.isFocused) showKeyboardDelayed(editText)
            }
            1 -> {
                editText.showSoftInputOnFocus = false
                attachKeyboardToWindow(editText)
                editText.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) showCustomKeyboard(v)
                    else hideCustomKeyboard(v)
                }
                if (editText.isFocused) showCustomKeyboard(editText)
            }
            2 -> setupEditTextForAlwaysVisible(editText)
        }
    }

    fun showKeyboard(view: View) {
        when (keyboardMode()) {
            0 -> {
                view.requestFocus()
                retainWindowFocus(view)
                val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
                imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
            }
            1 -> {
                view.requestFocus()
                if (view is EditText) showCustomKeyboard(view)
            }
            2 -> {
                view.requestFocus()
                if (view is EditText) showCompactKeyboard(editText = view)
            }
        }
    }

    fun showKeyboardDelayed(view: View, delayMs: Long = 150) {
        when (keyboardMode()) {
            0 -> {
                retainWindowFocus(view)
                view.postDelayed({
                    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return@postDelayed
                    imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
                }, delayMs)
            }
            1 -> {
                view.postDelayed({
                    if (view is EditText) showCustomKeyboard(view)
                }, delayMs)
            }
            2 -> {
                view.postDelayed({
                    if (view is EditText) showCompactKeyboard(editText = view)
                }, delayMs)
            }
        }
    }

    fun hideKeyboard(view: View) {
        tvkLog("hideKeyboard ${describe(view)}")
        view.clearFocus()
        if (keyboardMode() != 0) {
            hideCustomKeyboard(view)
        }
    }

    fun retainWindowFocus(window: Window) {
        if (Build.VERSION.SDK_INT >= 24) {
            window.addFlags(WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE)
        }
    }

    private fun retainWindowFocus(view: View) {
        if (Build.VERSION.SDK_INT < 24) return
        resolveActivity(view.context)?.window?.addFlags(WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE)
    }

    fun TextView.setupTvKeyboard() {
        when (keyboardMode()) {
            0 -> {
                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) showKeyboardDelayed(v)
                }
            }
            1 -> {
                if (this is EditText) showSoftInputOnFocus = false
                attachKeyboardToWindow(this)
                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus && v is EditText) showCustomKeyboard(v)
                    else if (!hasFocus) hideCustomKeyboard(v)
                }
            }
            2 -> {
                if (this is EditText) setupEditTextForAlwaysVisible(this)
            }
        }
    }

    fun isTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun applyTvFocusBorder(v: View) {
        if (!isTv(v.context)) return
        val primaryColor = FocusEffectUtil.getPrimaryColor(v.context)
        val borderWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 3f, v.resources.displayMetrics
        ).toInt()
        val cornerRadiusPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8f, v.resources.displayMetrics
        ).toInt()

        val borderDrawable = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(Color.TRANSPARENT)
            setStroke(borderWidthPx, primaryColor)
            setCornerRadius(cornerRadiusPx.toFloat())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            v.foreground = borderDrawable
        }
    }

    fun removeTvFocusBorder(v: View) {
        if (!isTv(v.context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            v.foreground = null
        }
    }

    private fun applyFocusBorder(v: View) = applyTvFocusBorder(v)
    private fun removeFocusBorder(v: View) = removeTvFocusBorder(v)

    private fun attachKeyboardToWindow(view: View) {
        val activity = view.context as? Activity ?: return
        getOrCreateKeyboard(activity)
    }

    private fun getOrCreateKeyboard(activity: Activity): TvKeyboardView {
        val decorView = activity.window.decorView as? ViewGroup ?: error("Cannot access decor view")
        var keyboard = decorView.findViewWithTag<TvKeyboardView>(TAG_KEYBOARD)
        if (keyboard == null) {
            keyboard = TvKeyboardView(activity).apply {
                this.tag = TAG_KEYBOARD
                visibility = View.GONE
            }
            decorView.addView(
                keyboard,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
            )
        }
        return keyboard
    }

    private fun getCompactKeyboard(view: View): TvKeyboardView? {
        if (!view.isAttachedToWindow) return null
        val decorView = view.rootView as? ViewGroup ?: return null
        var keyboard = decorView.findViewWithTag<TvKeyboardView>(TAG_KEYBOARD_COMPACT)
        if (keyboard == null) {
            keyboard = TvKeyboardView(view.context, compact = true).apply {
                this.tag = TAG_KEYBOARD_COMPACT
                visibility = View.GONE
            }
            tvkLog("getCompactKeyboard: created new compact keyboard (root=${decorView.javaClass.simpleName})")
            val landscape = view.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START or Gravity.BOTTOM
                bottomMargin = (16 * view.resources.displayMetrics.density).toInt()
                leftMargin = (12 * view.resources.displayMetrics.density).toInt()
                rightMargin = (12 * view.resources.displayMetrics.density).toInt()
            }
            if (landscape) {
                keyboard.post {
                    val content = keyboard.getChildAt(0) as? LinearLayout ?: return@post
                    for (i in 0 until content.childCount) {
                        val child = content.getChildAt(i)
                        val lp = child.layoutParams
                        if (lp != null) {
                            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                            child.layoutParams = lp
                        }
                    }
                }
            }
            decorView.addView(keyboard, params)
        }
        return keyboard
    }

    fun ensureCompactKeyboardVisible(view: View) {
        getCompactKeyboard(view)
    }

    fun isCompactKeyboardVisible(view: View): Boolean {
        val visible = getCompactKeyboard(view)?.isKeyboardVisible() == true
        tvkLog("isCompactKeyboardVisible ${describe(view)} -> $visible")
        return visible
    }

    fun hideCompactKeyboard(view: View) {
        tvkLog("hideCompactKeyboard ${describe(view)}")
        getCompactKeyboard(view)?.hide()
    }

    private fun showCustomKeyboard(view: View) {
        val editText = view as? EditText ?: return
        val activity = editText.context as? Activity ?: return
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(editText.windowToken, 0)
        tvkLog("showCustomKeyboard ${describe(editText)}")
        getOrCreateKeyboard(activity).apply {
            target = editText
            show()
        }
    }

    private fun showCompactKeyboard(editText: EditText) {
        val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(editText.windowToken, 0)
        tvkLog("showCompactKeyboard ${describe(editText)}")
        getCompactKeyboard(editText)?.apply {
            target = editText
            show()
        }
    }

    private fun hideCustomKeyboard(view: View) {
        val activity = view.context as? Activity ?: return
        tvkLog("hideCustomKeyboard ${describe(view)}")
        getOrCreateKeyboard(activity).hide()
    }
}
