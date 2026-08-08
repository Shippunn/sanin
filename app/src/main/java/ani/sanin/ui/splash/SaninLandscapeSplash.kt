package ani.sanin.ui.splash

import ani.sanin.R
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class SplashParticle(
    val startX: Float,
    val startY: Float,
    val targetX: Float,
    val targetY: Float,
    val size: Float,
    val alpha: Float,
    val startDelay: Float,
    val duration: Float
)

@Composable
fun SaninLandscapeSplash(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    val context = LocalContext.current

    val background = remember {
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.sanin_splash_background
        ).asImageBitmap()
    }

    val emblem = remember {
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.sanin_emblem
        )
    }

    /*
     * Sample the emblem once.
     * These points become the final destinations
     * of the blue particles.
     */
    val targetPoints = remember(emblem) {
        sampleEmblemPoints(
            bitmap = emblem,
            maxPoints = 130
        )
    }

    var progress = remember {
        Animatable(0f)
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 2300,
                easing = LinearEasing
            )
        )

        delay(300)
        onFinished()
    }

    val animationProgress = progress.value

    /*
     * Small breathing animation for the blue artwork.
     */
    val infinite = rememberInfiniteTransition(
        label = "saninGlow"
    )

    val glowPulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val backgroundAlpha =
        ((animationProgress - 0.03f) / 0.35f)
            .coerceIn(0f, 1f)

    val wordmarkAlpha =
        ((animationProgress - 0.16f) / 0.32f)
            .coerceIn(0f, 1f)

    val emblemAlpha =
        ((animationProgress - 0.32f) / 0.58f)
            .coerceIn(0f, 1f)

    val particleProgress =
        ((animationProgress - 0.30f) / 0.58f)
            .coerceIn(0f, 1f)

    Box(
        modifier = modifier.fillMaxSize()
    ) {

        /*
         * --------------------------------------------------
         * BACKGROUND
         * --------------------------------------------------
         */

        Image(
            bitmap = background,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(
                    backgroundAlpha * glowPulse
                )
        )

        /*
         * --------------------------------------------------
         * SUBTLE CORNER GLOW
         * --------------------------------------------------
         */

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawCornerGlow(
                topLeft = true,
                intensity = backgroundAlpha
            )

            drawCornerGlow(
                topLeft = false,
                intensity = backgroundAlpha
            )
        }

        /*
         * --------------------------------------------------
         * WORDMARK
         * --------------------------------------------------
         */

        Image(
            painter = painterResource(
                R.drawable.sanin_wordmark
            ),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(wordmarkAlpha)
        )

        /*
         * --------------------------------------------------
         * EMBLEM PARTICLES
         * --------------------------------------------------
         */

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawParticles(
                points = targetPoints,
                progress = particleProgress
            )
        }

        /*
         * --------------------------------------------------
         * REAL EMBLEM
         * --------------------------------------------------
         */

        Image(
            bitmap = emblem.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(emblemAlpha)
        )

        /*
         * --------------------------------------------------
         * FINAL BLUE PULSE
         * --------------------------------------------------
         */

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val pulseProgress =
                ((animationProgress - 0.68f) / 0.20f)
                    .coerceIn(0f, 1f)

            if (pulseProgress > 0f && pulseProgress < 1f) {
                drawEmblemPulse(
                    intensity =
                        sin(
                            pulseProgress *
                                Math.PI
                        ).toFloat()
                )
            }
        }
    }
}


/*
 * ==========================================================
 * EMBLEM POINT SAMPLING
 * ==========================================================
 */

