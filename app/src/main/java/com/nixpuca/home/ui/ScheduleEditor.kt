package com.nixpuca.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nixpuca.home.data.TimeFormat
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Graphical event entry: snapping scroll wheels for date and time, quick
 * chips for the common answers, and a live preview of exactly what will be
 * saved. Nothing to type except the event's title — the design rule this
 * screen is built around.
 */

/** "in 3h 12m" / "in 3 days" style countdown from now until [target]. */
internal fun countdownLabel(
    target: LocalDateTime,
    now: LocalDateTime = LocalDateTime.now(),
): String {
    val mins = java.time.Duration.between(now, target).toMinutes()
    return when {
        mins < -1 -> ""
        mins < 1 -> "any moment now"
        mins < 60 -> "in ${mins}m"
        mins < 60 * 24 -> "in ${mins / 60}h ${mins % 60}m"
        mins < 60 * 24 * 7 -> {
            val days = mins / (60 * 24)
            "in $days day" + (if (days > 1) "s" else "")
        }
        else -> {
            val days = mins / (60 * 24)
            "in $days days"
        }
    }
}

/** "Friday, 9 October 2026" — the long form used in previews. */
internal fun fullDateLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))

/** Short weekday chip label: "Fri". */
internal fun weekdayShort(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))

/**
 * One scrolling wheel: items fly past a fixed centre band, settle with a
 * pixel-perfect snap, and the item under the band drives [onSelected]. The
 * same component carries the date wheels (month/day/year) and the time
 * wheels (hour/minute/AM-PM).
 */
@Composable
internal fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 40.dp,
    visibleItems: Int = 5,
    fade: Color = NixPalette.Mist,
) {
    val count = items.size
    if (count == 0) return
    val selected = selectedIndex.coerceIn(0, count - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected)

    /** Index of the item currently sitting under the centre band. */
    fun centreIndex(): Int {
        val info = listState.layoutInfo
        val shown = info.visibleItemsInfo
        if (shown.isEmpty()) return -1
        val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        return shown.minByOrNull { abs((it.offset + it.size / 2f) - centre) }?.index ?: -1
    }

    // Wheel → world: report the centred item as the current selection.
    // The guard runs against what we last told the world, not `selected` —
    // that prop is only read when this effect starts, so a wheel dragged back
    // to its opening item would never re-report and the world would keep the
    // value from one step ago.
    var reported by remember { mutableStateOf(selected) }
    LaunchedEffect(listState, count) {
        snapshotFlow { centreIndex() }
            .distinctUntilChanged()
            .collect { idx ->
                if (idx in 0 until count && idx != reported) {
                    reported = idx
                    onSelected(idx)
                }
            }
    }

    // World → wheel: quick chips (and day-count changes) re-centre the wheel.
    // Only fires for changes that did NOT come from the wheel itself, so a
    // drag is never yanked away mid-scroll.
    LaunchedEffect(listState, selected, count) {
        if (selected != reported) {
            if (centreIndex() != selected) listState.scrollToItem(selected)
            reported = selected
        }
    }

    Box(
        modifier
            .height(itemHeight * visibleItems)
            .widthIn(min = 56.dp),
    ) {
        LazyColumn(
            state = listState,
            // Drag or fling, then let go: Compose lands the nearest row on the
            // band itself. Hand-rolled settling kept leaving the wheel a few
            // pixels off after a fast fling.
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItems - 1) / 2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(count) { i ->
                val distance = abs(i - selected).coerceAtMost(3)
                val isCentre = distance == 0
                Box(
                    Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = items[i],
                        style = if (isCentre) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCentre) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCentre) NixPalette.Ink
                        else NixPalette.Muted.copy(alpha = (1f - distance * 0.22f).coerceAtLeast(0.35f)),
                        maxLines = 1,
                    )
                }
            }
        }

        // Centre selection band.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .background(NixPalette.Accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
        )

        // Fade the top and bottom edges so the wheels read as a cylinder.
        val fadeHeight = itemHeight * (visibleItems - 1) / 2
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(fadeHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(fade, fade.copy(alpha = 0f)),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(fadeHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(fade.copy(alpha = 0f), fade),
                    ),
                ),
        )
    }
}

/** What the editor opens with: identity plus its current date/time. */
internal data class EventDraft(
    val title: String,
    /** YYYY-MM-DD. */
    val date: String,
    /** "" = all-day, else HH:mm. */
    val time: String,
    /** False for imported (Google / phone) events, which cannot be moved here. */
    val editable: Boolean = true,
)

/**
 * The event editor: title with emoji quick-picks, a date card (quick chips +
 * month/day/year wheels), a time card (all-day switch + hour/minute/AM-PM
 * wheels), and a live preview card that spells out exactly what will be
 * saved and how far away it is.
 */
