package ovh.verbois.retrohub.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.verbois.retrohub.emulation.ControlPlacement
import ovh.verbois.retrohub.emulation.ControlsPlacement
import ovh.verbois.retrohub.emulation.PadKey
import ovh.verbois.retrohub.emulation.Stick
import ovh.verbois.retrohub.ui.components.consoleButtonSurface
import ovh.verbois.retrohub.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private sealed interface Hit {
    data class Key(val key: PadKey) : Hit

    data object DPad : Hit

    data class Analog(val stick: Stick) : Hit

    data class StickKey(val button: StickButton) : Hit
}

/** Zones touchables enregistrées par chaque commande, en coordonnées de la racine. */
private class Regions {
    val items = mutableMapOf<String, Pair<Rect, Hit>>()

    fun register(id: String, coordinates: LayoutCoordinates, hit: Hit) {
        items[id] = coordinates.boundsInRoot() to hit
    }

    /** Le plus proche des boutons sous le doigt (zones un peu élargies pour pardonner). */
    fun hit(point: Offset, slop: Float): Pair<Rect, Hit>? = items.values
        .filter { (rect, _) -> rect.inflate(slop).contains(point) }
        .minByOrNull { (rect, _) -> (rect.center - point).getDistanceSquared() }
}

/** État affiché des commandes : boutons enfoncés, croix et stick. */
private class Pressed {
    var keys by mutableStateOf(emptySet<PadKey>())
    var stick by mutableStateOf(Offset.Zero)
    var rightStick by mutableStateOf(Offset.Zero)
    var stickButtons by mutableStateOf(emptySet<StickButton>())
}

/**
 * Commandes tactiles d’une console. Un seul gestionnaire suit tous les doigts : on glisse d’un
 * bouton à l’autre, on appuie sur plusieurs à la fois ; un doigt posé hors des commandes touche
 * l’image (écran tactile de la DS).
 */
