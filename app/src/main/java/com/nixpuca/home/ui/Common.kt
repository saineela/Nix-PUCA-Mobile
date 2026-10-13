package com.nixpuca.home.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nixpuca.home.data.JsonCard

/**
 * Black, white and grey — the console's official identity — plus a user
 * accent that is deliberately state-backed: picking a colour in Settings
 * repaints every accent-touched composable without recreating the palette.
 */
object NixPalette {
    /** Accent style id → light/dark colour. "mono" keeps official B&W. */
    val AccentOptions: List<Pair<String, String>> = listOf(
        "mono" to "Mono",
        "violet" to "Violet",
        "cyan" to "Cyan",
        "amber" to "Amber",
        "rose" to "Rose",
        "green" to "Green",
        "teal" to "Teal",
        "indigo" to "Indigo",
        "crimson" to "Crimson",
    )

    private data class AccentPair(val light: Color, val dark: Color)

    private val accentColors = mapOf(
        "mono" to AccentPair(Color(0xFF000000), Color(0xFFFFFFFF)),
        "violet" to AccentPair(Color(0xFF6C4CF1), Color(0xFF9F86FF)),
        "cyan" to AccentPair(Color(0xFF0E7CE8), Color(0xFF54B4FF)),
        "amber" to AccentPair(Color(0xFFC77700), Color(0xFFF4B942)),
        "rose" to AccentPair(Color(0xFFD6336C), Color(0xFFFF7AA2)),
        "green" to AccentPair(Color(0xFF1B8A4B), Color(0xFF4CD98B)),
        "teal" to AccentPair(Color(0xFF0F7F78), Color(0xFF3FD5CB)),
        "indigo" to AccentPair(Color(0xFF3D4FD6), Color(0xFF8E9BFF)),
        "crimson" to AccentPair(Color(0xFFB4182C), Color(0xFFFF6B7A)),
    )

    // Surface roles — all state-backed so a theme flip repaints the whole app.
    var Ink by mutableStateOf(Color(0xFF000000))
        private set
    var Paper by mutableStateOf(Color(0xFFFFFFFF))
        private set
    var Mist by mutableStateOf(Color(0xFFF3F3F3))
        private set
    var Line by mutableStateOf(Color(0xFFDDDDDD))
        private set
    var Muted by mutableStateOf(Color(0xFF666666))
        private set
    var Ok by mutableStateOf(Color(0xFF1B5E20))
        private set
    var Bad by mutableStateOf(Color(0xFFB00020))
        private set

    var dark by mutableStateOf(false)
        private set

    /** Currently selected accent (state-backed so Compose recomposes). */
    var Accent by mutableStateOf(Color.Black)
        private set

    /** Text/glyph colour that reads on top of [Accent]. */
    var OnAccent by mutableStateOf(Color.White)
        private set

    private var accentId = "mono"

    fun applyAccent(id: String) {
        accentId = id
        Accent = (accentColors[id] ?: accentColors.getValue("mono")).let {
            if (dark) it.dark else it.light
        }
        // Mono inverts with the theme, so its on-colour flips too.
        OnAccent = if (id == "mono") {
            if (dark) Color(0xFF101014) else Color.White
        } else {
            Color.White
        }
    }

    fun applyTheme(darkMode: Boolean) {
        dark = darkMode
        if (darkMode) {
            Ink = Color(0xFFEDEDF2)
            Paper = Color(0xFF101014)
            Mist = Color(0xFF1A1A21)
            Line = Color(0xFF2C2C35)
            Muted = Color(0xFF9A9AA6)
            Ok = Color(0xFF4CD98B)
            Bad = Color(0xFFFF6B7A)
        } else {
            Ink = Color(0xFF000000)
            Paper = Color(0xFFFFFFFF)
            Mist = Color(0xFFF3F3F3)
            Line = Color(0xFFDDDDDD)
            Muted = Color(0xFF666666)
            Ok = Color(0xFF1B5E20)
            Bad = Color(0xFFB00020)
        }
        // Re-resolve the accent against the new surface family.
        applyAccent(accentId)
    }
}

/**
 * Clickable with a springy press-down scale plus the normal ripple. Cards
 * that physically respond to a finger read as instant, which matters more
 * for perceived speed than shaving a round trip off the wire.
 */
@Composable
fun Modifier.tapScale(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * Titled card with an optional refresh affordance and loading spinner,
 * styled as liquid glass: a soft sheen fading down the panel, a gradient
 * edge and a low accent-tinted lift off the page.
 */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = NixPalette.Accent.copy(alpha = 0.28f),
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = NixPalette.Mist),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(NixPalette.Line, NixPalette.Line.copy(alpha = 0.35f)),
            ),
        ),
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (NixPalette.dark) 0.07f else 0.45f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = NixPalette.Muted,
                        )
                    }
                }
                if (loading) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = NixPalette.Ink,
                    )
                }
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh $title")
                    }
                }
                actions()
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Small status pill: label, value, and a check/warn glyph. */
@Composable
fun StatusChip(label: String, value: String, ok: Boolean?) {
    Surface(
        shape = RoundedCornerShape(50),
        color = NixPalette.Paper,
        border = androidx.compose.foundation.BorderStroke(1.dp, NixPalette.Line),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (ok) {
                true -> Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "ok",
                    Modifier.size(13.dp),
                    tint = NixPalette.Ok,
                )
                false -> Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = "problem",
                    Modifier.size(13.dp),
                    tint = NixPalette.Bad,
                )
                else -> Unit
            }
            Column {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = NixPalette.Muted)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Error line shown under a card when a request failed. */
@Composable
fun ErrorNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = NixPalette.Bad,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Neutral empty-state line. */
@Composable
fun EmptyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = NixPalette.Muted,
    )
}

/** Wraps status chips across lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(chips: List<com.nixpuca.home.StatusChip>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            StatusChip(chip.label, chip.value, chip.ok)
        }
    }
}

/**
 * Renders one console record as a clean card: a humanised title, short
 * values as wrapping field chips, longer values as labelled blocks. The raw
 * JSON payload is intentionally never shown — records should read like
 * information, not a dump.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JsonCardItem(card: JsonCard, modifier: Modifier = Modifier) {
    val chips = card.fields.filter { it.second.length <= 48 && !it.second.contains('\n') }
    val blocks = card.fields.filter { chip -> chip !in chips }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NixPalette.Paper),
        border = androidx.compose.foundation.BorderStroke(1.dp, NixPalette.Line),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = NixPalette.Ink,
            )
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chips.forEach { (key, value) ->
                        FieldChip(label = humanKey(key), value = value)
                    }
                }
            }
            blocks.forEach { (key, value) ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = humanKey(key).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = NixPalette.Ink,
                )
            }
        }
    }
}

/** One `label value` chip used for short record fields. */
@Composable
private fun FieldChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = NixPalette.Mist,
        border = androidx.compose.foundation.BorderStroke(1.dp, NixPalette.Line),
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = NixPalette.Muted)
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = NixPalette.Ink,
            )
        }
    }
}

/** "last_fed" → "Last fed" so record fields read like language, not keys. */
private fun humanKey(key: String): String =
    key.replace('_', ' ').replace('-', ' ').split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        .ifBlank { key }

/** Compact key/value list used for health, models and similar payloads. */
@Composable
fun KeyValueList(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        rows.forEach { (key, value) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.bodySmall,
                    color = NixPalette.Muted,
                    modifier = Modifier.width(132.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
