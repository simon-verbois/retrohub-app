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
import androidx.compose.ui.draw.drawWithContent
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
import ovh.verbois.retrohub.ui.theme.SkyScheme
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Le fond ne se redessine que 24 fois par seconde, comme le ciel du web : il bouge lentement. */
private const val FRAME_MS = 1000L / 24

private class Star(val x: Float, val y: Float, val radius: Float, val period: Float, val delay: Float)

/** Un ruban de fines lignes ondulées, comme le ciel du web (shell.tsx). */
private class Ribbon(val lines: Int, val amplitude: Float, val seed: Float, val top: Float, val period: Float, val opacity: Float)

private val Ribbons =
    listOf(
        Ribbon(lines = 16, amplitude = 46f, seed = 0.4f, top = 0.40f, period = 80f, opacity = 1f),
        Ribbon(lines = 12, amplitude = 34f, seed = 2.1f, top = 0.66f, period = 120f, opacity = 0.6f),
    )

private val Stars =
    Random(42).let { r -> List(30) { Star(r.nextFloat(), r.nextFloat(), 0.8f + r.nextFloat() * 1.8f, 12f + r.nextFloat() * 16f, r.nextFloat() * 20f) } }

/** Ruban déjà dessiné, deux écrans de large : une image n’en copie qu’une partie. */
private class RibbonImage(val image: ImageBitmap, val band: Float, val ribbon: Ribbon)

/**
 * Le ciel de RetroHub : dégradé bleu, halos qui dérivent, rubans de lignes et particules, avec
 * l’illustration du jeu choisi. Optimisé comme le web : 24 images par seconde au plus, rubans et
 * particules dessinés une fois puis seulement déplacés, aucun flou animé. Figé quand les
 * animations sont réduites ; Android cesse de le dessiner dès que l’application est cachée.
 *
 * [hero] : l’illustration est bien visible à droite, fondue dans le ciel vers la gauche et le bas
 * (accueil façon console) ; sinon elle reste un voile flou.
 */
