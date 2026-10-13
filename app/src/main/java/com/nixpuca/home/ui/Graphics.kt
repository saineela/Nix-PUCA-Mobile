package com.nixpuca.home.ui

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nixpuca.home.NixViewModel

// ---------------------------------------------------------------- GPU brands

enum class GpuVendor(val displayName: String, val brand: Color) {
    Nvidia("NVIDIA", Color(0xFF76B900)),
    Amd("AMD", Color(0xFFED1C24)),
    Intel("Intel", Color(0xFF0071C5)),
    Unknown("GPU", Color(0xFF444444)),
}

fun detectGpuVendor(name: String?): GpuVendor = when {
    name == null -> GpuVendor.Unknown
    name.contains("nvidia", true) || name.contains("rtx", true) || name.contains("geforce", true) -> GpuVendor.Nvidia
    name.contains("amd", true) || name.contains("radeon", true) || name.contains("rx ", true) -> GpuVendor.Amd
    name.contains("intel", true) || name.contains("arc ", true) || name.contains("uhd", true) -> GpuVendor.Intel
    else -> GpuVendor.Unknown
}

/**
 * Graphical GPU tile: brand wordmark over a stylised die shot with the live
 * GPU name. Recognises NVIDIA / AMD / Intel and paints their brand colour.
 */
