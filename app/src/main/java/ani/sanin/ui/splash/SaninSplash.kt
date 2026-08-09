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
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * ==========================================================
 * SHARED SANIN SPLASH
 * ==========================================================
 *
 * One shared particle materialization engine drives both
 * orientations. Each orientation only supplies its own
 * configuration (assets + logo geometry):
 *
 *   Shared ParticleMaterializationEngine
 *         |
 *         ├── Landscape configuration
 *         |
 *         └── Portrait configuration
 *
 * The engine handles alpha-pixel sampling, particle creation
 * with far-away curved Bezier trajectories, drawing, and the
 * synchronized logo shine. It never animates logo geometry.
 */

internal data class SaninSplashGeometry(
    val wordmarkOffsetY: Dp,
    val emblemOffsetY: Dp,
    val emblemWidth: Dp,
    val emblemHeight: Dp
)

internal sealed interface SaninSplashConfig {
    val backgroundRes: Int
    val wordmarkRes: Int
    val emblemRes: Int
    val particleCount: Int
}

/*
 * Fixed geometry - used by landscape so its exact current
 * layout is preserved.
 */
internal data class FixedSaninSplashConfig(
    override val backgroundRes: Int,
    override val wordmarkRes: Int,
    override val emblemRes: Int,
    val geometry: SaninSplashGeometry,
    override val particleCount: Int = 220
) : SaninSplashConfig

/*
 * Canvas-relative geometry - used by portrait. Logo centers
 * are fractions of the portrait canvas height and the emblem
 * particle field is sized from the emblem's actual pixels, so
 * particles always land exactly on the drawn logo.
 */
internal data class CanvasSaninSplashConfig(
    override val backgroundRes: Int,
    override val wordmarkRes: Int,
    override val emblemRes: Int,
    val wordmarkCenterY: Float,
    val emblemCenterY: Float,
    override val particleCount: Int = 220
) : SaninSplashConfig

/*
 * Landscape configuration - identical to the previous
 * dedicated landscape splash.
 */
private val LandscapeConfig = FixedSaninSplashConfig(
    backgroundRes = R.drawable.sanin_splash_background,
    wordmarkRes = R.drawable.sanin_wordmark,
    emblemRes = R.drawable.sanin_emblem,
    geometry = SaninSplashGeometry(
        wordmarkOffsetY = (-65).dp,
        emblemOffsetY = 90.dp,
        emblemWidth = 230.dp,
        emblemHeight = 310.dp
    )
)

/*
 * Portrait configuration - uses the new portrait assets.
 */
private val PortraitConfig = CanvasSaninSplashConfig(
    backgroundRes = R.drawable.sanin_splash_background_portrait,
    wordmarkRes = R.drawable.sanin_wordmark_portrait,
    emblemRes = R.drawable.sanin_emblem_portrait,
    wordmarkCenterY = 0.42f,
    emblemCenterY = 0.58f
)

@Composable
fun SaninLandscapeSplash(
    onFinished: () -> Unit
) {
    SaninSplash(
        config = LandscapeConfig,
        onFinished = onFinished
    )
}

@Composable
fun SaninPortraitSplash(
    onFinished: () -> Unit
) {
    SaninSplash(
        config = PortraitConfig,
        onFinished = onFinished
    )
}


/*
 * ==========================================================
 * SHARED MATERIALIZATION ENGINE
 * ==========================================================
 */