@Composable
fun TouchControls(
    spec: PadSpec,
    placement: ControlsPlacement,
    opacity: Float,
    scale: Float,
    onKey: (PadKey, Boolean) -> Unit,
    onStick: (Stick, Float, Float) -> Unit,
    onScreenTouch: (Offset?) -> Unit,
    onHaptic: () -> Unit,
    modifier: Modifier = Modifier,
    /** Bas de l’image du jeu (0..1) : en portrait, les commandes occupent tout l’espace dessous. */
    gameBottom: Float = 0.58f,
    /** Bord gauche de l’image du jeu (0..1), pour les marges latérales de la DS côte à côte. */
    gameLeft: Float = 0.2f,
    /** Déplacements et tailles choisis par le joueur, par groupe de commandes. */
    layout: Map<String, ControlPlacement> = emptyMap(),
    /** Mode édition : on déplace (un doigt) et on redimensionne (deux doigts) les groupes. */
    editing: Boolean = false,
    onLayout: (String, ControlPlacement) -> Unit = { _, _ -> },
    physicalInput: ovh.verbois.retrohub.game.ControllerInput? = null,
) {
    val currentLayout by rememberUpdatedState(layout)
    val currentScreenTouch by rememberUpdatedState(onScreenTouch)
    val regions = remember { Regions() }
    val pressed = remember { Pressed() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val baseUnit = 48.dp * scale
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(spec, editing) {
                if (editing || physicalInput != null) return@pointerInput
                awaitPointerEventScope {
                    val stickOwner = mutableMapOf<PointerId, Hit>()
                    var screenPointer: PointerId? = null
                    val stickCenters = mutableMapOf<Stick, Pair<Offset, Float>>()
                    val slop = 10.dp.toPx()
                    while (true) {
                        val event = awaitPointerEvent()
                        val keys = mutableSetOf<PadKey>()
                        val cKeys = mutableSetOf<StickButton>()
                        var stick: Offset? = null
                        var rightStick: Offset? = null
                        var screen: Offset? = null
                        for (change in event.changes) {
                            if (!change.pressed) {
                                stickOwner.remove(change.id)
                                if (screenPointer == change.id) screenPointer = null
                                continue
                            }
                            if (change.isConsumed) continue
                            val point = change.position + origin
                            val heldStick = stickOwner[change.id] as? Hit.Analog
                            if (heldStick != null) {
                                val (center, radius) = stickCenters.getValue(heldStick.stick)
                                val v = (point - center) / radius
                                val len = hypot(v.x, v.y).coerceAtLeast(1f)
                                val value = Offset(v.x / len, v.y / len)
                                if (heldStick.stick == Stick.LEFT) stick = value else rightStick = value
                                change.consume()
                                continue
                            }
                            if (screenPointer == change.id) {
                                screen = change.position
                                continue
                            }
                            val found = regions.hit(point, slop)
                            if (found == null) {
                                if (change.previousPressed.not() && screenPointer == null) {
                                    screenPointer = change.id
                                    screen = change.position
                                }
                                continue
                            }
                            val (rect, hit) = found
                            change.consume()
                            when (hit) {
                                is Hit.Key -> keys += hit.key

                                is Hit.StickKey -> cKeys += hit.button

                                Hit.DPad -> keys += dpadKeys((point - rect.center) / (rect.width / 2))

                                is Hit.Analog -> {
                                    if (!change.previousPressed && stickOwner.values.none { it == hit }) {
                                        stickOwner[change.id] = hit
                                        stickCenters[hit.stick] = rect.center to rect.width / 2
                                        val value = (point - rect.center) / (rect.width / 2)
                                        if (hit.stick == Stick.LEFT) stick = value else rightStick = value
                                    }
                                }
                            }
                        }
                        val previous = pressed.keys
                        (keys - previous).forEach { onKey(it, true) }
                        (previous - keys).forEach { onKey(it, false) }
                        if ((keys - previous).isNotEmpty() || (cKeys - pressed.stickButtons).isNotEmpty()) onHaptic()
                        pressed.keys = keys
                        if (cKeys != pressed.stickButtons) {
                            val x = cKeys.sumOf { it.dx.toDouble() }.toFloat().coerceIn(-1f, 1f)
                            val y = cKeys.sumOf { it.dy.toDouble() }.toFloat().coerceIn(-1f, 1f)
                            onStick(Stick.RIGHT, x, y)
                            pressed.stickButtons = cKeys
                        }
                        if (spec.rightStick) {
                            val value = rightStick ?: Offset.Zero
                            if (value != pressed.rightStick) {
                                pressed.rightStick = value
                                onStick(Stick.RIGHT, value.x, value.y)
                            }
                        }
                        val newStick = stick ?: Offset.Zero
                        if (newStick != pressed.stick) {
                            pressed.stick = newStick
                            onStick(Stick.LEFT, newStick.x, newStick.y)
                        }
                        currentScreenTouch(screen)
                    }
                }
            }.graphicsLayer { alpha = opacity },
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val editLine = Palette.Warning
        fun Modifier.group(id: String): Modifier {
            val place = layout[id] ?: ControlPlacement()
            val moved =
                offset { IntOffset((place.dx * widthPx).roundToInt(), (place.dy * heightPx).roundToInt()) }
                    .graphicsLayer {
                        scaleX = place.scale
                        scaleY = place.scale
                    }
            if (!editing) return this.then(moved)
            return this.then(moved)
                .border(2.dp, editLine, RoundedCornerShape(18.dp))
                .pointerInput(id) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val now = currentLayout[id] ?: ControlPlacement()
                        onLayout(
                            id,
                            ControlPlacement(
                                dx = (now.dx + pan.x * now.scale / widthPx).coerceIn(-0.5f, 0.5f),
                                dy = (now.dy + pan.y * now.scale / heightPx).coerceIn(-0.5f, 0.5f),
                                scale = (now.scale * zoom).coerceIn(0.6f, 1.8f),
                            ),
                        )
                    }
                }
        }
        val landscape = maxWidth > maxHeight
        val side = if (landscape) maxWidth * gameLeft else maxWidth
        val controlsHeight = if (landscape) maxHeight else maxHeight * (1f - gameBottom)
        val band = maxHeight * (1f - gameBottom)
        // DS côte à côte : les commandes tiennent dans la bande sous les écrans.
        val unit = when (placement) {
            ControlsPlacement.BOTTOM_CORNERS -> minOf(baseUnit, (band - 12.dp) / 2.95f)
            ControlsPlacement.SIDES -> minOf(baseUnit, (side - 12.dp) / 2.95f, (maxHeight - 112.dp) / 3.7f)
            ControlsPlacement.OVERLAY -> minOf(baseUnit, (maxWidth - 40.dp) / 6f, (maxHeight - 84.dp) / 3.7f)
            else -> minOf(baseUnit, (maxWidth - 48.dp) / 6f)
        }.coerceAtLeast(24.dp)
        val shownKeys = physicalInput?.keys ?: pressed.keys
        val shownStick = physicalInput?.let { Offset(it.leftX, it.leftY) } ?: pressed.stick
        val shownStickButtons = physicalInput?.let { input ->
            spec.stickButtons.filter { button ->
                (button.dx < 0 && input.rightX < -0.5f) || (button.dx > 0 && input.rightX > 0.5f) ||
                    (button.dy < 0 && input.rightY < -0.5f) || (button.dy > 0 && input.rightY > 0.5f)
            }.toSet()
        } ?: pressed.stickButtons
        val leftCluster: @Composable BoxScope.(Modifier) -> Unit = { m ->
            Box(m.group("left")) {
                if (spec.leftStick && spec.dpad) {
                    if (landscape) {
                        Box(Modifier.size(unit * 2.8f)) {
                            DPad(unit * 1.7f, shownKeys, Modifier.align(Alignment.TopStart).onGloballyPositioned { regions.register("dpad", it, Hit.DPad) })
                            AnalogStick(
                                unit * 1.7f,
                                shownStick,
                                Modifier.align(Alignment.BottomEnd).onGloballyPositioned {
                                    regions.register("stick", it, Hit.Analog(Stick.LEFT))
                                },
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DPad(unit * 2f, shownKeys, Modifier.onGloballyPositioned { regions.register("dpad", it, Hit.DPad) })
                            AnalogStick(unit * 2f, shownStick, Modifier.onGloballyPositioned { regions.register("stick", it, Hit.Analog(Stick.LEFT)) })
                        }
                    }
                } else if (spec.leftStick) {
                    AnalogStick(unit * 2.7f, shownStick, Modifier.onGloballyPositioned { regions.register("stick", it, Hit.Analog(Stick.LEFT)) })
                } else if (spec.dpad) {
                    DPad(unit * 2.5f, shownKeys, Modifier.onGloballyPositioned { regions.register("dpad", it, Hit.DPad) })
                }
            }
        }
        val rightCluster: @Composable BoxScope.(Modifier) -> Unit = { m ->
            Box(m.group("right").size(unit * 2.95f)) {
                spec.face.forEach { button ->
                    val compact = if (spec.rightStick) 0.7f else 1f
                    val size = (if (button.big) unit * 1.3f else unit) * compact
                    RoundKey(
                        button.label,
                        button.color,
                        button.key in shownKeys,
                        size,
                        Modifier.align(Alignment.Center).offset(
                            unit * (1.08f * button.x * compact + if (spec.rightStick) 0.45f else 0f),
                            unit * (1.08f * button.y * compact - if (spec.rightStick) 0.45f else 0f),
                        )
                            .onGloballyPositioned { regions.register("face-${button.key}", it, Hit.Key(button.key)) },
                    )
                }
                if (spec.rightStick) {
                    val value = physicalInput?.let { Offset(it.rightX, it.rightY) } ?: pressed.rightStick
                    AnalogStick(
                        unit * 1.6f,
                        value,
                        Modifier.align(Alignment.BottomStart).onGloballyPositioned {
                            regions.register("right-stick", it, Hit.Analog(Stick.RIGHT))
                        },
                    )
                }
                spec.stickButtons.forEach { button ->
                    RoundKey(
                        button.label,
                        button.color,
                        button in shownStickButtons,
                        unit * 0.72f,
                        Modifier.align(Alignment.Center).offset(unit * 1.08f * button.x, unit * 1.08f * button.y)
                            .onGloballyPositioned { regions.register("c-${button.label}", it, Hit.StickKey(button)) },
                        small = true,
                    )
                }
            }
        }
        val shoulders: @Composable (Boolean, Modifier) -> Unit = { left, m ->
            val keys = spec.shoulders.filter { it.left == left }.sortedBy { it.row }
            val key: @Composable (Shoulder, Modifier) -> Unit = { shoulder, modifier ->
                PillKey(
                    shoulder.label,
                    shoulder.key in shownKeys,
                    modifier.height(unit * 0.72f).onGloballyPositioned { regions.register("sh-${shoulder.key}", it, Hit.Key(shoulder.key)) },
                )
            }
            if (keys.size > 1) {
                Row(m.group(if (left) "shoulders-left" else "shoulders-right"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    keys.forEach { key(it, Modifier.width(unit * 1.2f)) }
                }
            } else {
                Column(m.group(if (left) "shoulders-left" else "shoulders-right"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    keys.forEach { key(it, Modifier.width(unit * 1.9f)) }
                }
            }
        }
        val centerKeySize = Modifier.width((unit * 1.4f).coerceAtLeast(64.dp)).height((unit * 0.65f).coerceAtLeast(32.dp))
        val center: @Composable (Modifier) -> Unit = { m ->
            Row(m.group("center"), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                spec.select?.let { label ->
                    PillKey(
                        label,
                        PadKey.SELECT in shownKeys,
                        centerKeySize.onGloballyPositioned {
                            regions.register("select", it, Hit.Key(PadKey.SELECT))
                        },
                        small = true,
                    )
                }
                spec.start?.let { label ->
                    PillKey(
                        label,
                        PadKey.START in shownKeys,
                        centerKeySize.onGloballyPositioned {
                            regions.register("start", it, Hit.Key(PadKey.START))
                        },
                        small = true,
                    )
                }
            }
        }
        if (placement == ControlsPlacement.OVERLAY) {
            shoulders(true, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 56.dp))
            shoulders(false, Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 56.dp))
            leftCluster(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 18.dp))
            rightCluster(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 18.dp))
            center(Modifier.align(Alignment.BottomCenter).padding(bottom = if (landscape) 14.dp else unit * 3f + 26.dp))
        } else if (placement == ControlsPlacement.BOTTOM_CORNERS) {
            val margin = maxWidth * gameLeft
            shoulders(true, Modifier.align(Alignment.TopStart).width(margin).padding(top = 56.dp, start = 6.dp))
            shoulders(false, Modifier.align(Alignment.TopEnd).width(margin).padding(top = 56.dp, end = 6.dp))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(band)) {
                leftCluster(Modifier.align(Alignment.CenterStart).padding(start = 20.dp))
                rightCluster(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    center(Modifier)
                }
            }
        } else if (landscape) {
            val bottomPad = 56.dp
            val pill = Modifier.width((unit * 1.4f).coerceAtLeast(64.dp)).height((unit * 0.65f).coerceAtLeast(32.dp))
            Box(Modifier.fillMaxHeight().width(side).align(Alignment.CenterStart)) {
                Column(
                    Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 56.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    shoulders(true, Modifier)
                }
                leftCluster(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad))
                spec.select?.let { label ->
                    PillKey(
                        label,
                        PadKey.SELECT in shownKeys,
                        Modifier.align(Alignment.BottomCenter).offset(y = (-14).dp).then(pill).group("center").onGloballyPositioned {
                            regions.register("select", it, Hit.Key(PadKey.SELECT))
                        },
                        small = true,
                    )
                }
            }
            Box(Modifier.fillMaxHeight().width(side).align(Alignment.CenterEnd)) {
                shoulders(false, Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 56.dp))
                rightCluster(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad))
                spec.start?.let { label ->
                    PillKey(
                        label,
                        PadKey.START in shownKeys,
                        Modifier.align(Alignment.BottomCenter).offset(y = (-14).dp).then(pill).group("center").onGloballyPositioned {
                            regions.register("start", it, Hit.Key(PadKey.START))
                        },
                        small = true,
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(controlsHeight)
                    .align(Alignment.BottomCenter)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Palette.SkyLow, Palette.Deep.copy(alpha = 1f)))),
            ) {
                shoulders(true, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 14.dp))
                shoulders(false, Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 14.dp))
                leftCluster(Modifier.align(Alignment.CenterStart).padding(start = 18.dp))
                rightCluster(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
                center(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp))
            }
        }
    }
}

