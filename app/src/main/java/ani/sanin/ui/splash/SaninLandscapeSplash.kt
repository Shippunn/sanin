package ani.sanin.ui.splash

import ani.sanin.R
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun SaninLandscapeSplash(
    onFinished: () -> Unit
) {
    val context = LocalContext.current

    val background = remember {
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.sanin_splash_background
        ).asImageBitmap()
    }

    val emblemBitmap = remember {
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.sanin_emblem
        )
    }

    val emblemImage = remember {
        emblemBitmap.asImageBitmap()
    }

    val wordmarkBitmap = remember {
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.sanin_wordmark
        )
    }

    val wordmark = painterResource(
        R.drawable.sanin_wordmark
    )

    val targets = remember(emblemBitmap) {
        sampleEmblem(
            emblemBitmap,
            120
        )
    }

    val progress = remember {
        Animatable(0f)
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            animationSpec = tween(
                durationMillis = 2400,
                easing = LinearEasing
            )
        )

        delay(250)

        onFinished()
    }

    val p = progress.value

    /*
     * ======================================================
     * NATURAL BACKGROUND REVEAL
     * ======================================================
     *
     * The supplied background already contains the correct
     * blue corner artwork.
     *
     * We simply reveal it gradually from black.
     */

    val backgroundAlpha =
        FastOutSlowInEasing.transform(
            ((p - 0.02f) / 0.38f)
                .coerceIn(0f, 1f)
        )

    /*
     * SANIN appears early.
     */

    val logoAlpha =
        FastOutSlowInEasing.transform(
            ((p - 0.12f) / 0.25f)
                .coerceIn(0f, 1f)
        )

    /*
     * EMBLEM PARTICLE FORMATION
     */

    val particleProgress =
        FastOutSlowInEasing.transform(
            ((p - 0.27f) / 0.48f)
                .coerceIn(0f, 1f)
        )

    val emblemAlpha =
        FastOutSlowInEasing.transform(
            ((p - 0.42f) / 0.35f)
                .coerceIn(0f, 1f)
        )

    /*
     * ONE synchronized shine across BOTH logos.
     */

    val shine =
        ((p - 0.62f) / 0.20f)
            .coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        /*
         * ==================================================
         * BACKGROUND
         * ==================================================
         */

        Image(
            bitmap = background,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(backgroundAlpha),
            contentScale = ContentScale.FillBounds
        )

        /*
         * ==================================================
         * LOGO COMPOSITION
         * ==================================================
         *
         * SANIN above.
         * EMBLEM below.
         *
         * They must never overlap.
         */

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            Image(
                painter = wordmark,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-65).dp)
                    .alpha(logoAlpha)
            )

            Image(
                bitmap = emblemImage,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 90.dp)
                    .alpha(emblemAlpha)
            )
        }

        /*
         * ==================================================
         * EMBLEM MATERIALIZATION
         * ==================================================
         */

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawEmblemParticles(
                targets = targets,
                progress = particleProgress
            )
        }

        /*
         * ==================================================
         * SYNCHRONIZED LOGO SHINE
         * ==================================================
         *
         * ONE light source travels across both SANIN
         * and the emblem.
         *
         * The shine is clipped to the alpha masks of the
         * supplied PNGs so no rectangle is visible outside
         * the actual logo pixels.
         */

        if (shine > 0f && shine < 1f) {

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawLogoShine(
                    progress = shine,
                    wordmark = wordmarkBitmap.asImageBitmap(),
                    emblem = emblemImage
                )
            }
        }
    }
}


/*
 * ==========================================================
 * EMBLEM SAMPLING
 * ==========================================================
 */

private fun sampleEmblem(
    bitmap: Bitmap,
    maxParticles: Int
): List<Offset> {

    val result = mutableListOf<Offset>()

    val step =
        sqrt(
            bitmap.width.toFloat() *
                bitmap.height.toFloat() /
                maxParticles
        )
            .toInt()
            .coerceAtLeast(2)

    for (y in 0 until bitmap.height step step) {

        for (x in 0 until bitmap.width step step) {

            val pixel =
                bitmap.getPixel(x, y)

            if (android.graphics.Color.alpha(pixel) > 40) {

                result += Offset(
                    x.toFloat() /
                        bitmap.width,
                    y.toFloat() /
                        bitmap.height
                )
            }
        }
    }

    return result
        .shuffled(Random(42))
        .take(maxParticles)
}


/*
 * ==========================================================
 * EMBLEM PARTICLES
 * ==========================================================
 */

