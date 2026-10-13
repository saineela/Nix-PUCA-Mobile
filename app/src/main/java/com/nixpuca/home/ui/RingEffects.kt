package com.nixpuca.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Echo Dot's own effect engine, ported motion-for-motion from the device
 * firmware (`echolocal/internal/hardware/led`): every one of the 38 effects
 * the ring advertises, with its real palettes, periods and frame maths.
 *
 * The device draws a 12-segment LED ring at 25 fps; each frame is a pure
 * function of elapsed time, exactly as the firmware's `Frame` is. That means
 * selecting an effect on the Devices page runs the same animation the Dot
 * runs — same colours, same speeds, same shapes — sampled here at the
 * screen's frame rate instead of 25 fps.
 *
 * Source of truth for each motion: `effect_*.go`; palettes: `palette.go`;
 * helpers (`around`, `shaded`, `dot`, `ringDist`, `wander`, `hash`, `Arc`):
 * `shape.go` / `color.go`. Names match `catalogue.go` verbatim.
 */
/** A palette: the colours an effect draws from, sampled round or along. */
private typealias Palette = List<Color>

internal object RingFx {

    /** The Dot's ring: twelve LEDs. */
    const val SEGMENTS = 12

    // ------------------------------------------------------- palettes

    /** Every hue at 0.6 of full scale — where the ring's rainbow has always sat. */
    private val wheel: Palette = List(6) { i -> scale(hue(i / 6f), 0.6f) }

    /** One colour, roughly a candle at 1900 K. */
    private val flame: Palette = listOf(Color(0xFFFF9329))

    /** Near-white flame base → orange → dull red edge; hottest first, sampled Along. */
    private val fire: Palette = listOf(
        Color(0xFFFFE0A0), Color(0xFFFF9020), Color(0xFFD03000), Color(0xFF400400),
    )

    /** Blood: nearly black at rest up to a scarlet with white in it; darkest first. */
    private val crimson: Palette = listOf(
        Color(0xFF280004), Color(0xFF800008), Color(0xFFD00014), Color(0xFFFF4048),
    )

    /** The greens and violets of an aurora, closing back on green (seamless round). */
    private val aurora: Palette = listOf(
        Color(0xFF00C850), Color(0xFF00B0B0), Color(0xFF3040C0), Color(0xFF7020A0), Color(0xFF109080),
    )

    /** Deep water up to the light on top; deepest first so a crest reaches the far end. */
    private val ocean: Palette = listOf(
        Color(0xFF000C40), Color(0xFF0050A0), Color(0xFF00B0C0), Color(0xFF90F0E0),
    )

    /** The cold end of white: blue shadow to glare; hottest last. */
    private val ice: Palette = listOf(
        Color(0xFF081850), Color(0xFF1060C0), Color(0xFF60C0F0), Color(0xFFE0F8FF),
    )

    /** Dusk sky: violet overhead down to gold at the horizon; darkest first. */
    private val sunset: Palette = listOf(
        Color(0xFF300860), Color(0xFFA01060), Color(0xFFE05020), Color(0xFFFFB030),
    )

    /** Forest greens, deep shade to new growth, no blue. */
    private val forest: Palette = listOf(
        Color(0xFF042810), Color(0xFF107020), Color(0xFF40B020), Color(0xFFA0E040),
    )

    /** Fault red: dark red at rest to near-white at the top of a pulse. */
    private val alarm: Palette = listOf(
        Color(0xFF300000), Color(0xFFC00000), Color(0xFFFF3018), Color(0xFFFFB0A0),
    )

    /** Two colours far enough apart to stay separate — sampled with Nth, never blended. */
    private val duo: Palette = listOf(Color(0xFF00E0C0), Color(0xFFE000A0))

    /** Pac-Man: his yellow, and the dim white of what he has not eaten yet. */
    private val pellets: Palette = listOf(Color(0xFFFFD000), Color(0xFF303038))

    // -------------------------------------------------- frame dispatch

