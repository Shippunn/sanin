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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.maxOf
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun SaninLandscapeSplash(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val emblemBitmap = remember {
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.sanin_emblem
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

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {

        /*
         * Emblem particles are generated once per layout.
         * They spawn far away and travel along curved
         * Bezier paths to their exact emblem pixel.
         */
        val emblemParticles = remember(emblemBitmap, maxWidth, maxHeight) {
            with(density) {
                val width = maxWidth.toPx()
                val height = maxHeight.toPx()
                createEmblemParticles(
                    bitmap = emblemBitmap,
                    screenWidth = width,
                    screenHeight = height,
                    emblemCenterX = width / 2f,
                    emblemCenterY = height / 2f + 90.dp.toPx(),
                    emblemWidth = 230.dp.toPx(),
                    emblemHeight = 310.dp.toPx()
                )
            }
        }

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
                particles = emblemParticles,
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
 * EMBLEM PARTICLE GENERATION
 * ==========================================================
 */

private data class EmblemParticle(
    val startX: Float,
    val startY: Float,
    val controlX: Float,
    val controlY: Float,
    val targetX: Float,
    val targetY: Float,
    val delay: Float,
    val duration: Float,
    val radius: Float,
    val alpha: Float
)

private fun createEmblemParticles(
    bitmap: Bitmap,
    screenWidth: Float,
    screenHeight: Float,
    emblemCenterX: Float,
    emblemCenterY: Float,
    emblemWidth: Float,
    emblemHeight: Float,
    count: Int = 220
): List<EmblemParticle> {

    val random = Random(42)

    /*
     * Sample the actual visible pixels of the PNG.
     */
    val visiblePixels = mutableListOf<Pair<Float, Float>>()

    val step = maxOf(
        2,
        sqrt(
            bitmap.width.toFloat() *
                bitmap.height.toFloat() /
                count
        ).toInt()
    )

    for (y in 0 until bitmap.height step step) {

        for (x in 0 until bitmap.width step step) {

            if (android.graphics.Color.alpha(
                    bitmap.getPixel(x, y)
                ) > 45
            ) {
                visiblePixels += Pair(
                    x.toFloat() / bitmap.width,
                    y.toFloat() / bitmap.height
                )
            }
        }
    }

    val maxSpan = maxOf(screenWidth, screenHeight)

    return visiblePixels
        .shuffled(random)
        .take(count)
        .map { target ->

            /*
             * Exact destination on the emblem.
             */
            val targetX =
                emblemCenterX -
                    emblemWidth / 2f +
                    target.first * emblemWidth

            val targetY =
                emblemCenterY -
                    emblemHeight / 2f +
                    target.second * emblemHeight

            /*
             * Start FAR away from the target:
             * roughly 28-66% of the screen's largest
             * dimension, in any direction.
             */
            val angle =
                random.nextFloat() *
                    (Math.PI * 2f).toFloat()

            val distance =
                maxSpan *
                    (0.28f + random.nextFloat() * 0.38f)

            val startX =
                targetX +
                    cos(angle) * distance

            val startY =
                targetY +
                    sin(angle) * distance

            /*
             * Perpendicular offset creates a natural curve.
             */
            val perpendicularX = -sin(angle)
            val perpendicularY = cos(angle)

            val curveAmount =
                maxSpan *
                    (-0.12f + random.nextFloat() * 0.24f)

            val midpointX =
                (startX + targetX) / 2f

            val midpointY =
                (startY + targetY) / 2f

            val controlX =
                midpointX +
                    perpendicularX * curveAmount

            val controlY =
                midpointY +
                    perpendicularY * curveAmount

            /*
             * Farther particles are generally smaller
             * and dimmer.
             */
            val distanceFactor =
                (
                    (distance / maxSpan - 0.28f) /
                        0.38f
                )
                    .coerceIn(0f, 1f)

            EmblemParticle(
                startX = startX,
                startY = startY,
                controlX = controlX,
                controlY = controlY,
                targetX = targetX,
                targetY = targetY,

                /*
                 * Stagger the particles.
                 */
                delay =
                    random.nextFloat() * 0.42f,

                /*
                 * Slight speed variation.
                 */
                duration =
                    0.48f +
                        random.nextFloat() * 0.24f,

                /*
                 * Small particles.
                 */
                radius =
                    (0.65f + random.nextFloat() * 1.8f) *
                        (1f - 0.35f * distanceFactor),

                alpha =
                    (0.35f + random.nextFloat() * 0.65f) *
                        (1f - 0.40f * distanceFactor)
            )
        }
}


/*
 * ==========================================================
 * EMBLEM PARTICLES
 * ==========================================================
 */

private fun DrawScope.drawEmblemParticles(
    particles: List<EmblemParticle>,
    progress: Float
) {

    if (progress <= 0f) return

    particles.forEach { particle ->

        val localProgress =
            (
                (progress - particle.delay) /
                    particle.duration
            ).coerceIn(0f, 1f)

        if (localProgress <= 0f) {
            return@forEach
        }

        /*
         * Smooth movement.
         */
        val t =
            FastOutSlowInEasing.transform(
                localProgress
            )

        /*
         * Quadratic Bezier:
         *
         * START
         *   |
         * CONTROL
         *   |
         * TARGET
         */
        val oneMinusT = 1f - t

        val x =
            oneMinusT * oneMinusT *
                particle.startX +
            2f * oneMinusT * t *
                particle.controlX +
            t * t *
                particle.targetX

        val y =
            oneMinusT * oneMinusT *
                particle.startY +
            2f * oneMinusT * t *
                particle.controlY +
            t * t *
                particle.targetY

        /*
         * Fade out near the destination.
         */
        val fadeOut =
            if (localProgress > 0.82f) {
                1f -
                    (
                        (localProgress - 0.82f) /
                            0.18f
                    )
            } else {
                1f
            }

        /*
         * Slightly smaller near the final position.
         */
        val radius =
            particle.radius.dp.toPx() *
                (1f - t * 0.35f)

        drawCircle(
            color = Color(
                red = 0.10f,
                green = 0.55f,
                blue = 1f,
                alpha =
                    (
                        particle.alpha *
                            fadeOut
                    ).coerceIn(0f, 1f)
            ),
            radius = radius,
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
