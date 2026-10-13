package com.nixpuca.home.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nixpuca.home.NixViewModel
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

/**
 * Settings › Hardware: the host's hardware (GPU, memory, CPU) read from the
 * console's health payload, drawn with a rotating GPU hero, live gauges and
 * animated fill bars, plus what this phone reports about itself.
 */
@Composable
fun HardwareScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    // Prime once, then keep polling so the gauges and bars stay live while
    // the page is on screen.
    LaunchedEffect(Unit) {
        vm.loadHealth()
        while (true) {
            kotlinx.coroutines.delay(5_000)
            vm.loadHealth()
        }
    }

    val payload = vm.healthPayload
    val info = rememberHardwareInfo(payload).let { remembered ->
        // Older consoles don't put the name in the health block; Home already
        // extracted it, so fall back to that instead of showing a blank card.
        if (remembered.gpuName == null && vm.gpuName != null) {
            remembered.copy(gpuName = vm.gpuName)
        } else remembered
    }
    val phone = rememberPhoneInfo()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Hardware monitor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Live from ${android.net.Uri.parse(vm.config.baseUrl).host ?: "your hub"} · this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = NixPalette.Muted,
                )
            }
            if (vm.loading["health"] == true) {
                androidx.compose.material3.CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = NixPalette.Ink,
                )
            } else {
                IconButton(onClick = { vm.loadHealth() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh hardware")
                }
            }
        }

        vm.dataError["health"]?.let { ErrorNote(it) }

        // ------------------------------------------------------- GPU hero
        GpuHeroCard(
            name = info.gpuName,
            vendor = detectGpuVendor(info.gpuName),
            load = info.gpuLoad,
            vramUsedGb = info.gpuVramUsedGb,
            vramTotalGb = info.gpuVramTotalGb,
            temperature = info.gpuTempC,
        )

        // ---------------------------------------------------- host gauges
        if (info.gauges.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                info.gauges.take(2).forEach { gauge ->
                    GaugeCard(
                        label = gauge.label,
                        fraction = gauge.fraction,
                        readout = gauge.readout,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (info.gauges.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // --------------------------------------------------- memory fills
        if (info.meters.isNotEmpty()) {
            SectionCard(
                title = "Memory",
                subtitle = "How full each pool on the host is",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    info.meters.forEach { meter ->
                        FillBar(label = meter.label, used = meter.used, total = meter.total)
                    }
                }
            }
        }

        // -------------------------------------------------- host spec list
        if (info.rows.isNotEmpty()) {
            SectionCard(title = "Host details", subtitle = "Reported by the console's health check") {
                KeyValueList(info.rows)
            }
        }

        if (payload == null && vm.loading["health"] != true) {
            SectionCard(title = "Host") {
                EmptyNote("No health payload yet — pull it with the refresh action above.")
            }
        }

        // ------------------------------------------------------ this phone
        SectionCard(title = "This phone", subtitle = "Reported by Android") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FillBar(
                    label = "Memory in use",
                    used = phone.usedBytes,
                    total = phone.totalBytes,
                )
                KeyValueList(phone.rows)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// --------------------------------------------------------------- host model

/** One percentage readout drawn as a ring gauge. */
private data class Gauge(val label: String, val fraction: Float, val readout: String)

/** One "used / total" bar. */
private data class Meter(val label: String, val used: Double, val total: Double)

private data class HardwareInfo(
    val gpuName: String?,
    val gpuLoad: Float?,
    val gpuVramUsedGb: Float?,
    val gpuVramTotalGb: Float?,
    val gpuTempC: Float?,
    val gauges: List<Gauge>,
    val meters: List<Meter>,
    val rows: List<Pair<String, String>>,
)

/**
 * Reads whatever shape this console build gives us: the health payload's
 * `gpu` / `cpu` / `memory` / `system` blocks are flattened into rows, and any
 * percentage- or byte-shaped number is additionally rendered graphically.
 */
@Composable
private fun rememberHardwareInfo(payload: JSONObject?): HardwareInfo = remember(payload) {
    if (payload == null) {
        return@remember HardwareInfo(null, null, null, null, null, emptyList(), emptyList(), emptyList())
    }

    val rows = mutableListOf<Pair<String, String>>()
    val gauges = mutableListOf<Gauge>()
    val meters = mutableListOf<Meter>()

    fun addRows(prefix: String, obj: JSONObject?) {
        if (obj == null) return
        val names = obj.keys()
        while (names.hasNext()) {
            val key = names.next()
            val value = obj.opt(key) ?: continue
            if (value is JSONObject || value is JSONArray) continue
            if (value === JSONObject.NULL) continue
            val label = (if (prefix.isEmpty()) key else "$prefix $key")
                .replace('_', ' ')
                .split(' ')
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            rows.add(label to value.toString())
        }
    }

    val gpu = payload.optJSONObject("gpu")
    val cpu = payload.optJSONObject("cpu") ?: payload.optJSONObject("processor")
    val memory = payload.optJSONObject("memory") ?: payload.optJSONObject("ram")
    val system = payload.optJSONObject("system") ?: payload.optJSONObject("host")

    addRows("", gpu)
    addRows("CPU", cpu)
    addRows("Memory", memory)
    addRows("System", system)

    val gpuName = gpu?.opt("name")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: payload.opt("gpu_name")?.toString()?.takeIf { it.isNotBlank() && it != "null" }

    // Percent-shaped values (0..1 or 0..100) become ring gauges.
    fun percentGauge(label: String, raw: Any?): Float? {
        val n = (raw as? Number)?.toDouble() ?: return null
        if (!n.isFinite() || n < 0) return null
        val fraction = if (n <= 1.0) n else n / 100.0
        if (fraction > 1.0) return null
        return fraction.toFloat()
    }

    fun scanPercentages(prefix: String, obj: JSONObject?) {
        if (obj == null) return
        val names = obj.keys()
        while (names.hasNext()) {
            val key = names.next()
            val value = obj.opt(key) ?: continue
            if (value is JSONObject) {
                scanPercentages(if (prefix.isEmpty()) key else "$prefix $key", value)
                continue
            }
            val lower = key.lowercase()
            val looksLikePercent = lower.contains("percent") || lower.contains("usage") ||
                lower.contains("load") || lower.contains("util") || lower.contains("duty")
            if (!looksLikePercent) continue
            val fraction = percentGauge(key, value) ?: continue
            val label = (if (prefix.isEmpty()) key else "$prefix $key")
                .replace('_', ' ')
                .split(' ')
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            gauges.add(Gauge(label, fraction, "${Math.round(fraction * 100)}%"))
        }
    }
    scanPercentages("", gpu)
    scanPercentages("CPU", cpu)
    scanPercentages("", memory)

    // Byte-shaped totals become fill bars (memory pools, VRAM, swap).
    fun addMeter(label: String, used: Double?, total: Double?) {
        if (used == null || total == null || total <= 0.0) return
        if (used < 0 || used > total * 1.5) return
        meters.add(Meter(label, used, total))
    }

    fun number(obj: JSONObject?, vararg keys: String): Double? {
        if (obj == null) return null
        for (key in keys) {
            val raw = obj.opt(key)
            val n = (raw as? Number)?.toDouble() ?: continue
            if (n.isFinite() && n >= 0) return n
        }
        return null
    }

    // VRAM lives on the GPU block in most shapes.
    val vramTotalGb = number(gpu, "memory_total", "vram_total", "total_memory", "memory_total_mb")
        ?.let { if (it > 1024) it / 1024.0 else it }
        ?.toFloat()
    val vramUsedGb = number(gpu, "memory_used", "vram_used", "used_memory", "memory_used_mb")
        ?.let { if (it > 1024) it / 1024.0 else it }
        ?.toFloat()
    if (vramUsedGb != null && vramTotalGb != null) {
        addMeter("GPU memory", vramUsedGb.toDouble(), vramTotalGb.toDouble())
    }

    addMeter(
        "System memory",
        number(memory, "used", "used_bytes", "used_mb", "bytes_used"),
        number(memory, "total", "total_bytes", "total_mb", "bytes_total"),
    )
    addMeter(
        "Swap",
        number(memory, "swap_used", "swap_used_bytes"),
        number(memory, "swap_total", "swap_total_bytes"),
    )

    // GPU gauge built from utilisation + temperature when present.
    val gpuLoadRaw = number(gpu, "utilization", "load", "usage", "gpu_load", "util")
    val gpuLoad = gpuLoadRaw?.let { raw ->
        (if (raw <= 1.0) raw else raw / 100.0).toFloat().coerceIn(0f, 1f)
    }
    val gpuTemp = number(gpu, "temperature", "temp", "gpu_temp", "temp_c")
        ?.let { raw -> if (raw in 0.0..200.0) raw.toFloat() else null }
    if (gpuLoad != null && gauges.none { it.label.equals("load", true) }) {
        gauges.add(0, Gauge("GPU load", gpuLoad, "${Math.round(gpuLoad * 100)}%"))
    }

    // Hardware identity rows that are always worth showing.
    fun put(label: String, value: String?) {
        if (value.isNullOrBlank() || value == "null") return
        if (rows.any { it.first.equals(label, true) }) return
        rows.add(label to value)
    }
    gpuName?.let { put("GPU", it) }
    put("Driver", gpu?.opt("driver")?.toString())
    put("Cores", cpu?.opt("cores")?.toString() ?: cpu?.opt("core_count")?.toString())
    put("CPU", cpu?.opt("name")?.toString() ?: cpu?.opt("model")?.toString())
    put("Platform", system?.opt("platform")?.toString() ?: system?.opt("os")?.toString())
    put("Machine", system?.opt("machine")?.toString())
    put("Python", system?.opt("python")?.toString() ?: system?.opt("python_version")?.toString())
    put("Hostname", system?.opt("hostname")?.toString())

    HardwareInfo(
        gpuName = gpuName,
        gpuLoad = gpuLoad,
        gpuVramUsedGb = vramUsedGb,
        gpuVramTotalGb = vramTotalGb,
        gpuTempC = gpuTemp,
        gauges = gauges.distinctBy { it.label }.take(4),
        meters = meters.distinctBy { it.label },
        rows = rows.distinctBy { it.first }.take(24),
    )
}

/**
 * The GPU hero: brand-coloured card with a die that slowly turns, a fan that
 * spins faster under load, plus live utilisation / VRAM / temperature readouts.
 */
@Composable
private fun GpuHeroCard(
    name: String?,
    vendor: GpuVendor,
    load: Float?,
    vramUsedGb: Float?,
    vramTotalGb: Float?,
    temperature: Float?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "gpu-hero")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val fan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        // The fan speeds up as the reported load climbs; idle cards idle.
        animationSpec = infiniteRepeatable(
            tween(((2200 - 1500 * (load ?: 0.2f)).toInt()).coerceIn(400, 2600), easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "fan",
    )
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "glow",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0B0B0F),
    ) {
        Box {
            Canvas(Modifier.fillMaxWidth().height(178.dp)) {
                val brand = vendor.brand
                // Brand wash behind everything.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(brand.copy(alpha = 0.40f * glow), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                        radius = size.minDimension * 1.1f,
                    ),
                )
                // Rotating die: a square that turns slowly on the board.
                val dieSize = 74f
                val dcx = size.width * 0.76f
                val dcy = size.height * 0.52f
                rotate(spin, pivot = Offset(dcx, dcy)) {
                    drawRect(
                        color = brand,
                        topLeft = Offset(dcx - dieSize / 2, dcy - dieSize / 2),
                        size = Size(dieSize, dieSize),
                    )
                    drawRect(
                        color = Color(0xFF0B0B0F),
                        topLeft = Offset(dcx - dieSize / 2 + 10, dcy - dieSize / 2 + 10),
                        size = Size(dieSize - 20, dieSize - 20),
                    )
                    // Silicon traces across the die.
                    for (i in -2..2) {
                        drawLine(
                            color = brand.copy(alpha = 0.8f),
                            start = Offset(dcx - dieSize / 2 + 14, dcy + i * 11f),
                            end = Offset(dcx + dieSize / 2 - 14, dcy + i * 11f),
                            strokeWidth = 3f,
                        )
                    }
                }
                // Spinning fan beside the die.
                val fcx = size.width * 0.30f
                val fcy = size.height * 0.52f
                val fr = 42f
                drawCircle(
                    color = Color(0xFF1A1A21),
                    radius = fr + 6f,
                    center = Offset(fcx, fcy),
                )
                drawCircle(
                    color = brand.copy(alpha = 0.35f),
                    radius = fr,
                    center = Offset(fcx, fcy),
                    style = Stroke(width = 4f),
                )
                rotate(fan, pivot = Offset(fcx, fcy)) {
                    for (i in 0 until 7) {
                        val a = Math.toRadians(i * (360.0 / 7.0))
                        drawLine(
                            color = Color(0xFF555563),
                            start = Offset(fcx, fcy),
                            end = Offset(
                                fcx + (fr - 6f) * cos(a).toFloat(),
                                fcy + (fr - 6f) * sin(a).toFloat(),
                            ),
                            strokeWidth = 9f,
                            cap = StrokeCap.Round,
                        )
                    }
                    drawCircle(color = Color(0xFF3A3A44), radius = 8f, center = Offset(fcx, fcy))
                }
                // Board edge + connector pins along the bottom.
                drawLine(
                    color = brand.copy(alpha = 0.5f),
                    start = Offset(16f, size.height - 22f),
                    end = Offset(size.width - 16f, size.height - 22f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
                var pinX = 26f
                while (pinX < size.width - 26f) {
                    drawLine(
                        color = brand.copy(alpha = 0.8f),
                        start = Offset(pinX, size.height - 22f),
                        end = Offset(pinX, size.height - 8f),
                        strokeWidth = 4f,
                    )
                    pinX += 16f
                }
            }

            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(6.dp), color = vendor.brand) {
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
                    modifier = Modifier.fillMaxWidth(0.62f),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroStat(
                        label = "LOAD",
                        value = load?.let { "${Math.round(it * 100)}%" } ?: "—",
                    )
                    Spacer(Modifier.width(14.dp))
                    HeroStat(
                        label = "VRAM",
                        value = when {
                            vramUsedGb != null && vramTotalGb != null ->
                                "%.1f/%.1f GB".format(vramUsedGb, vramTotalGb)
                            vramTotalGb != null -> "%.1f GB".format(vramTotalGb)
                            else -> "—"
                        },
                    )
                    Spacer(Modifier.width(14.dp))
                    HeroStat(
                        label = "TEMP",
                        value = temperature?.let { "${Math.round(it)}°C" } ?: "—",
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = Color(0xFF7A7A86),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ----------------------------------------------------------------- gauges

/** Ring gauge that animates from zero to [fraction] whenever it changes. */
@Composable
private fun GaugeCard(
    label: String,
    fraction: Float,
    readout: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "gauge")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "pulse",
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Column(
            Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(96.dp)) {
                    val stroke = Stroke(width = 10f, cap = StrokeCap.Round)
                    val inset = 12f
                    val arc = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(
                        color = NixPalette.Line,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arc,
                        style = stroke,
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(NixPalette.Accent, NixPalette.Accent.copy(alpha = 0.55f)),
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arc,
                        style = stroke,
                    )
                }
                Text(
                    text = readout,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NixPalette.Ink,
                    modifier = Modifier.alpha(pulse),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
                letterSpacing = 1.sp,
            )
        }
    }
}

/** Horizontal "used of total" bar that fills with animation. */
@Composable
private fun FillBar(label: String, used: Double, total: Double, modifier: Modifier = Modifier) {
    val fraction = if (total > 0) (used / total).coerceIn(0.0, 1.0).toFloat() else 0f
    // Sweeps to the new reading whenever the numbers change.
    val shown by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction,
        animationSpec = androidx.compose.animation.core.tween(1100, easing = EaseInOutCubic),
        label = "fill",
    )
    val hot = fraction >= 0.85f

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${formatBytes(used)} / ${formatBytes(total)} · ${Math.round(fraction * 100)}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (hot) NixPalette.Bad else NixPalette.Muted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(50))
                .background(NixPalette.Line),
        ) {
            val barColor = if (hot) NixPalette.Bad else NixPalette.Accent
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .height(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(listOf(barColor, barColor.copy(alpha = 0.65f))),
                    ),
            )
        }
    }
}