    /**
     * One moment of [effect]: the twelve segment colours, brightened by
     * [brightness] the way the driver dims the whole ring. [base] is the
     * ring's own colour, used by every effect that runs "in the ring's
     * colour"; paletted effects ignore it, exactly as the firmware does.
     *
     * Blank / "None" is a steady ring in the base colour.
     */
    fun frame(effect: String?, elapsedMs: Double, base: Color, brightness: Float = 1f): List<Color> {
        val name = effect?.trim().orEmpty()
        val p1 = listOf(base)
        val out = when {
            name.isEmpty() || name.equals("none", true) -> List(SEGMENTS) { base }
            else -> when (name.lowercase()) {
                // Ambient, in the ring's colour.
                "pulse" -> breathe(p1, elapsedMs)
                "heartbeat" -> heartbeat(p1, elapsedMs)
                "ripple" -> ripple(p1, elapsedMs)
                "standing wave" -> standing(p1, elapsedMs)
                "twinkle" -> twinkle(p1, elapsedMs)

                // Ambient, in colours of their own.
                "crimson heartbeat" -> heartbeat(crimson, elapsedMs)
                "aurora pulse" -> breathe(aurora, elapsedMs)
                "candle" -> flicker(flame, elapsedMs)
                "fireplace" -> flicker(fire, elapsedMs)
                "embers" -> embers(fire, elapsedMs)
                "aurora" -> drift(aurora, elapsedMs)
                "sunset drift" -> drift(sunset, elapsedMs)
                "ocean ripple" -> ripple(ocean, elapsedMs)
                "rainbow twinkle" -> twinkle(wheel, elapsedMs)
                "forest twinkle" -> twinkle(forest, elapsedMs)

                // Motion, in the ring's colour.
                "comet" -> comet(p1, elapsedMs)
                "chase" -> chase(p1, elapsedMs)
                "scanner" -> scanner(p1, elapsedMs)
                "pinwheel" -> pinwheel(p1, elapsedMs)
                "spiral" -> spiral(p1, elapsedMs)
                "wipe" -> wipe(p1, elapsedMs)
                "helix" -> helix(p1, elapsedMs)
                "orbits" -> orbits(p1, elapsedMs)
                "bounce" -> bounce(p1, elapsedMs)
                "spring" -> spring(p1, elapsedMs)

                // Motion, in colours of their own.
                "rainbow" -> spin(wheel, elapsedMs)
                "fire comet" -> comet(fire, elapsedMs)
                "ice comet" -> comet(ice, elapsedMs)
                "rainbow chase" -> chase(wheel, elapsedMs)
                "ice scanner" -> scanner(ice, elapsedMs)
                "rainbow pinwheel" -> pinwheel(wheel, elapsedMs)
                "sunset spiral" -> spiral(sunset, elapsedMs)
                "rainbow orbits" -> orbits(wheel, elapsedMs)
                "dna" -> helix(duo, elapsedMs)
                "pac-man" -> pacman(pellets, elapsedMs)

                // Announcements.
                "alert" -> alert(alarm, elapsedMs)
                "beacon" -> beacon(alarm, elapsedMs)

                // Anything else the device might name (room effects etc.):
                // a calm breath of the ring's own colour rather than a dark ring.
                else -> breathe(p1, elapsedMs)
            }
        }
        val b = brightness.coerceIn(0f, 1f)
        return if (b >= 0.999f) out else out.map { scale(it, b) }
    }

    /** Average colour of a frame — for the ambient glow behind the ring. */
    fun meanColor(segs: List<Color>): Color {
        var r = 0f
        var g = 0f
        var b = 0f
        for (c in segs) {
            r += c.red
            g += c.green
            b += c.blue
        }
        val n = segs.size.coerceAtLeast(1).toFloat()
        return Color((r / n).coerceIn(0f, 1f), (g / n).coerceIn(0f, 1f), (b / n).coerceIn(0f, 1f))
    }