@Composable
fun GpuCard(name: String?, modifier: Modifier = Modifier) {
    val vendor = detectGpuVendor(name)
    val pulse = rememberInfiniteTransition(label = "gpu")
    val glow by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "glow",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0B0B0F),
    ) {
        Box {
            // Circuit-glow backdrop in the brand colour.
            Canvas(Modifier.fillMaxSize()) {
                val brand = vendor.brand
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(brand.copy(alpha = 0.35f * glow), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.2f),
                        radius = size.minDimension * 0.9f,
                    ),
                )
                // Die grid.
                val cell = size.minDimension / 9f
                for (r in 0..8) {
                    for (c in 0..9) {
                        drawLine(
                            color = brand.copy(alpha = 0.08f),
                            start = Offset(c * cell * 1.15f, 0f),
                            end = Offset(c * cell * 1.15f, size.height),
                            strokeWidth = 1f,
                        )
                        drawLine(
                            color = brand.copy(alpha = 0.08f),
                            start = Offset(0f, r * cell),
                            end = Offset(size.width, r * cell),
                            strokeWidth = 1f,
                        )
                    }
                }
            }
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Brand wordmark.
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = vendor.brand,
                    ) {
                        Text(
                            text = vendor.displayName,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "GPU",
                        color = Color(0xFF8A8A96),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = name ?: "No GPU reported",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                // Die shot glyph.
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                ) {
                    val dieW = size.height * 1.5f
                    val dieH = size.height
                    val left = 2f
                    val top = 2f
                    drawRoundRect(
                        color = vendor.brand.copy(alpha = 0.9f),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(dieW, dieH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                    )
                    drawRoundRect(
                        color = vendor.brand.copy(alpha = 0.35f),
                        topLeft = Offset(left + 7f, top + 7f),
                        size = androidx.compose.ui.geometry.Size(dieW - 14f, dieH - 14f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                    )
                    // Pins.
                    val pinGap = dieW / 9f
                    for (i in 0..8) {
                        drawLine(
                            color = vendor.brand.copy(alpha = 0.8f),
                            start = Offset(left + i * pinGap + pinGap / 2, top + dieH),
                            end = Offset(left + i * pinGap + pinGap / 2, top + dieH + 8f),
                            strokeWidth = 3f,
                        )
                        drawLine(
                            color = vendor.brand.copy(alpha = 0.8f),
                            start = Offset(left + i * pinGap + pinGap / 2, top - 6f),
                            end = Offset(left + i * pinGap + pinGap / 2, top),
                            strokeWidth = 3f,
                        )
                    }
                    // Traces running off the die.
                    var traceY = 6f
                    for (i in 0..4) {
                        val y = size.height * (i + 1) / 6f
                        drawLine(
                            color = vendor.brand.copy(alpha = 0.5f),
                            start = Offset(left + dieW, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                        )
                        traceY += 4f
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------- sparkline

/** Minimal line/area graph used for latency and other rolling stats. */
@Composable
fun Sparkline(
    points: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = NixPalette.Accent,
    fillColor: Color = NixPalette.Accent.copy(alpha = 0.12f),
) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val max = points.max().coerceAtLeast(1.0)
        val min = points.min().coerceAtMost(max)
        val range = (max - min).coerceAtLeast(1.0)
        val stepX = size.width / (points.size - 1)
        val path = Path()
        val fill = Path()
        points.forEachIndexed { i, value ->
            val x = i * stepX
            val y = size.height - ((value - min) / range * size.height).toFloat() - 2f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            if (i == 0) {
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else fill.lineTo(x, y)
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, fillColor)
        drawPath(
            path,
            lineColor,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
}

// ------------------------------------------------------------- stat tile

/** Eyebrow stat tile: big value, small label, optional graph footer. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    ok: Boolean? = null,
    accent: Boolean = false,
    graph: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (accent) NixPalette.Ink else NixPalette.Mist,
        border = if (accent) null else androidx.compose.foundation.BorderStroke(1.dp, NixPalette.Line),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ok != null) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (ok) Color(0xFF3DDC84) else Color(0xFFE5484D))
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (accent) Color(0xFF9A9AA6) else NixPalette.Muted,
                    letterSpacing = 1.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (accent) Color.White else NixPalette.Ink,
                maxLines = 1,
            )
            if (graph != null) {
                Spacer(Modifier.height(6.dp))
                graph()
            }
        }
    }
}

// ------------------------------------------------------------ Echo Dot

/**
 * Compact circular device glyph for the devices grid: the same LED ring
 * language as [EchoDotVisual] at tile size, breathing while an effect runs.
 */
@Composable
fun DeviceGlyph(
    state: NixViewModel.RingState?,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val clock = rememberRingClock()
    val on = state?.on == true && connected
    val base = state?.rgb?.let { rgb ->
        Color(
            red = rgb.getOrElse(0) { 255 } / 255f,
            green = rgb.getOrElse(1) { 255 } / 255f,
            blue = rgb.getOrElse(2) { 255 } / 255f,
        )
    } ?: Color(0xFF7CFF6B)
    val effect = state?.effect?.takeIf { RingFx.isEffect(it) }

    Canvas(modifier.size(64.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 2f
        if (on && effect != null) {
            // The device's real effect frame, at tile size.
            val segs = RingFx.frame(effect, clock, base, state?.brightness ?: 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        RingFx.meanColor(segs).copy(alpha = 0.40f * RingFx.meanLuma(segs)),
                        Color.Transparent,
                    ),
                    center = c,
                    radius = r * 1.35f,
                ),
                center = c,
                radius = r * 1.35f,
            )
            drawSegmentRing(segs, c.x, c.y, r, stroke = 7f)
        } else {
            if (on) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(base.copy(alpha = 0.28f), Color.Transparent),
                        center = c,
                        radius = r * 1.35f,
                    ),
                    center = c,
                    radius = r * 1.35f,
                )
            }
            drawCircle(
                color = if (on) base else Color(0xFFD5D5DA),
                radius = r,
                style = Stroke(width = 7f),
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF2E2E36), Color(0xFF131318)),
                center = Offset(c.x - r * 0.3f, c.y - r * 0.35f),
                radius = r,
            ),
            center = c,
            radius = r * 0.8f,
        )
        drawCircle(
            color = if (on) base else Color(0xFFB9B9C1),
            radius = 4.5f,
            center = Offset(c.x, c.y - r * 0.52f),
        )
    }
}

/**
 * Graphical Echo Dot: an LED ring that mirrors the live device state
 * (colour, brightness, on/off) and plays every advertised effect with the
 * device's own animation code — see [RingFx], ported motion-for-motion from
 * the firmware, so selecting an effect runs the real thing.
 */
@Composable
fun EchoDotVisual(
    state: NixViewModel.RingState?,
    connected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    val clock = rememberRingClock()
    val on = state?.on == true && connected
    val base = state?.rgb?.let { rgb ->
        Color(
            red = rgb.getOrElse(0) { 255 } / 255f,
            green = rgb.getOrElse(1) { 255 } / 255f,
            blue = rgb.getOrElse(2) { 255 } / 255f,
        )
    } ?: Color(0xFF7CFF6B)
    val effect = state?.effect?.takeIf { RingFx.isEffect(it) }
    val brightness = state?.brightness ?: 1f
    // Shell/label colour: the ring's own colour. While an effect runs the
    // frame itself paints the ring; the glow inside the canvas follows the
    // frame's average colour instead.
    val ringColor = base

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0B0B0F),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .padding(20.dp),
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = minOf(cx, cy) - 6f
                // The device's real effect frame at this moment — the same
                // maths the firmware runs — or null for a solid/off ring.
                val segs = if (on && effect != null) {
                    RingFx.frame(effect, clock, base, brightness)
                } else {
                    null
                }
                // Ambient glow: steady for a solid light, riding the frame's
                // own brightness while an effect runs (dark beats stay dark).
                if (on) {
                    val glowColor = segs?.let { RingFx.meanColor(it) } ?: ringColor
                    val glowLuma = segs?.let { RingFx.meanLuma(it) } ?: 1f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                glowColor.copy(alpha = 0.30f * (0.18f + 0.82f * glowLuma)),
                                Color.Transparent,
                            ),
                            center = Offset(cx, cy),
                            radius = radius * 1.5f,
                        ),
                        center = Offset(cx, cy),
                        radius = radius * 1.5f,
                    )
                }
                // The ring: twelve LEDs for an effect, one steady circle for
                // a solid colour, the dark track when the light is off.
                if (segs != null) {
                    drawSegmentRing(segs, cx, cy, radius, stroke = 14f)
                } else {
                    drawCircle(
                        color = if (on) ringColor else Color(0xFF2A2A31),
                        radius = radius,
                        style = Stroke(width = 14f),
                    )
                }
                // Speaker body.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF2E2E36), Color(0xFF131318)),
                        center = Offset(cx - radius * 0.3f, cy - radius * 0.35f),
                        radius = radius * 1.05f,
                    ),
                    center = Offset(cx, cy),
                    radius = radius * 0.82f,
                )
                // Top light dot.
                drawCircle(
                    color = if (on) ringColor else Color(0xFF3A3A44),
                    radius = 5f,
                    center = Offset(cx, cy - radius * 0.55f),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = label,
                    color = Color(0xFFEDEDF2),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        !connected -> "not connected"
                        on -> state?.effect?.takeIf { it.isNotBlank() && !it.equals("none", true) } ?: "on"
                        else -> "off"
                    },
                    color = if (on) ringColor else Color(0xFF7A7A86),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

