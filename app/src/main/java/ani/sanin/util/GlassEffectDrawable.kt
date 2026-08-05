package ani.sanin.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

class GlassEffectDrawable(
    targetView: View,
    cornerRadiusPx: Float = 0f,
    blurRadius: Float = 25f,
    @ColorInt tintColor: Int = 0x66000000,
    refreshOnLayout: Boolean = true
) : Drawable() {

    private val targetRef = WeakReference(targetView)
    private var captureRootRef: WeakReference<View>? = null
    private var backdropCache: Bitmap? = null
    private var blurCache: Bitmap? = null
    private var effectsCache: Bitmap? = null

    private val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = tintColor }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0x40000000
        maskFilter = android.graphics.BlurMaskFilter(24f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val clipPath = Path()
    private val clipRect = RectF()
    private var cornerRad = cornerRadiusPx
    private var downscaleFactor = 0.25f

    private var lastWidth = -1
    private var lastHeight = -1
    private var lastBlurRadius = blurRadius.roundToInt().coerceIn(1, 100)
    private var lastVibrancy = 1.0f
    private var lastChromaticAberration = 0.0f
    private var lastRefractionAmount = 0.0f
    private var lastRefractionHeight = 0.0f
    private var depthEnabled = false

    private var isCapturing = false
    private var needsEffectsRefresh = false
    var averageBrightness: Float = 0.5f
        private set

    fun setCaptureRootView(root: View) {
        captureRootRef = WeakReference(root)
        invalidateCache()
        invalidateSelf()
    }

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        invalidateCache()
        targetRef.get()?.invalidate()
    }

    init {
        if (cornerRad > 0f && refreshOnLayout) {
            targetView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
    }

    fun setTintColor(@ColorInt color: Int) {
        tintPaint.color = color
        invalidateSelf()
    }

    fun setCornerRadius(radiusPx: Float) {
        cornerRad = radiusPx
        invalidateSelf()
    }

    fun setVibrancy(vibrancy: Float) {
        if (lastVibrancy != vibrancy) {
            lastVibrancy = vibrancy
            invalidateEffects()
        }
    }

    fun setChromaticAberration(amount: Float) {
        val clamped = amount.coerceIn(0f, 1f)
        if (lastChromaticAberration != clamped) {
            lastChromaticAberration = clamped
            invalidateEffects()
        }
    }

    fun setRefractionAmount(amount: Float) {
        val clamped = amount.coerceIn(0f, 1f)
        if (lastRefractionAmount != clamped) {
            lastRefractionAmount = clamped
            invalidateEffects()
        }
    }

    fun setRefractionHeight(height: Float) {
        val clamped = height.coerceIn(0f, 1f)
        if (lastRefractionHeight != clamped) {
            lastRefractionHeight = clamped
            invalidateEffects()
        }
    }

    fun setDepthEnabled(enabled: Boolean) {
        if (depthEnabled != enabled) {
            depthEnabled = enabled
            invalidateSelf()
        }
    }

    private fun invalidateEffects() {
        effectsCache = null
        invalidateSelf()
    }

    fun invalidateCache() {
        backdropCache = null
        blurCache = null
        effectsCache = null
    }

    private var lastScrollInvalidation = 0L

    fun invalidateOnScroll(scrollableView: View) {
        val invalidator = Runnable {
            invalidateCache()
            targetRef.get()?.invalidate()
        }
        val throttleMs = 80L
        val onScroll = {
            val now = System.currentTimeMillis()
            if (now - lastScrollInvalidation >= throttleMs) {
                lastScrollInvalidation = now
                scrollableView.postOnAnimation(invalidator)
            }
        }
        if (scrollableView is RecyclerView) {
            scrollableView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    onScroll()
                }
            })
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            scrollableView.setOnScrollChangeListener { _, _, _, _, _ -> onScroll() }
        }
    }

    override fun draw(canvas: Canvas) {
        if (isCapturing) return
        val w = bounds.width()
        val h = bounds.height()
        if (w <= 0 || h <= 0) return

        val target = targetRef.get() ?: return

        if (blurCache == null || lastWidth != w || lastHeight != h) {
            lastWidth = w
            lastHeight = h
            captureAndBlur(target, w, h)
            if (blurCache == null) return
            effectsCache = null
        }

        val blur = blurCache ?: return

        val effectsBitmap = if (effectsCache == null) {
            applyEffects(blur)
        } else {
            effectsCache
        } ?: blur

        canvas.save()
        if (cornerRad > 0f) {
            clipPath.rewind()
            clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
            clipPath.addRoundRect(clipRect, cornerRad, cornerRad, Path.Direction.CW)
            canvas.clipPath(clipPath)
        }

        if (depthEnabled) {
            canvas.drawRoundRect(
                0f, 0f, w.toFloat(), h.toFloat(),
                cornerRad, cornerRad, shadowPaint
            )
        }

        canvas.drawBitmap(effectsBitmap, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), blurPaint)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), tintPaint)
        canvas.restore()
    }

    private fun applyEffects(blur: Bitmap): Bitmap? {
        var result = blur

        if (lastVibrancy != 1.0f || lastChromaticAberration > 0f || lastRefractionAmount > 0f) {
            val w = blur.width
            val h = blur.height
            if (w <= 0 || h <= 0) return null

            val pixels = IntArray(w * h)
            blur.getPixels(pixels, 0, w, 0, 0, w, h)
            val out = pixels.copyOf()

            val cx = w / 2f
            val cy = h / 2f
            val maxDist = sqrt(cx * cx + cy * cy).coerceAtLeast(1f)
            val refractStrength = lastRefractionHeight * lastRefractionAmount
            val caStrength = lastChromaticAberration * 4f

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val dx = x - cx
                    val dy = y - cy
                    val dist = sqrt(dx * dx + dy * dy) / maxDist

                    var srcX = x.toFloat()
                    var srcY = y.toFloat()

                    if (refractStrength > 0f) {
                        val bulge = dist * refractStrength * 0.3f
                        srcX = (x + dx * bulge).coerceIn(0f, (w - 1).toFloat())
                        srcY = (y + dy * bulge).coerceIn(0f, (h - 1).toFloat())
                    }

                    if (caStrength > 0f && dist > 0f) {
                        val shift = dist * caStrength
                        val rX = (srcX + shift).roundToInt().coerceIn(0, w - 1)
                        val rY = (srcY - shift * 0.5f).roundToInt().coerceIn(0, h - 1)
                        val bX = (srcX - shift).roundToInt().coerceIn(0, w - 1)
                        val bY = (srcY + shift * 0.5f).roundToInt().coerceIn(0, h - 1)
                        val srcIdx = srcY.roundToInt() * w + srcX.roundToInt()
                        val rIdx = rY * w + rX
                        val bIdx = bY * w + bX
                        val gIdx = srcIdx

                        val s = srcIdx.coerceIn(0, pixels.size - 1)
                        val ri = rIdx.coerceIn(0, pixels.size - 1)
                        val gi = gIdx.coerceIn(0, pixels.size - 1)
                        val bi = bIdx.coerceIn(0, pixels.size - 1)

                        out[y * w + x] = Color.argb(
                            Color.alpha(pixels[s]),
                            Color.red(pixels[ri]),
                            Color.green(pixels[gi]),
                            Color.blue(pixels[bi])
                        )
                    } else if (refractStrength > 0f) {
                        val si = srcY.roundToInt() * w + srcX.roundToInt()
                        out[y * w + x] = pixels[si.coerceIn(0, pixels.size - 1)]
                    }
                }
            }

            if (lastVibrancy != 1.0f) {
                val vibrancy = lastVibrancy.coerceIn(0f, 2f)
                for (i in out.indices) {
                    val r = Color.red(out[i])
                    val g = Color.green(out[i])
                    val b = Color.blue(out[i])
                    val gray = (r * 0.299f + g * 0.587f + b * 0.114f)
                    val nr = (gray + (r - gray) * vibrancy).roundToInt().coerceIn(0, 255)
                    val ng = (gray + (g - gray) * vibrancy).roundToInt().coerceIn(0, 255)
                    val nb = (gray + (b - gray) * vibrancy).roundToInt().coerceIn(0, 255)
                    out[i] = Color.argb(Color.alpha(out[i]), nr, ng, nb)
                }
            }

            val bmp = try {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                return null
            }
            bmp.setPixels(out, 0, w, 0, 0, w, h)
            result = bmp
        }

        effectsCache = result
        return result
    }

    private fun captureAndBlur(target: View, w: Int, h: Int) {
        val ds = downscaleFactor
        val sw = (w * ds).roundToInt().coerceAtLeast(1)
        val sh = (h * ds).roundToInt().coerceAtLeast(1)

        val root = captureRootRef?.get() ?: target.rootView
        val bitmap = try {
            Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return
        }
        val c = Canvas(bitmap)
        c.scale(ds, ds)
        val pos = IntArray(2)
        target.getLocationInWindow(pos)
        c.translate(-pos[0].toFloat(), -pos[1].toFloat())
        isCapturing = true
        try {
            root.draw(c)
        } catch (e: Exception) {
            isCapturing = false
            return
        }
        isCapturing = false

        backdropCache = bitmap
        blurCache = fastBlur(bitmap, lastBlurRadius)
        computeAverageBrightness(bitmap)
    }

    override fun setAlpha(alpha: Int) {
        blurPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        blurPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = lastWidth.coerceAtLeast(0)
    override fun getIntrinsicHeight(): Int = lastHeight.coerceAtLeast(0)

    private fun computeAverageBrightness(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return
        val totalPixels = w * h
        val step = (totalPixels / 64).coerceAtLeast(1)
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var total = 0f
        var count = 0
        var i = 0
        while (i < totalPixels) {
            val p = pixels[i]
            total += (Color.red(p) * 0.299f + Color.green(p) * 0.587f + Color.blue(p) * 0.114f)
            count++
            i += step
        }
        averageBrightness = (total / count / 255f).coerceIn(0f, 1f)
    }

    fun destroy() {
        val target = targetRef.get()
        if (target != null) {
            try {
                target.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            } catch (_: Exception) {}
        }
        invalidateCache()
    }

    companion object {
        fun fastBlur(bitmap: Bitmap, radius: Int): Bitmap {
            val r = radius.coerceIn(1, 100)
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return bitmap

            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val result = stackBlur(pixels, w, h, r)
            val output = try {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                return bitmap
            }
            output.setPixels(result, 0, w, 0, 0, w, h)
            return output
        }

        private fun stackBlur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
            val r = radius.coerceIn(1, 100)
            val div = r + r + 1
            val wm = w - 1
            val hm = h - 1
            val windowSize = div.coerceAtMost(w * h)
            val pix = pixels.copyOf()

            val dv = IntArray(256 * div)
            for (i in dv.indices) dv[i] = i / div

            val s = IntArray(windowSize)
            var yi = 0

            for (y in 0 until h) {
                var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0
                for (i in -r..r) {
                    val col = pix[yi + (wm.coerceAtMost(i.coerceAtLeast(0)))]
                    s[i + r] = col
                    sumR += (col shr 16) and 0xFF
                    sumG += (col shr 8) and 0xFF
                    sumB += col and 0xFF
                    sumA += (col shr 24) and 0xFF
                }
                for (x in 0 until w) {
                    pix[yi] = (dv[sumA.coerceIn(0, dv.size - 1)] shl 24) or
                            (dv[sumR.coerceIn(0, dv.size - 1)] shl 16) or
                            (dv[sumG.coerceIn(0, dv.size - 1)] shl 8) or
                            dv[sumB.coerceIn(0, dv.size - 1)]
                    val left = s[0]
                    val right = if (x + r < wm) s[r + r] else s[windowSize - 1]
                    sumR += ((right shr 16) and 0xFF) - ((left shr 16) and 0xFF)
                    sumG += ((right shr 8) and 0xFF) - ((left shr 8) and 0xFF)
                    sumB += (right and 0xFF) - (left and 0xFF)
                    sumA += ((right shr 24) and 0xFF) - ((left shr 24) and 0xFF)
                    s.copyInto(s, 0, 1, windowSize)
                    val nextCol = if (x + r + 1 < w) pix[yi + r + 1] else pix[yi + (wm - x)]
                    s[windowSize - 1] = nextCol
                    yi++
                }
            }

            for (x in 0 until w) {
                yi = x
                var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0
                for (i in -r..r) {
                    val col = pix[yi + (hm.coerceAtMost(i.coerceAtLeast(0))) * w]
                    s[i + r] = col
                    sumR += (col shr 16) and 0xFF
                    sumG += (col shr 8) and 0xFF
                    sumB += col and 0xFF
                    sumA += (col shr 24) and 0xFF
                }
                for (y in 0 until h) {
                    pix[yi] = (dv[sumA.coerceIn(0, dv.size - 1)] shl 24) or
                            (dv[sumR.coerceIn(0, dv.size - 1)] shl 16) or
                            (dv[sumG.coerceIn(0, dv.size - 1)] shl 8) or
                            dv[sumB.coerceIn(0, dv.size - 1)]
                    val left = s[0]
                    val right = if (y + r < hm) s[r + r] else s[windowSize - 1]
                    sumR += ((right shr 16) and 0xFF) - ((left shr 16) and 0xFF)
                    sumG += ((right shr 8) and 0xFF) - ((left shr 8) and 0xFF)
                    sumB += (right and 0xFF) - (left and 0xFF)
                    sumA += ((right shr 24) and 0xFF) - ((left shr 24) and 0xFF)
                    s.copyInto(s, 0, 1, windowSize)
                    val nextCol = if (y + r + 1 < h) pix[yi + (r + 1) * w] else pix[yi + (hm - y) * w]
                    s[windowSize - 1] = nextCol
                    yi += w
                }
            }

            return pix
        }

        fun applyToView(
            view: View,
            cornerRadiusDp: Float = 16f,
            blurRadius: Float = 25f,
            tintColor: Int = 0x66000000
        ): GlassEffectDrawable {
            val density = view.resources.displayMetrics.density
            val radiusPx = cornerRadiusDp * density
            val drawable = GlassEffectDrawable(
                targetView = view,
                cornerRadiusPx = radiusPx,
                blurRadius = blurRadius,
                tintColor = tintColor,
                refreshOnLayout = true
            )
            view.background = drawable
            return drawable
        }
    }
}