private fun formatBytes(value: Double): String = when {
    value >= 1e9 -> "%.1f GB".format(value / 1e9)
    value >= 1e6 -> "%.0f MB".format(value / 1e6)
    value >= 1e3 -> "%.0f kB".format(value / 1e3)
    else -> "%.0f B".format(value)
}

// ------------------------------------------------------------- this phone

private data class PhoneInfo(val usedBytes: Double, val totalBytes: Double, val rows: List<Pair<String, String>>)

@Composable
private fun rememberPhoneInfo(): PhoneInfo {
    val context = androidx.compose.ui.platform.LocalContext.current
    var info by remember(context) { androidx.compose.runtime.mutableStateOf(readPhone(context)) }
    // Memory moves while the page is open — re-read so the bar is live too.
    LaunchedEffect(context) {
        while (true) {
            kotlinx.coroutines.delay(3_000)
            info = readPhone(context)
        }
    }
    return info
}

private fun readPhone(ctx: Context): PhoneInfo {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mem = ActivityManager.MemoryInfo()
    am.getMemoryInfo(mem)
    val rows = mutableListOf(
        "Model" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        "Android" to "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
        "Hardware" to android.os.Build.HARDWARE.orEmpty().ifBlank { "—" },
        "Cores" to Runtime.getRuntime().availableProcessors().toString(),
        "Device memory" to formatBytes(mem.totalMem.toDouble()),
    )
    if (mem.lowMemory) rows.add("Low memory" to "yes")
    return PhoneInfo(
        usedBytes = (mem.totalMem - mem.availMem).toDouble(),
        totalBytes = mem.totalMem.toDouble(),
        rows = rows,
    )
}