private fun sampleEmblemPoints(
    bitmap: android.graphics.Bitmap,
    maxPoints: Int
): List<Offset> {

    val points = mutableListOf<Offset>()

    val width = bitmap.width
    val height = bitmap.height

    val step =
        kotlin.math.sqrt(
            (width * height).toFloat() /
                maxPoints
        )
            .toInt()
            .coerceAtLeast(2)

    for (y in 0 until height step step) {

        for (x in 0 until width step step) {

            val pixel =
                bitmap.getPixel(x, y)

            val alpha =
                android.graphics.Color.alpha(pixel)

            if (alpha > 45) {

                /*
                 * Normalize the emblem coordinates.
                 */

                val normalizedX =
                    x.toFloat() /
                        width.toFloat()

                val normalizedY =
                    y.toFloat() /
                        height.toFloat()

                points += Offset(
                    normalizedX,
                    normalizedY
                )
            }
        }
    }

    /*
     * Limit the number of particles.
     */

    return points
        .shuffled(Random(42))
        .take(maxPoints)
}


/*
 * ==========================================================
 * PARTICLE DRAWING
 * ==========================================================
 */

private fun DrawScope.drawParticles(
    points: List<Offset>,
    progress: Float
) {

    if (progress <= 0f) return

    val centerX = size.width / 2f
    val centerY = size.height / 2f

    /*
     * The actual emblem is 230x310.
     * Convert its pixel-space proportions
     * to the screen.
     */

    val emblemWidth =
        230.dp.toPx()

    val emblemHeight =
        310.dp.toPx()

    points.forEachIndexed { index, target ->

        /*
         * Give every particle a deterministic
         * individual delay.
         */

        val delay =
            (index % 17) / 17f * 0.35f

        val local =
            (
                progress - delay
            )
                .coerceIn(0f, 1f)

        val eased =
            FastOutSlowInEasing.transform(
                local
            )

        /*
         * Target position inside the emblem.
         */

        val targetX =
            centerX -
                emblemWidth / 2f +
                target.x * emblemWidth

        val targetY =
            centerY -
                emblemHeight / 2f +
                target.y * emblemHeight +
                55.dp.toPx()

        /*
         * Start particles in a loose ring
         * surrounding the emblem.
         */

        val angle =
            (index * 137.5f) *
                Math.PI /
                180.0

        val startDistance =
            150.dp.toPx() +
                (index % 5) *
                24.dp.toPx()

        val startX =
            centerX +
                cos(angle).toFloat() *
                startDistance

        val startY =
            centerY +
                sin(angle).toFloat() *
                startDistance

        val x =
            startX +
                (targetX - startX) *
                eased

        val y =
            startY +
                (targetY - startY) *
                eased

        /*
         * Particles fade as they reach their
         * final position.
         */

        val alpha =
            (1f - eased)
                .coerceIn(0f, 1f) *
                0.85f

        val size =
            (1.3f + (index % 4) * 0.55f)
                .dp
                .toPx()

        drawCircle(
            color = Color(
                red = 0.08f,
                green = 0.48f,
                blue = 1f,
                alpha = alpha
            ),
            radius = size,
            center = Offset(x, y)
        )
    }
}


/*
 * ==========================================================
 * CORNER GLOW
 * ==========================================================
 */

private fun DrawScope.drawCornerGlow(
    topLeft: Boolean,
    intensity: Float
) {

    val center =
        if (topLeft) {
            Offset(
                x = size.width * 0.06f,
                y = size.height * 0.10f
            )
        } else {
            Offset(
                x = size.width * 0.94f,
                y = size.height * 0.90f
            )
        }

    val radius =
        size.width * 0.22f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(
                    red = 0.03f,
                    green = 0.35f,
                    blue = 1f,
                    alpha = 0.08f * intensity
                ),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}


/*
 * ==========================================================
 * FINAL EMBLEM PULSE
 * ==========================================================
 */

private fun DrawScope.drawEmblemPulse(
    intensity: Float
) {

    val center =
        Offset(
            x = size.width / 2f,
            y = size.height / 2f +
                55.dp.toPx()
        )

    val radius =
        size.width * 0.13f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(
                    red = 0.03f,
                    green = 0.40f,
                    blue = 1f,
                    alpha = 0.18f * intensity
                ),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