// -------------------------------------------------- ring-mirror background

/**
 * Optional full-screen background that mirrors the ring light's live effect
 * (e.g. Rainbow Twinkle paints slow rainbow bands). Enabled in Settings.
 */
@Composable
fun RingEffectBackground(state: NixViewModel.RingState?) {
    if (state == null || !state.on) return
    val transition = rememberInfiniteTransition(label = "mirror")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val effect = state.effect
    val isRainbow = effect.contains("rainbow", true)
    val base = state.rgb.let {
        Color(
            red = it.getOrElse(0) { 255 } / 255f,
            green = it.getOrElse(1) { 255 } / 255f,
            blue = it.getOrElse(2) { 255 } / 255f,
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        if (isRainbow) {
            val colors = listOf(
                Color(0xFFFF5E5E), Color(0xFFFFB74D), Color(0xFF66FF9E),
                Color(0xFF4FC3F7), Color(0xFF9C7BFF), Color(0xFFFF5E5E),
            )
            val shift = phase * size.width
            drawRect(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(-size.width + shift, 0f),
                    end = Offset(shift, size.height),
                ),
                alpha = 0.16f,
            )
        } else if (!effect.equals("none", true) && effect.isNotBlank()) {
            // Named solid/pulse effects: slow breathing wash of the live colour.
            val pulse = 0.5f + 0.5f * kotlin.math.sin(phase * 2 * Math.PI).toFloat()
            drawRect(color = base.copy(alpha = 0.10f + 0.08f * pulse))
        } else {
            drawRect(color = base.copy(alpha = 0.10f))
        }
    }
}

// --------------------------------------------------------- Google Calendar

/** The four-colour Google "G" mark, drawn as arcs. */
@Composable
fun GoogleG(size: Int = 20, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size.dp)) {
        val stroke = size.dp.toPx() * 0.22f
        val r = (size.dp.toPx() - stroke) / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        fun arc(start: Float, sweep: Float, color: Color) = drawArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
            topLeft = Offset(c.x - r, c.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        )
        arc(-45f, 105f, Color(0xFFEA4335)) // red top-right
        arc(60f, 105f, Color(0xFFFBBC05))  // yellow right-bottom
        arc(165f, 105f, Color(0xFF34A853)) // green bottom-left
        arc(270f, 65f, Color(0xFF4285F4))  // blue left-top
        // Horizontal bar of the G.
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(c.x, c.y - stroke / 2),
            end = Offset(c.x + r, c.y - stroke / 2),
            strokeWidth = stroke,
            cap = StrokeCap.Butt,
        )
    }
}
