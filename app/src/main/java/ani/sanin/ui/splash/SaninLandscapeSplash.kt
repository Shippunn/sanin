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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun SaninLandscapeSplash(
    onFinished: () -> Unit
) {
    val context = LocalContext.current

    val emblemTargets = remember {
        sampleEmblemPixels(
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.sanin_emblem
            ),
            120
        )
    }

    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
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
     * ------------------------------------------------------
     * BACKGROUND
     * ------------------------------------------------------
     *
     * This is the user's ORIGINAL background artwork.
     * No additional edge glow is created.
     *
     * It starts almost black and gradually reaches its
     * natural brightness.
     */
    val backgroundAlpha = FastOutSlowInEasing.transform(
        ((p - 0.02f) / 0.38f).coerceIn(0f, 1f)
    )

    /*
     * ------------------------------------------------------
     * WORDMARK
     * ------------------------------------------------------
     */

    val wordmarkAlpha = FastOutSlowInEasing.transform(
        ((p - 0.20f) / 0.25f).coerceIn(0f, 1f)
    )

    /*
     * ------------------------------------------------------
     * EMBLEM
     * ------------------------------------------------------
     */

    val particleProgress = FastOutSlowInEasing.transform(
        ((p - 0.30f) / 0.48f).coerceIn(0f, 1f)
    )

    val emblemAlpha = FastOutSlowInEasing.transform(
        ((p - 0.48f) / 0.30f).coerceIn(0f, 1f)
    )

    /*
     * ------------------------------------------------------
     * ONE SYNCHRONIZED SHINE
     * ------------------------------------------------------
     *
     * SANIN + EMBLEM receive the same light event.
     *
     * Neither image changes size or position.
     */

    val shineProgress = (
        (p - 0.64f) / 0.20f
    ).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        /*
         * ==================================================
         * ORIGINAL BACKGROUND
         * ==================================================
         */

        Image(
            painter = painterResource(
                R.drawable.sanin_splash_background
            ),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(backgroundAlpha),
            contentScale = ContentScale.FillBounds
        )

        /*
         * ==================================================
         * FIXED LOGO COMPOSITION
         * ==================================================
         *
         * IMPORTANT:
         * These values NEVER animate.
         * ContentScale.None keeps the PNG dimensions as the
         * source of truth - no fitting, no scaling.
         */

        /*
         * SANIN
         */

        Image(
            painter = painterResource(
                R.drawable.sanin_wordmark
            ),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-65).dp)
                .alpha(wordmarkAlpha),
            contentScale = ContentScale.None
        )

        /*
         * EMBLEM
         */

        Image(
            painter = painterResource(
                R.drawable.sanin_emblem
            ),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 90.dp)
                .alpha(emblemAlpha),
            contentScale = ContentScale.None
        )

        /*
         * ==================================================
         * EMBLEM MATERIALIZATION
         * ==================================================
         *
         * Particles are sampled from the actual alpha pixels
         * of sanin_emblem.png so they form the real emblem
         * silhouette.
         */

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawEmblemParticles(
                targets = emblemTargets,
                progress = particleProgress
            )
        }

        /*
         * ==================================================
         * SYNCHRONIZED LIGHT
         * ==================================================
         *
         * IMPORTANT:
         * This must illuminate the existing PNG pixels.
         *
         * It must NOT transform the PNG itself.
         */

        if (shineProgress > 0f && shineProgress < 1f) {

            LogoShine(
                progress = shineProgress
            )
        }
    }
}


/*
 * ==========================================================
 * SYNCHRONIZED LOGO SHINE
 * ==========================================================
 *
 * The sweep is clipped to the actual alpha of each PNG.
 * Only alpha/brightness is animated - geometry never
 * changes, so neither logo can visibly grow.
 */

@Composable
private fun LogoShine(
    progress: Float
) {

    val shineX = lerp(
        0.25f,
        0.75f,
        progress
    )

    /*
     * Narrow highlight.
     */
    val shineWidth = 0.10f

    /*
     * Brightness is strongest in the center of the shine.
     */
    val intensity =
        sin(progress * Math.PI)
            .toFloat()
            .coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {

        /*
         * --------------------------------------------------
         * SANIN SHINE
         * --------------------------------------------------
         */

        Image(
            painter = painterResource(
                R.drawable.sanin_wordmark
            ),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-65).dp)
                .graphicsLayer {
                    /*
                     * CRITICAL:
                     * No scale transformation.
                     *
                     * The PNG remains exactly the same size.
                     */

                    alpha = intensity * 0.85f

                    compositingStrategy =
                        CompositingStrategy.Offscreen
                }
                .drawWithContent {

                    drawContent()

                    /*
                     * A localized icy-blue brightness layer.
                     *
                     * The layer is masked with the PNG alpha
                     * (SrcIn) so the light is only ever
                     * visible on the actual logo pixels.
                     */

                    drawContext.canvas.saveLayer(
                        Rect(Offset.Zero, size),
                        Paint()
                    )

                    drawContent()

                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(
                                    0.55f,
                                    0.90f,
                                    1f,
                                    0.85f
                                ),
                                Color.Transparent
                            ),
                            startX =
                                size.width *
                                    (shineX - shineWidth),
                            endX =
                                size.width *
                                    (shineX + shineWidth)
                        ),
                        blendMode =
                            BlendMode.SrcIn
                    )

                    drawContext.canvas.restore()
                },
            contentScale = ContentScale.None
        )

        /*
         * --------------------------------------------------
         * EMBLEM SHINE
         * --------------------------------------------------
         */

        Image(
            painter = painterResource(
                R.drawable.sanin_emblem
            ),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 90.dp)
                .graphicsLayer {
                    /*
                     * SAME fixed geometry.
                     *
                     * Absolutely no scaling.
                     */

                    alpha = intensity * 0.95f

                    compositingStrategy =
                        CompositingStrategy.Offscreen
                }
                .drawWithContent {

                    drawContent()

                    drawContext.canvas.saveLayer(
                        Rect(Offset.Zero, size),
                        Paint()
                    )

                    drawContent()

                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(
                                    0.55f,
                                    0.90f,
                                    1f,
                                    0.90f
                                ),
                                Color.Transparent
                            ),
                            startX =
                                size.width *
                                    (shineX - shineWidth),
                            endX =
                                size.width *
                                    (shineX + shineWidth)
                        ),
                        blendMode =
                            BlendMode.SrcIn
                    )

                    drawContext.canvas.restore()
                },
            contentScale = ContentScale.None
        )
    }
}


/*
 * ==========================================================
 * EMBLEM SAMPLING
 * ==========================================================
 */

private fun sampleEmblemPixels(
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

    val centerX = size.width / 2f
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
                0.10f,
                0.55f,
                1f,
                alpha
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
 * UTILITY
 * ==========================================================
 */

private fun lerp(
    start: Float,
    end: Float,
    fraction: Float
): Float {
    return start +
        (end - start) * fraction
}