/** Directions de la croix : 8 directions, zone morte au centre. */
internal fun dpadKeys(v: Offset): Set<PadKey> {
    val length = hypot(v.x, v.y)
    if (length < 0.22f) return emptySet()
    val keys = mutableSetOf<PadKey>()
    if (abs(v.x) > 0.38f * length) keys += if (v.x > 0) PadKey.RIGHT else PadKey.LEFT
    if (abs(v.y) > 0.38f * length) keys += if (v.y > 0) PadKey.DOWN else PadKey.UP
    return keys
}

private val KeyFill get() = Palette.ButtonTop.copy(alpha = 0.85f)
private val KeyLine get() = Palette.Text.copy(alpha = 0.38f)
private val KeyPressed get() = Palette.PrimaryBottom.copy(alpha = 0.9f)

@Composable
private fun RoundKey(label: String, color: Color, down: Boolean, size: Dp, modifier: Modifier, small: Boolean = false) {
    Box(
        modifier.size(
            size,
        ).testTag(
            "touch-key-${label.lowercase()}",
        ).semantics {
            selected = down
        }.consoleButtonSurface(CircleShape, primary = down).border(1.dp, if (down) color else color.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = if (small) 12.sp else 20.sp),
            color = if (down) Palette.OnAccent else lerp(color, Color.White, 0.45f),
        )
    }
}

