package ovh.verbois.retrohub.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import ovh.verbois.retrohub.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Slow decorative motion is capped at 24 fps and yields to navigation. */
private const val FRAME_MS = 1000L / 24

/** Broad translucent wave sheets, rasterized once and moved as alpha masks. */
private class Ribbon(val amplitude: Float, val seed: Float, val top: Float, val period: Float, val opacity: Float)

private val Ribbons =
    listOf(
        Ribbon(amplitude = 38f, seed = 0.4f, top = 0.34f, period = 80f, opacity = 1f),
        Ribbon(amplitude = 28f, seed = 2.1f, top = 0.56f, period = 120f, opacity = 0.65f),
    )

/** Ruban déjà dessiné, deux écrans de large : une image n’en copie qu’une partie. */
private class RibbonImage(val image: ImageBitmap, val band: Float, val ribbon: Ribbon)

/** PSP sky: cached soft waves with independently tinted console artwork. */
@Composable
fun AnimatedBackground(
    artworkUrl: String?,
    console: String?,
    accent: Color,
    animated: Boolean,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    paused: Boolean = false,
) {
    val scheme = Palette.scheme
    val geometry = remember { SkyGeometry() }
    var time by remember { mutableFloatStateOf(6f) }
    val moving = animated && !paused
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        // Animation « infinie » au sens de Compose : elle ne bloque ni l’inactivité ni les tests.
        val initial = time
        val start = withInfiniteAnimationFrameMillis { it }
        var last = start - FRAME_MS
        while (true) {
            withInfiniteAnimationFrameMillis { now ->
                if (now - last >= FRAME_MS) {
                    last = now
                    time = initial + (now - start) / 1000f
                }
            }
        }
    }
    Box(
        modifier.fillMaxSize().graphicsLayer { compositingStrategy = if (moving) CompositingStrategy.Auto else CompositingStrategy.Offscreen }.drawWithCache {
            val gradient = Brush.verticalGradient(0f to scheme.skyTop, 0.48f to scheme.skyMid, 1f to scheme.skyLow)
            val top = Brush.radialGradient(listOf(scheme.glowTop, Color.Transparent), Offset(size.width / 2, -size.height * 0.1f), size.width * 0.7f)
            onDrawBehind {
                drawRect(gradient)
                drawRect(top)
            }
        },
    ) {
        Crossfade(artworkUrl to console, animationSpec = tween(if (reducedMotion) 0 else 180), label = "backdrop") { (url, consoleId) ->
            val model = resolveArtwork(url)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (scheme.dark) 0.16f else 0.2f }) {
                if (model != null) {
                    AsyncImage(model, null, Modifier.fillMaxSize().blur(24.dp), contentScale = ContentScale.Crop)
                } else if (consoleId != null) {
                    Image(painterResource(artworkResource(consoleId)), null, Modifier.fillMaxSize().blur(12.dp), contentScale = ContentScale.Crop)
                }
            }
        }
        Box(
            Modifier.fillMaxSize().drawWithCache {
                geometry.prepare(size, this, layoutDirection)
                val ribbons = geometry.ribbons
                val radius = size.maxDimension * 0.42f
                val glowA = Brush.radialGradient(listOf(scheme.glowA, Color.Transparent), Offset.Zero, radius)
                val glowB = Brush.radialGradient(listOf(scheme.glowB, Color.Transparent), Offset.Zero, radius * 0.9f)
                val glowBottom = Brush.radialGradient(listOf(scheme.glowBottom, Color.Transparent), Offset.Zero, radius)
                val glowAccent = Brush.radialGradient(listOf(accent.copy(alpha = 0.12f), Color.Transparent), Offset.Zero, radius * 0.7f)
                val white = ColorFilter.tint(Color.White, BlendMode.SrcIn)
                onDrawBehind {
                    val t = time
                    // Halos : dégradés calculés une fois, seulement déplacés (aller-retour lent, comme rh-glow).
                    val dx = size.width * 0.03f * sin(t * 2 * PI.toFloat() / 52f)
                    val dy = size.height * 0.03f * sin(t * 2 * PI.toFloat() / 40f)
                    translate(size.width * 0.22f + dx, size.height * 0.30f + dy) { drawCircle(glowA, radius, Offset.Zero) }
                    translate(size.width * 0.78f - dx, size.height * 0.62f - dy) {
                        drawCircle(glowB, radius * 0.9f, Offset.Zero)
                        drawCircle(glowAccent, radius * 0.7f, Offset.Zero)
                    }
                    translate(size.width * 0.5f, size.height) { drawCircle(glowBottom, radius, Offset.Zero) }
                    ribbons.forEach { drawRibbon(it, t, white) }
                }
            },
        )
    }
}

