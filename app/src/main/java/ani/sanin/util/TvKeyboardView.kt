package ani.sanin.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import ani.sanin.R
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName

class TvKeyboardView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val compact: Boolean = false
) : FrameLayout(context, attrs, defStyleAttr) {

    private val primaryColor = FocusEffectUtil.getPrimaryColor(context)
    private val glassKeyRes = if (compact) R.drawable.tv_key_glass_compact else R.drawable.tv_key_glass
    private val glassDangerRes =
        if (compact) R.drawable.tv_key_glass_danger_compact else R.drawable.tv_key_glass_danger
    private val keyCornerRadius =
        (if (compact) 7f else 10f) * resources.displayMetrics.density
    private val focusScale = if (compact) 1.08f else 1.12f
    private val focusElevation =
        (if (compact) 8f else 14f) * resources.displayMetrics.density

    private fun tvkLog(message: String) {
        Logger.log(Log.INFO, message, "TvKeyboard")
    }

    var target: EditText? = null
        set(value) {
            if (field != value) {
                tvkLog("target ${field?.let { it.id }} -> ${value?.let { it.id }} (compact=$compact, visible=$isVisible)")
                field = value
                if (value != null && !compact && !isVisible) show()
                if (compact && value != null) syncFromTarget()
            }
        }

    private var isSymbolsMode = false
    private var isCapsLock = false
    private var isShifted = false

    private var autoShowSuppressedUntil = 0L

    internal fun isAutoShowSuppressed(): Boolean =
        SystemClock.uptimeMillis() < autoShowSuppressedUntil

    private lateinit var modeToggle: TextView
    private lateinit var capsLock: TextView
    private lateinit var previewEditText: EditText
    private var firstKey: TextView? = null
    private val letterKeys = mutableListOf<TextView>()
    private val allKeys = mutableListOf<TextView>()

    private val letters = listOf(
        "q","w","e","r","t","y","u","i","o","p",
        "a","s","d","f","g","h","j","k","l",
        "z","x","c","v","b","n","m",",","."
    )

    private val symbols = listOf(
        "1","2","3","4","5","6","7","8","9","0",
        "-","/",":",";","(",")","$","&","@",
        "+","=","[","]","{","}","#","%","*"
    )

    init {
        inflate(context, if (compact) R.layout.tv_keyboard_compact else R.layout.tv_keyboard_view, this)
        setupKeys()
        applyGlassIfEnabled()
    }

    private fun applyGlassIfEnabled() {
        if (!GlassEffectManager.isComponentEnabled(GlassComponent.Keyboard)) return
        val savedTag = tag
        GlassEffectManager.applyGlass(
            this,
            GlassComponent.Keyboard,
            if (compact) 18f else 20f,
            GlassEffectManager.getTintColor()
        )
        tag = savedTag
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupKeys() {
        val letterIds = listOf(
            R.id.keyQ, R.id.keyW, R.id.keyE, R.id.keyR, R.id.keyT,
            R.id.keyY, R.id.keyU, R.id.keyI, R.id.keyO, R.id.keyP,
            R.id.keyA, R.id.keyS, R.id.keyD, R.id.keyF, R.id.keyG,
            R.id.keyH, R.id.keyJ, R.id.keyK, R.id.keyL,
            R.id.keyZ, R.id.keyX, R.id.keyC, R.id.keyV, R.id.keyB,
            R.id.keyN, R.id.keyM, R.id.keyComma, R.id.keyPeriod
        )

        modeToggle = findViewById(R.id.keyModeToggle)
        capsLock = findViewById(R.id.keyCapsLock)
        capsLock.alpha = 0.5f
        if (compact) {
            previewEditText = findViewById(R.id.keyPreview)
            previewEditText.showSoftInputOnFocus = false
        }

        for (i in letterIds.indices) {
            val v = findViewById<TextView>(letterIds[i])
            if (firstKey == null) firstKey = v
            v.text = letters.getOrElse(i) { "" }
            letterKeys.add(v)
            allKeys.add(v)
        }

        val specialIds = listOf(
            R.id.keyBackspace, R.id.keyEnter, R.id.keySpace,
            R.id.keyHide, R.id.keyModeToggle, R.id.keyCapsLock
        )

        for (id in specialIds) {
            allKeys.add(findViewById(id))
        }

        for (v in allKeys) {
            v.setOnClickListener { onKeyClick(v) }
            v.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) applyKeyFocus(view)
                else removeKeyFocus(view)
            }
            v.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        0,
                        0,
                        view.width.coerceAtLeast(1),
                        view.height.coerceAtLeast(1),
                        keyCornerRadius
                    )
                }
            }
        }

        isShifted = true
        updateLetterCase()
    }

    private fun applyKeyFocus(v: View) {
        val glow = resolveGlowColor()
        val gap = (if (compact) 2f else 3f) * v.resources.displayMetrics.density
        val strokePx = (if (compact) 1.5f else 2f) * v.resources.displayMetrics.density

        val glowLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor((glow and 0x00FFFFFF) or (0x59 shl 24))
            cornerRadius = keyCornerRadius + gap
        }
        val bodyLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            colors = intArrayOf(
                Color.argb(0x80, 255, 255, 255),
                Color.argb(0x33, 255, 255, 255)
            )
            setStroke(strokePx.toInt(), (glow and 0x00FFFFFF) or (0xE6 shl 24))
            cornerRadius = keyCornerRadius
        }
        val focused = LayerDrawable(arrayOf(glowLayer, bodyLayer))
        val inset = gap.toInt().coerceAtLeast(1)
        focused.setLayerInset(1, inset, inset, inset, inset)
        v.background = focused

        if (keyAnimationsEnabled()) {
            v.animate()
                .scaleX(focusScale)
                .scaleY(focusScale)
                .translationZ(focusElevation)
                .setDuration(150)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            v.scaleX = focusScale
            v.scaleY = focusScale
            v.translationZ = focusElevation
        }
    }

    private fun removeKeyFocus(v: View) {
        v.setBackgroundResource(if (v.id == R.id.keyHide) glassDangerRes else glassKeyRes)
        if (keyAnimationsEnabled()) {
            v.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationZ(0f)
                .setDuration(150)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            v.scaleX = 1f
            v.scaleY = 1f
            v.translationZ = 0f
        }
    }

    private fun keyAnimationsEnabled(): Boolean =
        PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) &&
            PrefManager.getVal<Boolean>(PrefName.KeyboardKeyAnimations)

    private fun resolveGlowColor(): Int {
        val luminance =
            (Color.red(primaryColor) + Color.green(primaryColor) + Color.blue(primaryColor)) / 3
        return if (luminance < 40) 0xFF7FCCFF.toInt() else primaryColor
    }

    private fun onKeyClick(view: TextView) {
        val src = if (compact) previewEditText else (target ?: return)
        if (!compact && target == null) return
        val text = src.text ?: return
        val start = src.selectionStart.coerceAtLeast(0)
        val end = src.selectionEnd.coerceAtLeast(0)
        val minPos = minOf(start, end)
        val maxPos = maxOf(start, end)

        when (view.id) {
            R.id.keyBackspace -> {
                if (minPos != maxPos) {
                    text.delete(minPos, maxPos)
                } else if (minPos > 0) {
                    text.delete(minPos - 1, minPos)
                }
            }
            R.id.keyEnter -> {
                target?.onEditorAction(EditorInfo.IME_ACTION_DONE)
            }
            R.id.keySpace -> {
                text.insert(minPos, " ")
                src.setSelection(minPos + 1)
            }
            R.id.keyHide -> {
                tvkLog("keyHide pressed, hiding keyboard (compact=$compact)")
                hide()
                target?.let { t ->
                    t.clearFocus()
                    t.post {
                        var p: View? = t
                        while (p != null) {
                            if (p.isFocusable && p !is EditText) {
                                p.requestFocus()
                                break
                            }
                            p = p.parent as? View
                        }
                    }
                }
            }
            R.id.keyModeToggle -> toggleMode()
            R.id.keyCapsLock -> toggleCapsLock()
            else -> {
                var char = view.text?.toString() ?: return
                if (!isSymbolsMode && isCapsLock) {
                    char = char.uppercase()
                } else if (!isSymbolsMode && isShifted) {
                    char = char.uppercase()
                    isShifted = false
                    updateLetterCase()
                }
                text.insert(minPos, char)
                src.setSelection(minPos + char.length)
            }
        }
        if (compact) syncToTarget()
    }

    private fun syncToTarget() {
        val t = target ?: return
        val prevText = previewEditText.text?.toString() ?: ""
        if (t.text?.toString() != prevText) {
            t.setText(prevText)
        }
        try { t.setSelection(previewEditText.selectionStart.coerceAtLeast(0)) } catch (_: Exception) {}
    }

    private fun syncFromTarget() {
        val t = target ?: return
        val targetText = t.text?.toString() ?: ""
        if (previewEditText.text?.toString() != targetText) {
            previewEditText.setText(targetText)
        }
        try { previewEditText.setSelection(t.selectionStart.coerceAtLeast(0)) } catch (_: Exception) {}
    }

    private fun toggleMode() {
        isSymbolsMode = !isSymbolsMode
        val chars = if (isSymbolsMode) symbols else letters
        for (i in letterKeys.indices) {
            letterKeys[i].text = chars.getOrElse(i) { "" }
        }
        modeToggle.text = if (isSymbolsMode) "ABC" else "\u003F123"
        updateLetterCase()
    }

    private fun toggleCapsLock() {
        isCapsLock = !isCapsLock
        capsLock.text = if (isCapsLock) "CAPS" else "caps"
        capsLock.alpha = if (isCapsLock) 1.0f else 0.5f
        updateLetterCase()
    }

    private fun updateLetterCase() {
        if (isSymbolsMode) return
        val upper = isCapsLock || isShifted
        for (i in letterKeys.indices) {
            val base = letters.getOrElse(i) { "" }
            letterKeys[i].text = if (upper) base.uppercase() else base
        }
    }

    private var keyboardHeight = 0

    fun show() {
        isShifted = true
        updateLetterCase()
        if (compact) {
            tvkLog("show() compact visible=$isVisible target=${target?.let { it.id }}")
            syncFromTarget()
            visibility = VISIBLE
            requestFocus()
            post {
                tvkLog("compact keyboard post: requesting focus firstKey=${firstKey?.id} currentFocus=${findFocus()?.let { it.javaClass.simpleName + "#" + it.id }}")
                firstKey?.requestFocus()
            }
            return
        }
        animate().cancel()
        if (isVisible) return
        tvkLog("show() full visible=$isVisible target=${target?.let { it.id }}")
        visibility = VISIBLE
        requestLayout()
        post {
            keyboardHeight = height
            translationY = height.toFloat()
            animate().translationY(0f).setDuration(200).start()
        }
    }

    fun hide() {
        if (compact) {
            tvkLog("hide() compact visible=$isVisible target=${target?.let { it.id }}")
            syncToTarget()
            autoShowSuppressedUntil = SystemClock.uptimeMillis() + 200
            target?.clearFocus()
            clearFocus()
            visibility = GONE
            return
        }
        animate().cancel()
        if (!isVisible) return
        tvkLog("hide() full visible=$isVisible target=${target?.let { it.id }}")
        val h = if (keyboardHeight > 0) keyboardHeight else height
        animate().translationY(h.toFloat()).setDuration(200).withEndAction {
            visibility = GONE
            translationY = 0f
        }.start()
    }

    fun isKeyboardVisible(): Boolean = isVisible

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (compact && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (isVisible) {
                tvkLog("dispatchKeyEvent BACK consumed, hiding compact keyboard")
                hide()
                target?.clearFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