@Composable
private fun PillKey(label: String, down: Boolean, modifier: Modifier, small: Boolean = false) {
    Box(
        modifier.testTag("touch-key-${label.lowercase()}").semantics { selected = down }.consoleButtonSurface(RoundedCornerShape(50), primary = down),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = if (small) 11.sp else 14.sp),
            color = if (down) Palette.OnAccent else Color.White,
        )
    }
}

@Composable
private fun DPad(size: Dp, keys: Set<PadKey>, modifier: Modifier) {
    Canvas(modifier.size(size).testTag("touch-dpad")) {
        val arm = this.size.width / 3
        val path =
            Path().apply {
                moveTo(arm, 0f)
                lineTo(arm * 2, 0f)
                lineTo(arm * 2, arm)
                lineTo(arm * 3, arm)
                lineTo(arm * 3, arm * 2)
                lineTo(arm * 2, arm * 2)
                lineTo(arm * 2, arm * 3)
                lineTo(arm, arm * 3)
                lineTo(arm, arm * 2)
                lineTo(0f, arm * 2)
                lineTo(0f, arm)
                lineTo(arm, arm)
                close()
            }
        drawPath(path, Brush.verticalGradient(listOf(KeyFill, Palette.ButtonBottom.copy(alpha = 0.85f))))
        drawPath(path, KeyLine, style = Stroke(2.dp.toPx()))
        val highlight = { x: Float, y: Float -> drawRect(KeyPressed, Offset(x, y), androidx.compose.ui.geometry.Size(arm, arm)) }
        if (PadKey.UP in keys) highlight(arm, 0f)
        if (PadKey.DOWN in keys) highlight(arm, arm * 2)
        if (PadKey.LEFT in keys) highlight(0f, arm)
        if (PadKey.RIGHT in keys) highlight(arm * 2, arm)
        drawCircle(KeyLine, arm * 0.18f, Offset(arm * 1.5f, arm * 1.5f))
    }
}

@Composable
internal fun AnalogStick(size: Dp, position: Offset, modifier: Modifier, tag: String = "touch-left-stick") {
    Box(modifier.size(size).testTag(tag).clip(CircleShape).background(KeyFill).border(2.dp, KeyLine, CircleShape)) {
        val knob = size * 0.42f
        val travel = (size - knob) / 2
        Box(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset((position.x * travel.toPx()).roundToInt(), (position.y * travel.toPx()).roundToInt()) }
                .size(knob)
                .clip(CircleShape)
                .background(if (position != Offset.Zero) KeyPressed else Color(0x66FFFFFF)),
        )
    }
}