    /** Perceived brightness of a frame, 0…1 — drives the glow so dark beats stay dark. */
    fun meanLuma(segs: List<Color>): Float {
        var l = 0f
        for (c in segs) l += 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
        return (l / segs.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    /** Whether [name] is a running animation (as opposed to blank / "None"). */
    fun isEffect(name: String?): Boolean =
        !name.isNullOrBlank() && !name.equals("none", true)

    // -------------------------------------------------- colour helpers

    /** scale: the channels are linear brightness, so this is the whole of it. */
    private fun scale(c: Color, f: Float): Color = Color(
        red = (c.red * f).coerceIn(0f, 1f),
        green = (c.green * f).coerceIn(0f, 1f),
        blue = (c.blue * f).coerceIn(0f, 1f),
    )

    /** add: one colour on top of another, holding at full scale. */
    private fun add(a: Color, b: Color): Color = Color(
        red = (a.red + b.red).coerceAtMost(1f),
        green = (a.green + b.green).coerceAtMost(1f),
        blue = (a.blue + b.blue).coerceAtMost(1f),
    )

    /** blend: f of the way from a to b, linear. */
    private fun blend(a: Color, b: Color, f: Float): Color {
        val g = f.coerceIn(0f, 1f)
        return Color(
            red = a.red + (b.red - a.red) * g,
            green = a.green + (b.green - a.green) * g,
            blue = a.blue + (b.blue - a.blue) * g,
        )
    }

    /** hue: a 0–1 position on the colour wheel to full-saturation RGB. */
    private fun hue(h: Float): Color {
        val x = ((h * 6f) % 6f + 6f) % 6f
        fun ramp(v: Float) = v.coerceIn(0f, 1f)
        return when (x.toInt()) {
            0 -> Color(1f, ramp(x), 0f)
            1 -> Color(ramp(2 - x), 1f, 0f)
            2 -> Color(0f, 1f, ramp(x - 2))
            3 -> Color(0f, ramp(4 - x), 1f)
            4 -> Color(ramp(x - 4), 0f, 1f)
            else -> Color(1f, 0f, ramp(6 - x))
        }
    }

    // -------------------------------------------------- palette sampling

    /** At: sampled as a circle, blending neighbours, wrapping past the end (seamless). */
    private fun Palette.at(x: Float): Color {
        if (isEmpty()) return Color.Black
        if (size == 1) return this[0]
        val xf = (((x % 1f) + 1f) % 1f) * size
        val i = xf.toInt().coerceIn(0, size - 1)
        return blend(this[i], this[(i + 1) % size], xf - i)
    }

    /** Along: sampled as a gradient with two ends, for palettes that run one way. */
    private fun Palette.along(x: Float): Color {
        if (size < 2) return at(x)
        val xf = x.coerceIn(0f, 1f) * (size - 1)
        val i = xf.toInt()
        if (i >= size - 1) return this[size - 1]
        return blend(this[i], this[i + 1], xf - i)
    }

    /**
     * Shade: sample at x, dim by dim. A one-colour palette dims in full; a
     * palette that already cools along its length only has the edge taken off,
     * so its far end does not go black.
     */
    private fun Palette.shade(x: Float, dim: Float): Color {
        val d = if (size > 1) 0.5f + 0.5f * dim else dim
        return scale(along(x), d)
    }

    /** Nth: one colour whole, for effects with countable parts. */
    private fun Palette.nth(i: Int): Color = this[((i % size) + size) % size]

    // -------------------------------------------------- shape helpers

    /** around: the palette laid round the ring at one brightness f. */
    private fun around(p: Palette, f: Float): List<Color> =
        List(SEGMENTS) { i -> scale(p.at(i / SEGMENTS.toFloat()), f) }

    /** shaded: every segment at the same point in the palette, climbing as f rises. */
    private fun shaded(p: Palette, f: Float): List<Color> = List(SEGMENTS) { p.shade(f, f) }

    /** ringDist: how far segment i is from a position, the short way round the ring. */
    private fun ringDist(i: Int, at: Float): Float {
        val norm = ((at % SEGMENTS) + SEGMENTS) % SEGMENTS
        val d = abs(i - norm)
        return min(d, SEGMENTS - d)
    }

    /** dot: a glow centred on a fractional position, added rather than assigned. */
    private fun dot(out: MutableList<Color>, at: Float, c: Color, width: Float) {
        for (i in 0 until SEGMENTS) {
            val d = ringDist(i, at)
            if (d >= width) continue
            val f = 1f - d / width
            out[i] = add(out[i], scale(c, f * f))
        }
    }

    /** wander: three sines whose rates do not divide each other — flicker that never repeats. */
    private fun wander(t: Double, offset: Double): Double {
        val s = sin(2 * PI * (1.7 * t + offset)) +
            sin(2 * PI * (2.9 * t + 1.3 * offset)) +
            sin(2 * PI * (4.7 * t + 0.6 * offset))
        return (s / 3 + 1) / 2
    }

    /**
     * hash: mixes an integer into one that looks unrelated to its neighbours,
     * so per-segment variation needs no state between frames. Same mix as the
     * firmware (uint32 arithmetic; Int wraps identically).
     */
    private fun hash(input: Int): Int {
        var x = input
        x = x xor (x ushr 16)
        x *= 0x7FEB352D
        x = x xor (x ushr 15)
        x *= -2073254261 // 0x846CA68B as a signed 32-bit wrap
        x = x xor (x ushr 16)
        return x
    }

    /**
     * Arc: lights a clockwise fraction of the ring from 0 to 1, dimming the
     * leading segment by whatever is left over. Fills from segment 11 so the
     * arc grows across the front of the device.
     */
    private fun arc(fraction: Float, c: Color): List<Color> {
        val f = fraction.coerceIn(0f, 1f)
        val lit = f * SEGMENTS
        val out = MutableList(SEGMENTS) { Color.Black }
        for (i in 0 until SEGMENTS) {
            val remaining = lit - i
            if (remaining <= 0f) break
            out[(SEGMENTS - 1 + i) % SEGMENTS] = scale(c, min(1f, remaining))
        }
        return out
    }

    // -------------------------------------------------- the motions

    /** breathe: pulses the whole ring. 1200 ms cosine. */
    private fun breathe(p: Palette, t: Double): List<Color> {
        val period = 1200.0
        val f = ((1 - cos(2 * PI * (t % period) / period)) / 2).toFloat()
        return around(p, f)
    }

    /** heartbeat: thumps twice and rests — strong beat, weaker one behind, then a gap. */
    private fun heartbeat(p: Palette, t: Double): List<Color> {
        val period = 1400.0
        val decay = 170.0
        val beats = listOf(0.0 to 1.0, 200.0 to 0.6)
        val tt = t % period
        var f = 0.0
        for ((at, amp) in beats) {
            if (tt < at) continue
            f = maxOf(f, amp * exp(-(tt - at) / decay))
        }
        return shaded(p, f.toFloat())
    }

    /** ripple: two crests travelling round the ring over a low glow. 3200 ms a lap. */
    private fun ripple(p: Palette, t: Double): List<Color> {
        val revolution = 3200.0
        val crests = 2.0
        val glow = 0.12
        val phase = (t % revolution) / revolution
        return List(SEGMENTS) { i ->
            var c = (1 + cos(2 * PI * crests * (i.toDouble() / SEGMENTS - phase))) / 2
            c *= c * c // cubed: narrows crests, widens troughs, stays positive
            p.shade(c.toFloat(), (glow + (1 - glow) * c).toFloat())
        }
    }

    /** standing: two waves going opposite ways; where they cancel the ring is dark, in place. */
    private fun standing(p: Palette, t: Double): List<Color> {
        val period = 2600.0
        val crests = 2.0
        val glow = 0.08
        val swell = cos(2 * PI * (t % period) / period)
        return List(SEGMENTS) { i ->
            val f = abs(sin(2 * PI * crests * i / SEGMENTS) * swell)
            p.shade(f.toFloat(), (glow + (1 - glow) * f).toFloat())
        }
    }

    /**
     * twinkle: each segment brightens on its own schedule — period, phase and
     * palette place all hashed from the index, periods not dividing each other.
     */
    private fun twinkle(p: Palette, t: Double): List<Color> {
        val shortest = 1700.0
        val spread = 1300.0
        val duty = 0.35
        val dark = 0.05
        return List(SEGMENTS) { i ->
            val h = hash(i - 1640531527) // i + 0x9E3779B9 in uint32
            val period = shortest + spread * (h and 0xFFFF) / 65536.0
            val phase = (h ushr 16) / 65536.0
            val color = p.at(phase.toFloat())
            var f = dark
            val x = ((t / period) + phase) % 1.0
            if (x < duty) f = maxOf(dark, sin(PI * x / duty))
            scale(color, f.toFloat())
        }
    }

    /** flicker: a flame — the ring wanders as one thing, each segment a little around it. */
    private fun flicker(p: Palette, t: Double): List<Color> {
        val ts = t / 1000.0
        val whole = 0.75 + 0.25 * wander(ts, 0.0)
        return List(SEGMENTS) { i ->
            val w = wander(ts, i * 0.7)
            val f = whole * (0.8 + 0.2 * w)
            // Along from the far end: the brighter the segment, the deeper into
            // the flame's hot base it is.
            scale(p.along((1 - w).toFloat()), min(1.0, f).toFloat())
        }
    }

    /** embers: flicker with the occasional spark flaring past the top of the palette. */
    private fun embers(p: Palette, t: Double): List<Color> {
        val out = flicker(p, t).toMutableList()
        val every = 9000.0
        val lasts = 260.0
        for (i in 0 until SEGMENTS) {
            val h = hash(i + 1374496513) // i + 0x51ED2701
            val phase = (h.toLong() and 0xFFFFFFFFL) / 4294967296.0
            val x = ((t / every) + phase) % 1.0
            val window = lasts / every
            if (x >= window) continue
            // Up fast and down slower, which is what a spark does.
            var f = x / window
            f = sin(PI * f.pow(0.7))
            out[i] = add(out[i], scale(p.along(0f), (0.7 * f).toFloat()))
        }
        return out
    }

    /** drift: two slow waves decide where each segment sits in the palette — bands that fold. */
    private fun drift(p: Palette, t: Double): List<Color> {
        val ts = t / 1000.0
        return List(SEGMENTS) { i ->
            val pos = i / SEGMENTS.toDouble()
            val x = pos + 0.03 * ts +
                0.10 * sin(2 * PI * (0.07 * ts + pos)) +
                0.06 * sin(2 * PI * (0.13 * ts + 2 * pos))
            val f = 0.45 + 0.55 * (0.5 + 0.5 * sin(2 * PI * (0.11 * ts + 1.5 * pos)))
            scale(p.at(x.toFloat()), f.toFloat())
        }
    }

    /** comet: a bright head clockwise with a decaying tail, palette running along the tail. */
    private fun comet(p: Palette, t: Double): List<Color> {
        val perSegment = 80.0 // two frames per segment at 25 fps
        val tail = doubleArrayOf(1.0, 0.55, 0.3, 0.16, 0.08)
        val head = (((t / perSegment).toLong() % SEGMENTS) + SEGMENTS).toInt() % SEGMENTS
        val out = MutableList(SEGMENTS) { Color.Black }
        for (i in tail.indices) {
            val c = p.shade(i.toFloat() / (tail.size - 1), tail[i].toFloat())
            out[(head - i + SEGMENTS * 2) % SEGMENTS] = c
        }
        return out
    }

    /** chase: the theatre marquee — every third segment lit, stepping round, lamps keep their colour. */
    private fun chase(p: Palette, t: Double): List<Color> {
        val step = 110.0
        val spacing = 3
        val out = MutableList(SEGMENTS) { Color.Black }
        val off = (((t / step).toLong() % spacing) + spacing).toInt() % spacing
        for (lamp in 0 until SEGMENTS / spacing) {
            out[off + lamp * spacing] = p.nth(lamp)
        }
        return out
    }

    /** scanner: an eye sweeps round the ring and back; symmetric glow so the turn reads as slowing. */
    private fun scanner(p: Palette, t: Double): List<Color> {
        val sweep = 900.0 // one pass; there-and-back is twice this
        val width = 2.2f
        var x = (t / sweep) % 2.0
        if (x > 1) x = 2 - x
        val head = x * (SEGMENTS - 1)
        return List(SEGMENTS) { i ->
            val d = ringDist(i, head.toFloat())
            if (d >= width) return@List Color.Black
            val f = 1f - d / width
            p.shade(d / width, f * f)
        }
    }

    /** pinwheel: four arms ninety degrees apart, each carrying its own colour round with it. */
    private fun pinwheel(p: Palette, t: Double): List<Color> {
        val revolution = 1600.0
        val arms = 4
        val span = SEGMENTS / arms
        val at = (t % revolution) / revolution * SEGMENTS
        return List(SEGMENTS) { i ->
            val turns = (i - at) / span
            val arm = turns.roundToInt()
            val d = abs(turns - arm) * span
            if (d < 1) scale(p.nth(arm), (1 - d).toFloat()) else Color.Black
        }
    }

    /** spiral: a brightness gradient round the ring, turning. Lit the whole way round. */
    private fun spiral(p: Palette, t: Double): List<Color> {
        val revolution = 2200.0
        val phase = (t % revolution) / revolution
        return List(SEGMENTS) { i ->
            val behind = ((i.toDouble() / SEGMENTS - phase + 1) % 1 + 1) % 1
            val f = (1 - behind).toFloat()
            p.shade(f, f)
        }
    }

    /** wipe: fills the ring from the front and empties again — counted out, not travelling. */
    private fun wipe(p: Palette, t: Double): List<Color> {
        val period = 2400.0
        var x = 2 * (t % period) / period
        if (x > 1) x = 2 - x
        return arc(x.toFloat(), p.along(x.toFloat()))
    }

    /** helix: two dots turning opposite ways; where they meet they add into one brighter light. */
    private fun helix(p: Palette, t: Double): List<Color> {
        val revolution = 2400.0
        val phase = (t % revolution) / revolution * SEGMENTS
        val out = MutableList(SEGMENTS) { Color.Black }
        dot(out, phase.toFloat(), p.nth(0), 1.6f)
        dot(out, (-phase).toFloat(), p.nth(1), 1.6f)
        return out
    }

    /** orbits: three dots at speeds that do not divide each other, passing and re-passing. */
    private fun orbits(p: Palette, t: Double): List<Color> {
        val speeds = doubleArrayOf(1.0, -0.62, 0.37)
        val revolution = 2600.0
        val tt = t / revolution
        val out = MutableList(SEGMENTS) { Color.Black }
        for (i in speeds.indices) {
            val pos = (((tt * speeds[i]) % 1.0 + 1.0) % 1.0) * SEGMENTS
            dot(out, pos.toFloat(), p.nth(i), 1.4f)
        }
        return out
    }

    // Bounce's whole sequence, worked out once: each bounce keeps 0.55 of its
    // height and lasts with its square root, so the bounces shorten on their
    // own; 700 ms of rest before the next drop.
    private val bounceArcs: List<Triple<Double, Double, Double>> = buildList {
        val first = 900.0
        val keeps = 0.55
        val spent = 0.04
        var cycle = 0.0
        var h = 1.0
        while (h > spent) {
            val d = first * sqrt(h)
            add(Triple(cycle, d, h))
            cycle += d
            h *= keeps
        }
    }
    private val bounceCycle: Double = bounceArcs.sumOf { it.second } + 700.0

    /** bounce: a ball drops and bounces, each parabola quick through the bottom, slow at the top. */
    private fun bounce(p: Palette, t: Double): List<Color> {
        val tt = t % bounceCycle
        var at = 0.0
        for ((start, d, height) in bounceArcs) {
            if (tt < start || tt >= start + d) continue
            val x = 2 * (tt - start) / d - 1
            at = height * (1 - x * x) * (SEGMENTS - 1)
            break
        }
        val out = MutableList(SEGMENTS) { Color.Black }
        dot(out, at.toFloat(), p.along((at / (SEGMENTS - 1)).toFloat()), 1.5f)
        return out
    }

    /** spring: an arc stretches out and lets go — each half overshoots and rings down. */
    private fun spring(p: Palette, t: Double): List<Color> {
        val half = 1300.0
        val rings = 1.5
        val decay = 3.0
        val stretched = 0.72
        val slack = 0.12
        fun step(from: Double, to: Double, x: Double): Double =
            to + (from - to) * exp(-decay * x) * cos(2 * PI * rings * x)
        val tt = t % (2 * half)
        val raw = if (tt < half) {
            step(slack, stretched, tt / half)
        } else {
            step(stretched, slack, (tt - half) / half)
        }
        val x = raw.coerceIn(0.0, 1.0).toFloat()
        return arc(x, p.along(x))
    }

    /** spin: turns the palette round the ring. Rainbow's motion. 1500 ms a revolution. */
    private fun spin(p: Palette, t: Double): List<Color> {
        val revolution = 1500.0
        val phase = (t % revolution) / revolution
        return List(SEGMENTS) { i -> p.at(i / SEGMENTS.toFloat() + phase.toFloat()) }
    }

    /** pacman: eats his way round the ring — dotted ahead, dark behind, chomping as he goes. */
    private fun pacman(p: Palette, t: Double): List<Color> {
        val lap = 3600.0
        val chomp = 220.0
        val at = (t % lap) / lap * SEGMENTS
        val out = MutableList(SEGMENTS) { Color.Black }
        for (i in 0 until SEGMENTS) {
            if (i % 2 == 0 && i > at) out[i] = p.nth(1)
        }
        val open = abs(sin(PI * (t % chomp) / chomp))
        dot(out, at.toFloat(), scale(p.nth(0), (0.55 + 0.45 * (1 - open)).toFloat()), 1.3f)
        return out
    }

    /** alert: three swelling pulses and a rest — one is a glitch, two a heartbeat. */
    private fun alert(p: Palette, t: Double): List<Color> {
        val period = 2000.0
        val pulse = 130.0
        val gap = 90.0
        val tt = t % period
        var f = 0.0
        for (i in 0 until 3) {
            val at = i * (pulse + gap)
            if (tt >= at && tt < at + pulse) f = sin(PI * (tt - at) / pulse)
        }
        return shaded(p, f.toFloat())
    }

    /** beacon: a bright arc sweeps round the ring like a vehicle's warning light. */
    private fun beacon(p: Palette, t: Double): List<Color> {
        val revolution = 900.0
        val width = 2.5f
        val at = (t % revolution) / revolution * SEGMENTS
        return List(SEGMENTS) { i ->
            val d = ringDist(i, at.toFloat())
            if (d >= width) return@List Color.Black
            val f = 1f - d / width
            p.shade(f, f * f)
        }
    }
}

/**
 * Monotonic milliseconds since first frame, running only while the caller is
 * composed. The firmware's frames are a pure function of elapsed time; this is
 * that timeline at screen frame rate.
 */
@Composable
internal fun rememberRingClock(): Double {
    var ms by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now
                ms = (now - start) / 1_000_000.0
            }
        }
    }
    return ms
}

/**
 * Draws a frame as the Dot's twelve LEDs: one arc per segment, small dark
 * gaps between them, segment 0 at the top running clockwise.
 */
internal fun DrawScope.drawSegmentRing(
    segs: List<Color>,
    cx: Float,
    cy: Float,
    radius: Float,
    stroke: Float,
    gapDeg: Float = 5f,
) {
    val sweep = 360f / RingFx.SEGMENTS
    for (i in 0 until RingFx.SEGMENTS) {
        drawArc(
            color = segs.getOrElse(i) { Color.Black },
            startAngle = -90f + i * sweep + gapDeg / 2,
            sweepAngle = sweep - gapDeg,
            useCenter = false,
            style = Stroke(width = stroke),
            topLeft = Offset(cx - radius, cy - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        )
    }
}
