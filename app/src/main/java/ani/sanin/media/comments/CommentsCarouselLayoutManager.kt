package ani.sanin.media.comments

import android.content.Context
import android.graphics.PointF
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class CommentsCarouselLayoutManager(
    context: Context
) : RecyclerView.LayoutManager() {

    private var verticalScrollOffset = 0
    private var itemHeight = 0
    private var itemWidth = 0
    private var totalHeight = 0

    private val cylinderRadius = 1200f
    private val angleStep = 30f
    private val focusedScale = 1.35f
    private val unfocusedScale = 0.72f
    private val focusedAlpha = 1f
    private val unfocusedAlpha = 0.55f
    private val focusGap = 140f

    val focusedPosition: Int get() {
        if (itemCount == 0) return 0
        val raw = verticalScrollOffset.toFloat() / itemHeight.coerceAtLeast(1)
        return raw.toInt().coerceIn(0, itemCount - 1)
    }

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

        detachAndScrapAttachedViews(recycler)

        val view = recycler.getViewForPosition(0)
        measureChildWithMargins(view, 0, 0)
        itemHeight = view.decoratedMeasuredHeight
        itemWidth = (parentWidth * 0.85f).toInt()
        recycler.recycleView(view)

        if (itemHeight <= 0) return

        totalHeight = itemCount * itemHeight
        val maxScroll = (itemCount - 1) * itemHeight
        verticalScrollOffset = verticalScrollOffset.coerceIn(0, maxScroll.coerceAtLeast(0))

        fill(recycler, state)
    }

    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
        applyTransformToChildren()
    }

    private fun fill(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (itemCount == 0) return

        val parentHeight = height
        val centerY = parentHeight / 2f

        val focusIndex = focusedPosition
        val visibleRange = 8

        val startPos = (focusIndex - visibleRange).coerceAtLeast(0)
        val endPos = (focusIndex + visibleRange).coerceAtMost(itemCount - 1)

        val viewsToDetach = mutableListOf<View>()
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val pos = getPosition(child)
            if (pos < startPos || pos > endPos) {
                viewsToDetach.add(child)
            }
        }
        viewsToDetach.forEach { removeAndRecycleView(it, recycler) }

        for (pos in startPos..endPos) {
            val child = recycler.getViewForPosition(pos)
            val offset = pos - focusIndex
            val yCenter = centerY + offset * (itemHeight.coerceAtLeast(1) + if (offset == 0) focusGap else (focusGap * 0.25f).toInt())
            val top = yCenter - itemHeight / 2f

            addView(child)
            measureChildWithMargins(child, 0, 0)
            val w = if (offset == 0) (itemWidth * focusedScale).toInt() else itemWidth
            val h = child.measuredHeight
            layoutDecoratedWithMargins(child, (width - w) / 2, top.toInt(), (width + w) / 2, top.toInt() + h)
        }

        applyTransformToChildren()
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        if (itemCount == 0) return 0
        val maxScroll = ((itemCount - 1) * itemHeight.coerceAtLeast(1)).coerceAtLeast(0)
        val target = (verticalScrollOffset + dy).coerceIn(0, maxScroll)
        val consumed = target - verticalScrollOffset
        verticalScrollOffset = target
        fill(recycler, state)
        return consumed
    }

    override fun canScrollVertically() = true

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF {
        val direction = if (targetPosition < focusedPosition) -1f else 1f
        return PointF(0f, direction)
    }

    override fun requestChildRectangleOnScreen(
        parent: RecyclerView,
        child: View,
        rect: android.graphics.Rect,
        immediate: Boolean,
        focusedChildVisible: Boolean
    ): Boolean {
        val pos = getPosition(child)
        smoothScrollToPosition(parent, parent.state, pos)
        return true
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int
    ) {
        val smoothScroller = object : RecyclerView.SmoothScroller() {
            override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
                return this@CommentsCarouselLayoutManager.computeScrollVectorForPosition(targetPosition)
            }

            override fun onTargetFound(
                targetView: View,
                state: RecyclerView.SmoothScroller.Action,
                action: RecyclerView.SmoothScroller.Action
            ) {
                val dy = calculateDistanceToPosition(targetView)
                if (dy != 0) {
                    action.update(0, dy, 250, androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                }
            }
        }
        smoothScroller.targetPosition = position
        startSmoothScroll(smoothScroller)
    }

    private fun calculateDistanceToPosition(target: View): Int {
        val pos = getPosition(target)
        val targetScroll = pos * itemHeight.coerceAtLeast(1)
        return targetScroll - verticalScrollOffset
    }

    private fun applyTransformToChildren() {
        val parentHeight = height
        val centerY = parentHeight / 2f

        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val childCenterY = child.top + child.height / 2f
            val distanceFromCenter = childCenterY - centerY
            val normalizedDist = distanceFromCenter / itemHeight.coerceAtLeast(1).toFloat()

            val angle = Math.toRadians((normalizedDist * angleStep).toDouble()).toFloat()
            val cosVal = kotlin.math.cos(angle).coerceAtLeast(0.01f)
            val sinVal = kotlin.math.sin(angle)

            val scale = focusedScale - (1f - cosVal) * (focusedScale - unfocusedScale)
            val alpha = focusedAlpha - (1f - cosVal) * (focusedAlpha - unfocusedAlpha)
            val rotationX = -normalizedDist * 12f
            val translationZ = -sinVal * cylinderRadius * 0.3f

            val isFocused = kotlin.math.abs(normalizedDist) < 0.5f

            child.scaleX = scale
            child.scaleY = scale
            child.alpha = alpha.coerceIn(0f, 1f)
            child.rotationX = rotationX.coerceIn(-45f, 45f)
            child.translationZ = translationZ.coerceIn(-500f, 0f)

            child.elevation = if (isFocused) 24f else 4f
        }
    }

    override fun isAutoMeasureEnabled() = false
}