@Composable
internal fun SaninSplash(
    config: SaninSplashConfig,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val emblemBitmap = remember {
        BitmapFactory.decodeResource(
            context.resources,
            config.emblemRes
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
     * The natural blue corner elements are part of it.
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
        modifier = Modifier
            .fillMaxSize()
            // Opaque black backdrop: the background artwork
            // fades in from black, never revealing the app
            // content behind the splash.
            .background(Color.Black)
    ) {

        /*
         * Resolve the orientation-specific logo geometry.
         */
        val geometry = when (config) {
            is FixedSaninSplashConfig -> config.geometry
            is CanvasSaninSplashConfig -> with(density) {
                SaninSplashGeometry(
                    wordmarkOffsetY = maxHeight * (config.wordmarkCenterY - 0.5f),
                    emblemOffsetY = maxHeight * (config.emblemCenterY - 0.5f),
                    emblemWidth = emblemBitmap.width.toDp(),
                    emblemHeight = emblemBitmap.height.toDp()
                )
            }
        }

        /*
         * Emblem particles are generated once per layout.
         * They spawn far away and travel along curved
         * Bezier paths to their exact emblem pixel.
         */
        val emblemParticles = remember(emblemBitmap, maxWidth, maxHeight, geometry) {
            with(density) {
                val width = maxWidth.toPx()
                val height = maxHeight.toPx()
                createEmblemParticles(
                    bitmap = emblemBitmap,
                    screenWidth = width,
                    screenHeight = height,
                    emblemCenterX = width / 2f,
                    emblemCenterY = height / 2f + geometry.emblemOffsetY.toPx(),
                    emblemWidth = geometry.emblemWidth.toPx(),
                    emblemHeight = geometry.emblemHeight.toPx(),
                    count = config.particleCount
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
                config.backgroundRes
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
                config.wordmarkRes
            ),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = geometry.wordmarkOffsetY)
                .alpha(wordmarkAlpha),
            contentScale = ContentScale.None
        )

        /*
         * EMBLEM
         */

        Image(
            painter = painterResource(
                config.emblemRes
            ),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = geometry.emblemOffsetY)
                .alpha(emblemAlpha),
            contentScale = ContentScale.None
        )

        /*
         * ==================================================
         * EMBLEM MATERIALIZATION
         * ==================================================
         *
         * Particles are sampled from the actual alpha pixels
         * of the emblem so they form the real emblem
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
                progress = shineProgress,
                config = config,
                geometry = geometry
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
    progress: Float,
    config: SaninSplashConfig,
    geometry: SaninSplashGeometry
) {

    /*
     * Ping-pong sweep: left -> right -> left within the
     * same shine window, so the light passes twice.
     */
    val shineX =
        if (progress < 0.5f) {
            lerp(
                0.25f,
                0.75f,
                progress / 0.5f
            )
        } else {
            lerp(
                0.75f,
                0.25f,
                (progress - 0.5f) / 0.5f
            )
        }

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

        ShineLayer(
            logoRes = config.wordmarkRes,
            offsetY = geometry.wordmarkOffsetY,
            shineX = shineX,
            shineWidth = shineWidth,
            intensity = intensity
        )

        /*
         * --------------------------------------------------
         * EMBLEM SHINE
         * --------------------------------------------------
         */

        ShineLayer(
            logoRes = config.emblemRes,
            offsetY = geometry.emblemOffsetY,
            shineX = shineX,
            shineWidth = shineWidth,
            intensity = intensity
        )
    }
}


/*
 * ==========================================================
 * LOGO SHINE LAYER
 * ==========================================================
 *
 * One logo layer with the shine band and sparkles masked to
 * the actual PNG alpha (SrcIn), so the light is only ever
 * visible on the real logo pixels.
 */

@Composable
private fun ShineLayer(
    logoRes: Int,
    offsetY: Dp,
    shineX: Float,
    shineWidth: Float,
    intensity: Float
) {

    Image(
        painter = painterResource(
            logoRes
        ),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.Center)
            .offset(y = offsetY)
            .graphicsLayer {
                /*
                 * CRITICAL:
                 * No scale transformation.
                 *
                 * The PNG remains exactly the same size.
                 */

                alpha = intensity

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
                                0.78f,
                                0.96f,
                                1f,
                                1f
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

                /*
                 * Sparkles riding the band, clipped to
                 * the same PNG alpha.
                 */

                drawContext.canvas.saveLayer(
                    Rect(Offset.Zero, size),
                    Paint()
                )

                drawContent()

                drawLogoSparkles(
                    bandX = shineX * size.width,
                    logoWidth = size.width,
                    logoHeight = size.height,
                    intensity = intensity
                )

                drawContext.canvas.restore()
            },
        contentScale = ContentScale.None
    )
}


/*
 * ==========================================================
 * LOGO SPARKLES
 * ==========================================================
 *
 * Small bright cross glints that ride along the shine band.
 * Every sparkle draw uses SrcIn so it is clipped to the
 * actual PNG alpha and never appears outside the logo.
 */

private fun DrawScope.drawLogoSparkles(
    bandX: Float,
    logoWidth: Float,
    logoHeight: Float,
    intensity: Float
) {

    if (intensity <= 0f) return

    /*
     * A few glints offset around the band, spread
     * vertically across the logo.
     */
    val sparkles = listOf(
        0.00f to 0.28f,
        -0.030f to 0.52f,
        0.025f to 0.72f,
        -0.012f to 0.16f
    )

    sparkles.forEach { (xOffset, yFactor) ->

        val cx =
            bandX +
                xOffset * logoWidth

        val cy =
            yFactor * logoHeight

        /*
         * Sparkle size scales with the logo.
         */
        val armRadius =
            logoWidth * 0.018f

        val strokeWidth =
            logoWidth * 0.0035f

        val alpha =
            (intensity * 0.9f)
                .coerceIn(0f, 1f)

        drawLine(
            color = Color(
                0.90f,
                0.98f,
                1f,
                alpha
            ),
            start = Offset(
                cx - armRadius,
                cy
            ),
            end = Offset(
                cx + armRadius,
                cy
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
            blendMode = BlendMode.SrcIn
        )

        drawLine(
            color = Color(
                0.90f,
                0.98f,
                1f,
                alpha
            ),
            start = Offset(
                cx,
                cy - armRadius
            ),
            end = Offset(
                cx,
                cy + armRadius
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
            blendMode = BlendMode.SrcIn
        )

        /*
         * Small bright core.
         */
        drawCircle(
            color = Color(
                1f,
                1f,
                1f,
                alpha
            ),
            radius = strokeWidth * 0.9f,
            center = Offset(cx, cy),
            blendMode = BlendMode.SrcIn
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