@Composable
fun AnimatedBackground(artworkUrl: String?, console: String?, accent: Color, animated: Boolean, modifier: Modifier = Modifier, hero: Boolean = false) {
    val scheme = Palette.scheme
    var time by remember { mutableFloatStateOf(6f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        // Animation « infinie » au sens de Compose : elle ne bloque ni l’inactivité ni les tests.
        val start = withInfiniteAnimationFrameMillis { it }
        var last = start - FRAME_MS
        while (true) {
            withInfiniteAnimationFrameMillis { now ->
                if (now - last >= FRAME_MS) {
                    last = now
                    time = 6f + (now - start) / 1000f
                }
            }
        }
    }
    Box(
        modifier.fillMaxSize().drawWithCache {
            val gradient = Brush.verticalGradient(0f to scheme.skyTop, 0.48f to scheme.skyMid, 1f to scheme.skyLow)
            val top = Brush.radialGradient(listOf(scheme.glowTop, Color.Transparent), Offset(size.width / 2, -size.height * 0.1f), size.width * 0.7f)
            onDrawBehind {
                drawRect(gradient)
                drawRect(top)
            }
        },
    ) {
        Crossfade(Triple(artworkUrl, console, hero), animationSpec = tween(650), label = "backdrop") { (url, consoleId, big) ->
            val model = resolveArtwork(url)
            if (big && model != null) {
                HeroArt(model, scheme)
            } else {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (scheme.dark) 0.16f else 0.2f }) {
                    if (model != null) {
                        AsyncImage(model, null, Modifier.fillMaxSize().blur(24.dp), contentScale = ContentScale.Crop)
                    } else if (consoleId != null) {
                        Image(painterResource(artworkResource(consoleId)), null, Modifier.fillMaxSize().blur(12.dp), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val ribbons = Ribbons.map { prerender(it, size, this, layoutDirection) }
                val sprite = starSprite(this, layoutDirection)
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
                    drawStars(sprite, t)
                }
            },
        )
    }
}

/** Illustration à la manière d’une console : nette, à droite, fondue dans le ciel. */
@Composable
private fun HeroArt(model: Any, scheme: SkyScheme) {
    AsyncImage(
        model,
        null,
        Modifier.fillMaxSize()
            .graphicsLayer {
                alpha = if (scheme.dark) 0.5f else 0.6f
                compositingStrategy = CompositingStrategy.Offscreen
            }.drawWithContent {
                drawContent()
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.3f to Color.Black.copy(alpha = 0.45f), 0.65f to Color.Black),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(Brush.verticalGradient(0.5f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            },
        contentScale = ContentScale.Crop,
    )
}

/** Dessine un ruban une fois, en masque alpha (8 bits par pixel : peu de mémoire). */
private fun prerender(ribbon: Ribbon, size: Size, density: Density, direction: LayoutDirection): RibbonImage {
    val width = size.width.coerceAtLeast(1f)
    val band = maxOf(size.height * 0.34f, with(density) { 220.dp.toPx() })
    val image = ImageBitmap((width * 2).roundToInt(), (band * 1.4f).roundToInt(), ImageBitmapConfig.Alpha8)
    val scale = band / 300f
    val base = image.height / 2f
    val step = with(density) { 6.dp.toPx() }
    val stroke = Stroke(with(density) { 1.2.dp.toPx() })
    CanvasDrawScope().draw(density, direction, Canvas(image), Size(image.width.toFloat(), image.height.toFloat())) {
        for (k in 0 until ribbon.lines) {
            val path = Path()
            var x = 0f
            while (x <= image.width + step) {
                val u = x / width * 2 * PI.toFloat()
                val y =
                    (ribbon.amplitude + k * 2.4f) * sin(u + ribbon.seed + k * 0.09f) +
                        ribbon.amplitude * 0.35f * sin(2 * u + ribbon.seed * 2 + k * 0.16f)
                if (x == 0f) path.moveTo(x, base + y * scale) else path.lineTo(x, base + y * scale)
                x += step
            }
            drawPath(path, Color.White.copy(alpha = (0.08f + k.toFloat() / ribbon.lines * 0.32f) * ribbon.opacity), style = stroke)
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

/** Particule : un point lumineux dessiné une fois, puis copié à chaque image. */
private fun starSprite(density: Density, direction: LayoutDirection): ImageBitmap {
    val side = 32
    val image = ImageBitmap(side, side)
    CanvasDrawScope().draw(density, direction, Canvas(image), Size(side.toFloat(), side.toFloat())) {
        drawCircle(
            Brush.radialGradient(
                0f to Color.White,
                0.3f to Color.White.copy(alpha = 0.9f),
                0.45f to Color(0x66BED7FF),
                1f to Color.Transparent,
                center = Offset(side / 2f, side / 2f),
                radius = side / 2f,
            ),
        )
    }
    return image
}

private fun DrawScope.drawStars(sprite: ImageBitmap, t: Float) {
    for (star in Stars) {
        // Une particule naît, monte d’environ 100 px en dérivant, scintille puis s’éteint (rh-float).
        val p = ((t + star.delay) % star.period) / star.period
        val alpha =
            when {
                p < 0.25f -> p / 0.25f * 0.75f
                p < 0.5f -> 0.75f - (p - 0.25f) / 0.25f * 0.5f
                p < 0.75f -> 0.25f + (p - 0.5f) / 0.25f * 0.4f
                else -> 0.65f * (1 - (p - 0.75f) / 0.25f)
            }
        val drift = 12.dp.toPx() * sin(p * PI.toFloat())
        val rise = (20f - 100f * p) * density
        val side = (star.radius * density * 6f * (0.6f + 0.4f * sin(p * PI.toFloat()))).roundToInt().coerceAtLeast(2)
        val x = (star.x * size.width + drift - side / 2f).roundToInt()
        val y = (star.y * size.height + rise - side / 2f).roundToInt()
        drawImage(sprite, dstOffset = IntOffset(x, y), dstSize = IntSize(side, side), alpha = alpha.coerceIn(0f, 1f))
    }
}