/** Accent and theme changes must not rerasterize geometry or allocate new mask bitmaps. */
private class SkyGeometry {
    private var key: Triple<Size, Float, LayoutDirection>? = null
    var ribbons: List<RibbonImage> = emptyList()
        private set

    fun prepare(size: Size, density: Density, direction: LayoutDirection) {
        val next = Triple(size, density.density, direction)
        if (next == key) return
        ribbons = Ribbons.map { prerender(it, size, density, direction) }
        key = next
    }
}

/** Dessine un ruban une fois, en masque alpha (8 bits par pixel : peu de mémoire). */
private fun prerender(ribbon: Ribbon, size: Size, density: Density, direction: LayoutDirection): RibbonImage {
    val width = size.width.coerceAtLeast(1f)
    val band = maxOf(size.height * 0.34f, with(density) { 220.dp.toPx() })
    val image = ImageBitmap((width * 2).roundToInt(), (band * 1.4f).roundToInt(), ImageBitmapConfig.Alpha8)
    val scale = band / 300f
    val base = image.height / 2f
    val step = with(density) { 6.dp.toPx() }
    CanvasDrawScope().draw(density, direction, Canvas(image), Size(image.width.toFloat(), image.height.toFloat())) {
        val edge = Path()
        var x = 0f
        while (x <= image.width + step) {
            val u = x / width * 2 * PI.toFloat()
            val y = base + (ribbon.amplitude * sin(u + ribbon.seed) + ribbon.amplitude * 0.35f * sin(2 * u + ribbon.seed * 2)) * scale
            if (x == 0f) edge.moveTo(x, y) else edge.lineTo(x, y)
            x += step
        }
        val sheet = Path().apply {
            addPath(edge)
            lineTo(image.width.toFloat(), image.height.toFloat())
            lineTo(0f, image.height.toFloat())
            close()
        }
        drawPath(
            sheet,
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.35f to Color.White.copy(alpha = 0.06f * ribbon.opacity),
                0.65f to Color.White.copy(alpha = 0.16f * ribbon.opacity),
                1f to Color.Transparent,
            ),
        )
        for ((widthDp, alpha) in listOf(12f to 0.025f, 6f to 0.04f, 2f to 0.09f)) {
            drawPath(edge, Color.White.copy(alpha = alpha * ribbon.opacity), style = Stroke(with(density) { widthDp.dp.toPx() }))
        }
    }
    return RibbonImage(image, band, ribbon)
}

private fun DrawScope.drawRibbon(ribbon: RibbonImage, t: Float, white: ColorFilter) {
    val width = size.width.roundToInt()
    val shift = ((((t / ribbon.ribbon.period) % 1f) + 1f) % 1f * width).roundToInt().coerceIn(0, width)
    val bob = sin(t / 11f * PI.toFloat()) * ribbon.band * 0.06f
    val top = size.height * ribbon.ribbon.top + ribbon.band / 2 - ribbon.image.height / 2f + bob
    drawImage(
        ribbon.image,
        srcOffset = IntOffset(shift, 0),
        srcSize = IntSize(width, ribbon.image.height),
        dstOffset = IntOffset(0, top.roundToInt()),
        dstSize = IntSize(width, ribbon.image.height),
        colorFilter = white,
    )
}
