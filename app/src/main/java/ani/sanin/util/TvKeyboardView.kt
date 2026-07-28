package ani.sanin.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
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
    private val surfaceVariant: Int
    private val surfaceVariantDim: Int

    var target: EditText? = null
        set(value) {
            if (field != value) {
                field = value
                if (value != null && !compact && !isVisible) show()
                if (compact && value != null) syncFromTarget()
            }
        }

    private var isSymbolsMode = false
    private var isCapsLock = false

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
        val ta = context.theme.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorSurfaceVariant))
        surfaceVariant = ta.getColor(0, 0x1AFFFFFF.toInt())
        ta.recycle()
        surfaceVariantDim = (surfaceVariant and 0x00FFFFFF) or (0x4D shl 24)
        setupKeys()
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

        for (id in letterIds) {
            val v = findViewById<TextView>(id)
            if (firstKey == null) firstKey = v
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
        }
    }

    private fun applyKeyFocus(v: View) {
        val scale = if (compact) 1.08f else 1.12f
        if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.KeyboardKeyAnimations)) {
            v.animate().scaleX(scale).scaleY(scale).setDuration(100).start()
        } else {
            v.scaleX = scale
            v.scaleY = scale
        }
        val borderPx = (if (compact) 1f else 2f) * v.resources.displayMetrics.density
        val corner = (if (compact) 3f else 6f) * v.resources.displayMetrics.density
        v.background = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(surfaceVariantDim)
            setStroke(borderPx.toInt(), primaryColor)
            cornerRadius = corner
        }
    }

    private fun removeKeyFocus(v: View) {
        if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.KeyboardKeyAnimations)) {
            v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        } else {
            v.scaleX = 1f
            v.scaleY = 1f
        }
        v.setBackgroundColor(surfaceVariant)
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
                hide()
                target?.let { t ->
                    t.clearFocus()
                    t.post {
                        if (t.isFocused || (t.parent as? ViewGroup)?.findFocus() == t) {
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
            }
            R.id.keyModeToggle -> toggleMode()
            R.id.keyCapsLock -> toggleCapsLock()
            else -> {
                val char = view.text?.toString() ?: return
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
    }

    private fun toggleCapsLock() {
        isCapsLock = !isCapsLock
        capsLock.text = if (isCapsLock) "CAPS" else "caps"
        capsLock.alpha = if (isCapsLock) 1.0f else 0.5f
        if (!isSymbolsMode) {
            for (i in letterKeys.indices) {
                val key = letterKeys[i]
                val text = key.text.toString()
                key.text = if (isCapsLock) text.uppercase() else text.lowercase()
            }
        }
    }

    private var keyboardHeight = 0

    fun show() {
        if (compact) {
            syncFromTarget()
            visibility = VISIBLE
            requestFocus()
            post {
                firstKey?.requestFocus()
            }
            return
        }
        animate().cancel()
        if (isVisible) return
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
            syncToTarget()
            clearFocus()
            visibility = GONE
            return
        }
        animate().cancel()
        if (!isVisible) return
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
                hide()
                target?.clearFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
