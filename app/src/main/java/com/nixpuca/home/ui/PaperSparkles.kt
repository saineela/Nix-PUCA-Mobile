package com.nixpuca.home.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One piece of falling paper. Parameters are fixed per particle (no random
 * calls during draw) so the shower is deterministic and cheap: the animation
 * only ever advances one shared float.
 */
private class PaperPiece(
    val x: Float,          // horizontal start, 0..1 of width
    val delay: Float,      // phase offset, 0..1
    val speed: Float,      // fall speed multiplier
    val wDp: Float,
    val hDp: Float,
    val spinTurns: Float,  // full rotations across one fall
    val swayDp: Float,     // horizontal drift amplitude
    val color: Int,        // index into the palette list
    val streamer: Boolean, // long thin strip vs small confetti square
)

/** Confetti pieces: small squares, curled strips and a few long streamers. */
private fun buildPieces(count: Int): List<PaperPiece> {
    // Deterministic pseudo-random (LCG) — same shower every celebration.
    var seed = 0x5EED5EED
    fun next(): Float {
        seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
        return seed / 0x7FFFFFFF.toFloat()
    }
    return List(count) { i ->
        val streamer = next() > 0.82f
        PaperPiece(
            x = (i + next() * 0.9f) / count,
            delay = next(),
            speed = 0.75f + next() * 0.7f,
            wDp = if (streamer) 3f else 5f + next() * 5f,
            hDp = if (streamer) 14f + next() * 10f else 5f + next() * 5f,
            spinTurns = if (streamer) 1f + next() * 2f else 2f + next() * 4f,
            swayDp = 6f + next() * 22f,
            color = (next() * 6).toInt(),
            streamer = streamer,
        )
    }
}

/**
 * Falling paper sparkles: the celebration backdrop for a finished pairing.
 *
 * A loop of paper pieces drops from above the top edge, sways side to side
 * as it falls and tumbles end over end, fading in at the top and out at the
 * bottom so nothing ever pops. Drawn in a Canvas with one shared progress
 * float, so the whole shower costs a single invalidation per frame.
 */
@Composable
fun FallingPaperSparkles(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sparkles")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(5_200, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "sparkleProgress",
    )
    val pieces = buildPieces(56)

    Canvas(modifier) {
        val colors = listOf(
            NixPalette.Accent,
            NixPalette.Ok,
            NixPalette.OnAccent.takeIf { NixPalette.Accent != Color.Black }
                ?: NixPalette.Ink,
            Color(0xFFF4B942), // amber
            Color(0xFFFF7AA2), // rose
            NixPalette.Ink,
        )
        val height = size.height
        val width = size.width

        pieces.forEach { p ->
            val w = p.wDp.dp.toPx()
            val h = p.hDp.dp.toPx()
            val phase = (progress * p.speed + p.delay) % 1f
            val y = phase * (height + h * 2f) - h
            val sway = sin((phase + p.delay) * 2.0 * PI).toFloat() * p.swayDp.dp.toPx()
            val x = p.x * width + sway - w / 2f

            // Fade at both ends of the run so pieces never appear/disappear hard.
            val fade = when {
                phase < 0.12f -> phase / 0.12f
                phase > 0.9f -> (1f - phase) / 0.1f
                else -> 1f
            }
            val color = colors[p.color % colors.size]

            rotate(
                degrees = 360f * p.spinTurns * phase,
                pivot = Offset(x + w / 2f, y + h / 2f),
            ) {
                drawRect(
                    color = color.copy(alpha = 0.85f * fade.coerceIn(0f, 1f)),
                    topLeft = Offset(x, y),
                    size = Size(w, h),
                )
                if (p.streamer) {
                    // A lighter core stripe reads as a curled paper strip.
                    drawRect(
                        color = NixPalette.Paper.copy(alpha = 0.7f * fade.coerceIn(0f, 1f)),
                        topLeft = Offset(x + w * 0.32f, y),
                        size = Size(w * 0.36f, h),
                    )
                }
            }
        }
    }
}