@Composable
internal fun EventEditorDialog(
    heading: String,
    confirmLabel: String,
    initial: EventDraft,
    busy: Boolean,
    notice: String?,
    noticeIsError: Boolean = false,
    onConfirm: (title: String, date: String, time: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val parsedDate = remember(initial.date) {
        try {
            LocalDate.parse(initial.date)
        } catch (_: Throwable) {
            LocalDate.now()
        }
    }
    val parsedTime = remember(initial.time) {
        val text = initial.time.trim()
        if (text.length >= 5) {
            val h = text.substring(0, 2).toIntOrNull() ?: 9
            val m = text.substring(3, 5).toIntOrNull() ?: 0
            h.coerceIn(0, 23) to m.coerceIn(0, 59)
        } else {
            9 to 0
        }
    }

    var title by remember(initial) { mutableStateOf(initial.title) }
    var date by remember(initial) { mutableStateOf(parsedDate) }
    var allDay by remember(initial) { mutableStateOf(initial.time.isBlank()) }
    var hour by remember(initial) { mutableStateOf(parsedTime.first) }
    var minute by remember(initial) { mutableStateOf(parsedTime.second) }
    var amPm by remember(initial) { mutableStateOf(if (parsedTime.first >= 12) 1 else 0) }

    val use24 = TimeFormat.use24Hour
    val today = LocalDate.now()

    val monthNames = remember {
        java.time.Month.entries.map {
            it.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
        }
    }
    val yearStart = remember(parsedDate) { minOf(parsedDate.year, today.year) - 1 }
    val years = remember(yearStart) { (yearStart until yearStart + 12).map { it.toString() } }
    val dayCount = remember(date) { date.lengthOfMonth() }
    val dayItems = remember(dayCount) { (1..dayCount).map { it.toString() } }
    val hourItems = remember(use24) {
        if (use24) (0..23).map { "%02d".format(it) } else (1..12).map { it.toString() }
    }
    val minuteItems = remember { (0..59).map { "%02d".format(it) } }
    val amPmItems = remember { listOf("AM", "PM") }

    // Selected indices, derived so every wheel repaints when another one
    // (or a quick chip) moves the date/time.
    val monthSel = date.monthValue - 1
    val daySel = date.dayOfMonth - 1
    val yearSel = (date.year - yearStart).coerceIn(0, years.lastIndex)
    val hourSel = if (use24) hour else if (hour % 12 == 0) 11 else hour % 12 - 1
    val minuteSel = minute

    val previewTime = "%02d:%02d".format(hour, minute)
    val previewDate = date.atTime(if (allDay) 0 else hour, if (allDay) 0 else minute)
    val countdown = if (previewDate.isBefore(LocalDateTime.now())) "" else countdownLabel(previewDate)

    fun setDate(newDate: LocalDate) {
        // Clamp the day when a shorter month/year is chosen (Jan 31 → Feb).
        val maxDay = newDate.lengthOfMonth()
        date = newDate.withDayOfMonth(newDate.dayOfMonth.coerceAtMost(maxDay))
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = NixPalette.Paper,
            border = BorderStroke(1.dp, NixPalette.Line),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ---------------------------------------------------- header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NixPalette.Accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("📅", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            heading,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (initial.editable) "Spin the wheels — nothing to type"
                            else "Imported event",
                            style = MaterialTheme.typography.labelSmall,
                            color = NixPalette.Muted,
                        )
                    }
                    if (busy) {
                        Spacer(Modifier.weight(1f))
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NixPalette.Ink)
                    }
                }

                // ----------------------------------------------------- title
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabelledField(title = "What's happening") {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = { Text("Dentist appointment") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("📅", "⏰", "🎂", "🎉", "🏃", "🍽️", "✈️", "💊", "📞", "🛒", "🧹", "🎬", "💼", "🌱", "🎓")
                            .forEach { emoji ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = NixPalette.Mist,
                                    border = BorderStroke(1.dp, NixPalette.Line),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable(enabled = !busy) {
                                            title = (title.trimEnd() + " " + emoji).trimStart()
                                        },
                                ) {
                                    Text(
                                        emoji,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    )
                                }
                            }
                    }
                }

                // ----------------------------------------------------- date
                SectionBlock(label = "Date") {
                    Text(
                        text = fullDateLabel(date),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QuickChip("Today") { setDate(today) }
                        QuickChip("Tomorrow") { setDate(today.plusDays(1)) }
                        QuickChip("Weekend") {
                            // Mon=1 … Sun=7: roll to this coming Saturday.
                            val dow = today.dayOfWeek.value
                            val daysUntilSat = if (dow >= 6) 0 else 6 - dow
                            setDate(today.plusDays(daysUntilSat.toLong()))
                        }
                        QuickChip("Next week") { setDate(today.plusDays(7)) }
                        QuickChip("+1 month") { setDate(today.plusMonths(1)) }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        WheelPicker(
                            items = monthNames,
                            selectedIndex = monthSel,
                            onSelected = { setDate(date.withMonth(it + 1)) },
                            modifier = Modifier.weight(1.5f),
                            fade = NixPalette.Mist,
                        )
                        WheelPicker(
                            items = dayItems,
                            selectedIndex = daySel,
                            onSelected = { idx ->
                                val wanted = idx + 1
                                date = date.withDayOfMonth(wanted.coerceAtMost(date.lengthOfMonth()))
                            },
                            modifier = Modifier.weight(1f),
                            fade = NixPalette.Mist,
                        )
                        WheelPicker(
                            items = years,
                            selectedIndex = yearSel,
                            onSelected = { setDate(date.withYear(yearStart + it)) },
                            modifier = Modifier.weight(1f),
                            fade = NixPalette.Mist,
                        )
                    }
                }

                // ----------------------------------------------------- time
                SectionBlock(label = "Time") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (allDay) "All-day" else TimeFormat.format(previewTime),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (allDay) "The event lasts the whole day"
                                else if (use24) "24-hour clock" else "12-hour clock",
                                style = MaterialTheme.typography.labelSmall,
                                color = NixPalette.Muted,
                            )
                        }
                        Text(
                            "All-day",
                            style = MaterialTheme.typography.labelMedium,
                            color = NixPalette.Muted,
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = allDay,
                            onCheckedChange = { allDay = it },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = NixPalette.Accent,
                            ),
                        )
                    }
                    if (!allDay) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf("08:00", "12:00", "17:00", "19:00", "21:00").forEach { hhmm ->
                                QuickChip(TimeFormat.format(hhmm)) {
                                    hour = hhmm.substring(0, 2).toInt()
                                    minute = hhmm.substring(3, 5).toInt()
                                    amPm = if (hour >= 12) 1 else 0
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            WheelPicker(
                                items = hourItems,
                                selectedIndex = hourSel,
                                onSelected = { idx ->
                                    if (use24) {
                                        hour = idx
                                    } else {
                                        val h12 = idx + 1 // 1..12
                                        val base = h12 % 12
                                        hour = if (amPm == 1) base + 12 else base
                                    }
                                },
                                modifier = Modifier.weight(if (use24) 1.2f else 1f),
                                fade = NixPalette.Mist,
                            )
                            WheelPicker(
                                items = minuteItems,
                                selectedIndex = minuteSel,
                                onSelected = { minute = it },
                                modifier = Modifier.weight(1f),
                                fade = NixPalette.Mist,
                            )
                            if (!use24) {
                                WheelPicker(
                                    items = amPmItems,
                                    selectedIndex = amPm,
                                    onSelected = { idx ->
                                        amPm = idx
                                        if (idx == 1 && hour < 12) hour += 12
                                        if (idx == 0 && hour >= 12) hour -= 12
                                    },
                                    modifier = Modifier.weight(1f),
                                    fade = NixPalette.Mist,
                                )
                            }
                        }
                    }
                }

                // -------------------------------------------------- preview
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = NixPalette.Accent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, NixPalette.Accent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = title.ifBlank { "Untitled event" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = fullDateLabel(date),
                                style = MaterialTheme.typography.bodySmall,
                                color = NixPalette.Ink,
                                modifier = Modifier.weight(1f),
                            )
                            if (countdown.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = NixPalette.Accent,
                                ) {
                                    Text(
                                        text = countdown,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NixPalette.OnAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (allDay) "All-day" else "at ${TimeFormat.format(previewTime)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = NixPalette.Muted,
                        )
                    }
                }

                if (!initial.editable) {
                    Text(
                        "Imported events are copies — edit them in their own calendar app.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Muted,
                    )
                }

                notice?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (noticeIsError) NixPalette.Bad else NixPalette.Ok,
                    )
                }

                // --------------------------------------------------- actions
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete, enabled = !busy) {
                            Text("Delete", color = NixPalette.Bad)
                        }
                        Spacer(Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
                    Button(
                        onClick = {
                            onConfirm(
                                title.trim(),
                                date.toString(),
                                if (allDay) "" else previewTime,
                            )
                        },
                        enabled = !busy && title.isNotBlank() && initial.editable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NixPalette.Ink,
                            contentColor = NixPalette.Paper,
                        ),
                    ) {
                        Text(if (busy) "Saving…" else confirmLabel)
                    }
                }
            }
        }
    }
}

/** Small caps label above a single field (the title input). */
@Composable
private fun LabelledField(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            color = NixPalette.Muted,
            fontWeight = FontWeight.Bold,
        )
        content()
    }
}

/** Mist card with a caps label, used for the Date and Time blocks. */
@Composable
private fun SectionBlock(label: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = NixPalette.Muted,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

/** Pill chip used for the quick date/time answers. */
@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = NixPalette.Paper,
        border = BorderStroke(1.dp, NixPalette.Accent.copy(alpha = 0.6f)),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = NixPalette.Accent,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}
