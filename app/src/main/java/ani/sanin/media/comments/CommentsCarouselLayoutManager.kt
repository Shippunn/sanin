package ani.sanin.media.comments

import android.content.Context
import android.graphics.PointF
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class CommentsCarouselLayoutManager(
    context: Context
) : LinearLayoutManager(context, VERTICAL, false) {

    private var itemHeight = 200
    private var itemWidth = 0

    private val cylinderRadius = 1200f
    private val angleStep = 30f
    private val focusGap = 140f

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams =
        RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (itemCount == 0) {
            detachAndScrapAttachedViews(recycler)
            return
        }
        val parentWidth = width - paddingLeft - paddingRight
        val parentHeight = height - paddingTop - paddingBottom
        if (parentWidth <= 0 || parentHeight <= 0) return

        val view = recycler.getViewForPosition(0)
        measureChildWithMargins(view, 0, 0)
        itemHeight = (parentHeight * 0.30f).toInt().coerceAtLeast(view.measuredHeight)
        itemWidth = (parentWidth * 0.85f).toInt()
        recycler.recycleView(view)

        if (itemHeight <= 0) return

        fill(recycler, state)
    }

    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
        applyTransform()
    }

    private var pixelOffset = 0f
    var focusedPosition: Int get() {
        if (itemCount == 0) return 0
        val raw = pixelOffset / itemHeight.toFloat()
        return raw.toInt().coerceIn(0, itemCount - 1)
    }

    private fun fill(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (itemCount == 0) return

        detachAndScrapAttachedViews(recycler)

        val parentWidth = width
        val centerY = height / 2f
        val visibleRange = 6
        val focusIndex = focusedPosition

        val startPos = (focusIndex - visibleRange).coerceAtLeast(0)
        val endPos = (focusIndex + visibleRange).coerceAtMost(itemCount - 1)

        for (pos in startPos..endPos) {
            val child = recycler.getViewForPosition(pos)
            val offset = pos - focusIndex
            val gap = if (offset == 0) focusGap else focusGap * 0.25f
            val yCenter = centerY + offset * (itemHeight.toFloat() + gap)

            addView(child)
            measureChildWithMargins(child, 0, 0)
            val top = (yCenter - itemHeight / 2f).toInt()
            layoutDecoratedWithMargins(
                child,
                (parentWidth - itemWidth) / 2,
                top,
                (parentWidth + itemWidth) / 2,
                top + child.measuredHeight.coerceAtMost((itemHeight * 1.5f).toInt())
            )
        }
    }

    fun scrollToNext() {
        val maxPos = itemCount - 1
        if (focusedPosition < maxPos) {
            pixelOffset = ((focusedPosition + 1) * itemHeight).toFloat()
            requestLayout()
        }
    }

    fun scrollToPrevious() {
        if (focusedPosition > 0) {
            pixelOffset = ((focusedPosition - 1) * itemHeight).toFloat()
            requestLayout()
        }
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        if (itemCount == 0) return 0
        val maxOffset = ((itemCount - 1) * itemHeight).toFloat().coerceAtLeast(0f)
        val oldOffset = pixelOffset
        pixelOffset = (pixelOffset + dy).coerceIn(0f, maxOffset)
        val consumed = (pixelOffset - oldOffset).roundToInt()
        fill(recycler, state)
        return consumed
    }

    override fun canScrollVertically() = true

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF {
        val direction = if (targetPosition < focusedPosition) -1f else 1f
        return PointF(0f, direction)
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int
    ) {
        super.smoothScrollToPosition(recyclerView, state, position)
    }

    private fun applyTransform() {
        val centerY = height / 2f

        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val childCenterY = child.top + child.height / 2f
            val distanceFromCenter = childCenterY - centerY
            val normalizedDist = distanceFromCenter / itemHeight.coerceAtLeast(1).toFloat()

            val angle = Math.toRadians((normalizedDist * angleStep).toDouble()).toFloat()
            val cosVal = cos(angle).coerceAtLeast(0.01f)
            val sinVal = sin(angle)

            val alpha = 1f - (1f - cosVal) * 0.45f
            val rotationX = -normalizedDist * 12f
            val translationZ = -sinVal * cylinderRadius * 0.3f

            val isFocused = abs(normalizedDist) < 0.5f

            child.scaleX = 1f
            child.scaleY = 1f
            child.alpha = alpha.coerceIn(0f, 1f)
            child.rotationX = rotationX.coerceIn(-45f, 45f)
            child.translationZ = translationZ.coerceIn(-500f, 0f)
            child.elevation = if (isFocused) 24f else 4f
        }
    }
}