private fun DrawScope.drawEmblemParticles(
    targets: List<Offset>,
    progress: Float
) {

    if (progress <= 0f) return

    val centerX =
        size.width / 2f

    val centerY =
        size.height / 2f +
            90.dp.toPx()

    val emblemWidth =
        230.dp.toPx()

    val emblemHeight =
        310.dp.toPx()

    targets.forEachIndexed { index, target ->

        val delay =
            (index % 19) /
                19f *
                0.25f

        val local =
            ((progress - delay) /
                (1f - delay))
                .coerceIn(0f, 1f)

        val eased =
            FastOutSlowInEasing.transform(
                local
            )

        val targetX =
            centerX -
                emblemWidth / 2f +
                target.x * emblemWidth

        val targetY =
            centerY -
                emblemHeight / 2f +
                target.y * emblemHeight

        /*
         * Start particles close to their final
         * destination so they form the emblem,
         * rather than exploding across the screen.
         */

        val angle =
            index * 2.39996f

        val radius =
            65.dp.toPx() +
                (index % 7) *
                9.dp.toPx()

        val startX =
            targetX +
                cos(angle) * radius

        val startY =
            targetY +
                sin(angle) * radius

        val x =
            lerp(
                startX,
                targetX,
                eased
            )

        val y =
            lerp(
                startY,
                targetY,
                eased
            )

        val alpha =
            (1f - eased) *
                0.9f

        drawCircle(
            color = Color(
                red = 0.10f,
                green = 0.55f,
                blue = 1f,
                alpha = alpha
            ),
            radius =
                (1.2f + index % 3)
                    .dp
                    .toPx(),
            center = Offset(x, y)
        )
    }
}


/*
 * ==========================================================
 * SYNCHRONIZED LOGO SHINE
 * ==========================================================
 *
 * The shine sweep calculation is intentionally kept here.
 * The final rendering clips the sweep to the alpha masks of
 * sanin_wordmark.png and sanin_emblem.png, so the light is
 * only ever visible on the actual logo pixels.
 */

private fun DrawScope.drawLogoShine(
    progress: Float,
    wordmark: ImageBitmap,
    emblem: ImageBitmap
) {

    /*
     * The logos are rendered centered, SANIN 65.dp above the
     * center and the emblem 90.dp below it, at their intrinsic
     * sizes. Mirror that here so the masks line up exactly
     * with the visible logos.
     */

    val centerX =
        size.width / 2f

    val centerY =
        size.height / 2f

    val wordmarkLeft =
        centerX -
            wordmark.width * density / 2f

    val wordmarkTop =
        centerY -
            65f * density -
            wordmark.height * density / 2f

    val emblemLeft =
        centerX -
            emblem.width * density / 2f

    val emblemTop =
        centerY +
            90f * density -
            emblem.height * density / 2f

    /*
     * ONE light source travels across both SANIN
     * and the emblem.
     */

    val x =
        size.width *
            (
                0.28f +
                    progress * 0.44f
            )

    val width =
        size.width * 0.045f

    val gradient =
        Brush.linearGradient(
            colors = listOf(
                Color.Transparent,

                Color(
                    red = 0.45f,
                    green = 0.85f,
                    blue = 1f,
                    alpha = 0.15f
                ),

                Color(
                    red = 0.70f,
                    green = 0.95f,
                    blue = 1f,
                    alpha = 0.95f
                ),

                Color(
                    red = 0.35f,
                    green = 0.75f,
                    blue = 1f,
                    alpha = 0.20f
                ),

                Color.Transparent
            ),
            start = Offset(
                x - width,
                0f
            ),
            end = Offset(
                x + width,
                0f
            )
        )

    /*
     * Render the sweep inside a layer containing only the two
     * logos, then draw the sweep with SrcIn so it is masked by
     * the PNG alpha and never appears as a rectangle outside
     * the actual logo pixels.
     */

    val maskBounds =
        Rect(
            left = min(
                wordmarkLeft,
                emblemLeft
            ),
            top = min(
                wordmarkTop,
                emblemTop
            ),
            right = max(
                wordmarkLeft +
                    wordmark.width * density,
                emblemLeft +
                    emblem.width * density
            ),
            bottom = max(
                wordmarkTop +
                    wordmark.height * density,
                emblemTop +
                    emblem.height * density
            )
        )

    saveLayer(
        maskBounds,
        Paint()
    ) {

        drawImage(
            image = wordmark,
            dstOffset = IntOffset(
                wordmarkLeft.roundToInt(),
                wordmarkTop.roundToInt()
            ),
            dstSize = IntSize(
                (wordmark.width * density).roundToInt(),
                (wordmark.height * density).roundToInt()
            )
        )

        drawImage(
            image = emblem,
            dstOffset = IntOffset(
                emblemLeft.roundToInt(),
                emblemTop.roundToInt()
            ),
            dstSize = IntSize(
                (emblem.width * density).roundToInt(),
                (emblem.height * density).roundToInt()
            )
        )

        drawRect(
            brush = gradient,
            topLeft = Offset(
                x - width,
                maskBounds.top
            ),
            size = Size(
                width * 2f,
                maskBounds.height
            ),
            alpha = 0.85f,
            blendMode = BlendMode.SrcIn
        )
    }
}


/*
 * ==========================================================
 * UTILITY
 * ==========================================================
 */

private fun lerp(
    start: Float,
    end: Float,
    fraction: Float
): Float =
    start +
        (end - start) *
        fraction
