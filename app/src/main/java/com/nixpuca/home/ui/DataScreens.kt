package com.nixpuca.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.nixpuca.home.NixViewModel
import com.nixpuca.home.data.TimeFormat

private val KNOWLEDGE_TABS = listOf("Memory", "Records", "People")

/** Knowledge: memory block, KB records and people — three tabs, one loader. */
@Composable
fun KnowledgeScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(vm.knowledgeTab) { vm.loadKnowledge() }

    Column(modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = vm.knowledgeTab,
            containerColor = NixPalette.Paper,
            contentColor = NixPalette.Ink,
        ) {
            KNOWLEDGE_TABS.forEachIndexed { index, label ->
                Tab(
                    selected = vm.knowledgeTab == index,
                    onClick = { vm.knowledgeTab = index },
                    text = { Text(label) },
                )
            }
        }

        when (vm.knowledgeTab) {
            0 -> MemoryTab(vm)
            1 -> CardsTab(
                cards = vm.kbCards,
                loading = vm.loading["kb"] == true,
                error = vm.dataError["kb"],
                empty = "No knowledge records yet. Ask ${vm.companionName} to remember something.",
                onRefresh = { vm.loadKb() },
            )
            else -> CardsTab(
                cards = vm.peopleCards,
                loading = vm.loading["people"] == true,
                error = vm.dataError["people"],
                empty = "No people recorded yet.",
                onRefresh = { vm.loadPeople() },
            )
        }
    }
}

@Composable
private fun MemoryTab(vm: NixViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Memory block",
                subtitle = "What NIX remembers about you",
                loading = vm.loading["memory"] == true,
                onRefresh = { vm.loadMemory() },
            ) {
                when {
                    vm.dataError["memory"] != null -> ErrorNote(vm.dataError["memory"]!!)
                    vm.memoryText.isNullOrBlank() ->
                        EmptyNote("Nothing stored yet — memory fills up as you talk to ${vm.companionName}.")
                    // No inner verticalScroll here: this Text lives inside a
                    // LazyColumn item, which already measures it with an
                    // infinite height. Nesting a vertically scrollable
                    // container in that constraint throws
                    // IllegalStateException and takes the whole screen down.
                    else -> Text(
                        text = vm.memoryText.orEmpty(),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Generic card list with loading, error and empty states. */
@Composable
fun CardsTab(
    cards: List<com.nixpuca.home.data.JsonCard>,
    loading: Boolean,
    error: String?,
    empty: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty() && !loading) {
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionCard(title = "Nothing to show", loading = false, onRefresh = onRefresh) {
                if (error != null) ErrorNote(error) else EmptyNote(empty)
            }
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (error != null) {
            item { ErrorNote(error) }
        }
        items(cards, key = { it.raw + it.title }) { card ->
            JsonCardItem(card)
        }
        item {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRefresh) {
                Text(if (loading) "Loading…" else "Refresh")
            }
        }
    }
}

/**
 * Schedule: a real month calendar (NIX + Google events marked per day), the
 * selected day's agenda, Google Calendar connect via secret iCal address,
 * and the existing upcoming/reminder lists below.
 */
@Composable
fun ScheduleScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        if (vm.scheduleUpcoming.isEmpty() && vm.schedulePast.isEmpty() && vm.scheduleActions.isEmpty()) {
            vm.loadSchedule()
        }
    }

    val loading = vm.loading["schedule"] == true
    val error = vm.dataError["schedule"]
    var addOpen by remember { mutableStateOf(false) }
    /** Event being edited (tap the pencil / the event itself). */
    var editing by remember { mutableStateOf<EditableEvent?>(null) }
    /** Which integration card is open: 0 = Google, 1 = phone, null = none. */
    var integration by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MonthCalendar(vm, onAddEvent = { addOpen = true }, onEdit = { editing = it })
        }
        vm.eventEditNotice?.let { notice ->
            item {
                Text(notice, color = NixPalette.Ok, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            IntegrationsCard(
                expanded = integration,
                onToggle = { which ->
                    if (which == 2) {
                        addOpen = true
                    } else {
                        integration = if (integration == which) null else which
                    }
                },
            )
        }
        if (integration == 0) {
            item { GoogleCalendarCard(vm) }
        }
        if (integration == 1) {
            item { LocalCalendarCard(vm) }
        }
        item {
            SectionCard(
                title = "Upcoming",
                subtitle = "Events and reminders",
                loading = loading,
                onRefresh = { vm.loadSchedule() },
            ) {
                when {
                    error != null -> ErrorNote(error)
                    vm.scheduleUpcoming.isEmpty() -> EmptyNote("No upcoming events.")
                    else -> UpcomingTimeline(vm.scheduleUpcoming, onEdit = { editing = it })
                }
            }
        }
        if (vm.scheduleActions.isNotEmpty()) {
            item {
                SectionCard(title = "Standalone actions", subtitle = "Scheduled work not tied to an event") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        vm.scheduleActions.forEach { JsonCardItem(it) }
                    }
                }
            }
        }
    }

    if (addOpen) {
        AddEventDialog(vm = vm, onDismiss = { addOpen = false })
    }
    editing?.let { event ->
        EditEventDialog(vm = vm, event = event, onDismiss = { editing = null })
    }
}

/** One event as the edit dialog sees it: identity plus its current values. */
private data class EditableEvent(
    val title: String,
    val date: String,
    val time: String,
    val source: String,
)

/** Pull the editable pieces out of an ISO start string. */
private fun editableOf(title: String, start: String?, source: String): EditableEvent? {
    val text = start?.trim().orEmpty()
    if (text.length < 10) return null
    val date = text.substring(0, 10)
    val time = if (text.length >= 16 && text[10] == 'T') text.substring(11, 16) else ""
    return EditableEvent(title = title, date = date, time = time, source = source)
}

/**
 * Edit / delete an event: the same wheel editor the add flow uses, prefilled
 * — imported (Google / phone) copies open read-only with their note.
 */
@Composable
private fun EditEventDialog(vm: NixViewModel, event: EditableEvent, onDismiss: () -> Unit) {
    val imported = event.source == "google" || event.source.startsWith("local")
    EventEditorDialog(
        heading = "Edit event",
        confirmLabel = "Save changes",
        initial = remember(event) {
            EventDraft(
                title = event.title,
                date = event.date,
                time = event.time,
                editable = !imported,
            )
        },
        busy = vm.eventEditBusy,
        // The schedule header surfaces vm.eventEditNotice after this closes.
        notice = null,
        onConfirm = { title, date, time ->
            vm.updateEvent(event.title, event.date, title, date, time)
            onDismiss()
        },
        onDelete = {
            vm.deleteEvent(event.title, event.date)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/** Month grid with event dots + a selected-day agenda underneath. */
@Composable
private fun MonthCalendar(
    vm: NixViewModel,
    onAddEvent: () -> Unit,
    onEdit: (EditableEvent) -> Unit,
) {
    var cursor by remember { mutableStateOf(java.time.YearMonth.now()) }
    var selected by remember { mutableStateOf(java.time.LocalDate.now()) }
    val today = java.time.LocalDate.now()

    // Day → events (NIX accent-dot, Google blue, local calendar green).
    val eventsByDay = remember(vm.calendarEvents, vm.googleEvents, vm.localEvents, cursor) {
        val map = mutableMapOf<Long, MutableList<com.nixpuca.home.CalendarEvent>>()
        (vm.calendarEvents + vm.googleEvents + vm.localEvents).forEach { event ->
            if (event.dayMillis != 0L) map.getOrPut(event.dayMillis) { mutableListOf() }.add(event)
        }
        map
    }

    fun dayMillisOf(date: java.time.LocalDate): Long =
        java.time.LocalDateTime.of(date, java.time.LocalTime.MIN)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    SectionCard(
        title = cursor.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + cursor.year,
        subtitle = "NIX · Google · your calendars",
        onRefresh = { vm.loadSchedule() },
        actions = {
            TextButton(onClick = onAddEvent) { Text("+ Add", style = MaterialTheme.typography.labelLarge) }
            TextButton(onClick = {
                cursor = cursor.minusMonths(1)
                selected = cursor.atDay(1)
            }) { Text("‹", style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = {
                cursor = cursor.plusMonths(1)
                selected = cursor.atDay(1)
            }) { Text("›", style = MaterialTheme.typography.titleMedium) }
        },
    ) {
        // Weekday header.
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // Weeks.
        val first = cursor.atDay(1)
        val lead = (first.dayOfWeek.value + 6) % 7 // Monday-first offset.
        val totalCells = ((lead + cursor.lengthOfMonth() + 6) / 7) * 7
        var cellDate = first.minusDays(lead.toLong())
        repeat(totalCells / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) {
                    val date = cellDate
                    val events = eventsByDay[dayMillisOf(date)].orEmpty()
                    val inMonth = date.month == cursor.month
                    val isToday = date == today
                    val isSelected = date == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isSelected -> NixPalette.Accent
                                    isToday -> NixPalette.Mist
                                    else -> Color.Transparent
                                },
                            )
                            .clickable {
                                selected = date
                                if (!inMonth) cursor = java.time.YearMonth.from(date)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isToday || isSelected) FontWeight.Bold else null,
                            color = when {
                                isSelected -> NixPalette.OnAccent
                                !inMonth -> NixPalette.Line
                                isToday -> NixPalette.Ink
                                else -> NixPalette.Ink
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        // Event dots: nix = white on accent, google = multicolour.
                        if (events.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                events.take(3).forEach { event ->
                                    Box(
                                        Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(
                                        when {
                                            isSelected -> NixPalette.OnAccent
                                            event.source == "google" -> Color(0xFF4285F4)
                                            event.source.startsWith("local") -> Color(0xFF14764A)
                                            else -> NixPalette.Accent
                                        },
                                            ),
                                    )
                                }
                            }
                        }
                    }
                    cellDate = cellDate.plusDays(1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = NixPalette.Line)
        Spacer(Modifier.height(8.dp))
        // Selected-day agenda.
        Text(
            text = selected.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
            style = MaterialTheme.typography.labelSmall,
            color = NixPalette.Muted,
        )
        Spacer(Modifier.height(6.dp))
        val dayEvents = eventsByDay[dayMillisOf(selected)].orEmpty()
        if (dayEvents.isEmpty()) {
            EmptyNote("Nothing scheduled for this day.")
        } else {
            dayEvents.forEach { event ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    event.source == "google" -> Color(0xFF4285F4)
                                    event.source.startsWith("local") -> Color(0xFF14764A)
                                    else -> NixPalette.Accent
                                },
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = when {
                                event.allDay -> "All day"
                                event.start.length >= 16 ->
                                    TimeFormat.format(event.start.substring(11, 16))
                                else -> event.start.take(10)
                            } + when {
                                event.source == "google" -> " · Google"
                                event.source.startsWith("local") -> " · " + event.source.removePrefix("local").removePrefix(" · ")
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = NixPalette.Muted,
                        )
                    }
                    // NIX events are editable right here; imported ones are
                    // read-only copies owned by their own calendar app.
                    if (event.source == "nix") {
                        IconButton(
                            onClick = {
                                editableOf(event.title, event.start, event.source)?.let(onEdit)
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "Edit ${event.title}",
                                tint = NixPalette.Muted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Three ways to bring events in — collapsed to buttons so the schedule
 * opens on the calendar, not on configuration.
 */
@Composable
private fun IntegrationsCard(expanded: Int?, onToggle: (Int) -> Unit) {
    SectionCard(
        title = "Connect integrations",
        subtitle = "Google Calendar, this phone, or add one yourself",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntegrationTile(
                label = "Google Calendar",
                active = expanded == 0,
                modifier = Modifier.weight(1f),
                onClick = { onToggle(0) },
            ) { GoogleG(size = 18) }
            IntegrationTile(
                label = "Your calendars",
                active = expanded == 1,
                modifier = Modifier.weight(1f),
                onClick = { onToggle(1) },
            ) {
                Text("📅", style = MaterialTheme.typography.titleMedium)
            }
            IntegrationTile(
                label = "Add event",
                active = false,
                modifier = Modifier.weight(1f),
                onClick = { onToggle(2) },
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NixPalette.Ink,
                )
            }
        }
    }
}

/** One icon-over-label button in the integrations row. */
@Composable
private fun IntegrationTile(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (active) NixPalette.Ink else NixPalette.Paper,
        border = BorderStroke(1.dp, if (active) NixPalette.Ink else NixPalette.Line),
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            icon()
            Spacer(Modifier.height(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) NixPalette.Paper else NixPalette.Ink,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Upcoming as a date timeline: day chip, rail, event card. */
@Composable
private fun UpcomingTimeline(
    cards: List<com.nixpuca.home.data.JsonCard>,
    onEdit: (EditableEvent) -> Unit,
) {
    cards.forEachIndexed { index, card ->
        val start = card.fields.firstOrNull { it.first.equals("start", true) }?.second
        // Reminders often arrive with no `start` at all — fall back to the
        // field that does state a moment (end / due / …) so the day chip,
        // timing row and countdown still show instead of going blank.
        val timingKey = if (start.isNullOrBlank()) {
            TIMING_FALLBACK_KEYS.firstOrNull { want ->
                card.fields.any { (key, value) -> key.equals(want, true) && value.isNotBlank() }
            }
        } else {
            null
        }
        val timingRaw = timingKey?.let { key ->
            card.fields.firstOrNull { it.first.equals(key, true) }?.second
        } ?: start
        val parts = parseEventStart(start) ?: parseLooseTiming(timingRaw)
        val editable = editableOf(card.title, start, "nix")
        // The exact moment this entry points at, for the countdown pill.
        val target = parts?.let { p ->
            val hm = p.time24?.split(":")
            if (hm != null && hm.size == 2) {
                p.date.atTime(hm[0].toInt(), hm[1].toInt())
            } else {
                p.date.atStartOfDay()
            }
        }
        val countdown = target
            ?.takeIf { it.isAfter(java.time.LocalDateTime.now()) }
            ?.let { countdownLabel(it) }
            ?: ""
        Row(Modifier.fillMaxWidth()) {
            // Day column with a connecting rail to the next entry.
            Column(
                Modifier.width(44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (parts != null) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = NixPalette.Accent,
                    ) {
                        Column(
                            Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = parts.day,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NixPalette.OnAccent,
                            )
                            Text(
                                text = parts.month,
                                style = MaterialTheme.typography.labelSmall,
                                color = NixPalette.OnAccent,
                            )
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .size(10.dp)
                            .background(NixPalette.Accent, CircleShape),
                    )
                }
                if (index != cards.lastIndex) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .width(2.dp)
                            .weight(1f)
                            .background(NixPalette.Line),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                modifier = Modifier
                    .padding(bottom = if (index == cards.lastIndex) 0.dp else 10.dp)
                    // Tap an upcoming event to edit or delete it.
                    .tapScale {
                        editable?.let(onEdit)
                    },
                shape = RoundedCornerShape(14.dp),
                color = NixPalette.Paper,
                border = BorderStroke(1.dp, NixPalette.Line),
            ) {
                Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = card.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = NixPalette.Ink,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (countdown.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = NixPalette.Accent.copy(alpha = 0.14f),
                            ) {
                                Text(
                                    text = countdown,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NixPalette.Accent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        if (editable != null) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "Edit ${card.title}",
                                tint = NixPalette.Muted,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }

                    // The timing, stated plainly: clock time + full date.
                    if (parts != null) {
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // A moment borrowed from `end`/`due` says so, so it
                            // never reads as if the reminder *starts* then.
                            if (timingKey != null) {
                                Text(
                                    text = timingKey.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    letterSpacing = 0.8.sp,
                                    color = NixPalette.Muted,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = parts.time ?: "All-day",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NixPalette.Ink,
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = fullDateLabel(parts.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = NixPalette.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Anything else the console told us about this entry
                    // (location, source, recurrence, notes…), labelled.
                    val extras = card.fields.filterNot { (key, _) ->
                        key.equals("title", true) || key.equals("id", true) ||
                            key.equals("start", true) || key.equals(timingKey, true)
                    }.take(3)
                    extras.forEach { (key, value) ->
                        if (value.isBlank()) return@forEach
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = key.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 0.8.sp,
                                color = NixPalette.Muted,
                                modifier = Modifier.width(72.dp),
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = NixPalette.Ink,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.height(5.dp))
                    Text(
                        text =                        buildList {
                            parts?.relative?.let { add(it) }
                            add(if (editable != null) "tap to edit" else "reminder")
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Muted,
                    )
                }
            }
        }
    }
}

/** Parsed pieces of an event start: day-of-month, month, time, "Today"-style label. */
private data class EventStart(
    val day: String,
    val month: String,
    /** Rendered in the phone's 12h/24h style, or null for all-day. */
    val time: String?,
    val relative: String,
    val date: java.time.LocalDate,
    /** Raw 24-hour HH:mm from the server, kept for countdown maths. */
    val time24: String?,
)

private fun parseEventStart(raw: String?): EventStart? {
    val text = raw?.trim().orEmpty()
    if (text.length < 10) return null
    val date = try {
        java.time.LocalDate.parse(text.substring(0, 10))
    } catch (_: Throwable) {
        return null
    }
    val time24 = Regex("(\\d{1,2}:\\d{2})(?::\\d{2})?").find(text)?.groupValues?.get(1)
    return eventStartOf(date, time24)
}

/** Fields that can stand in for a missing `start`, in the order we prefer them. */
private val TIMING_FALLBACK_KEYS = listOf("end", "due", "deadline", "when", "date")

private val LOOSE_MONTHS = mapOf(
    "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
    "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
)

/**
 * Timing for entries with no ISO `start`: reminders the console sends with
 * only an "8 Oct 2026, 3:00 PM"-style moment (all-day notes, due dates).
 */
private fun parseLooseTiming(raw: String?): EventStart? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val m = Regex(
        "(\\d{1,2})\\s+([A-Za-z]{3,})\\s+(\\d{4})" +
            "(?:\\s*,?\\s*(\\d{1,2}):(\\d{2})\\s*([AaPp])?[Mm]?)?",
    ).find(text) ?: return null
    val day = m.groupValues[1].toIntOrNull() ?: return null
    val month = LOOSE_MONTHS[m.groupValues[2].take(3).lowercase()] ?: return null
    val year = m.groupValues[3].toIntOrNull() ?: return null
    val date = try {
        java.time.LocalDate.of(year, month, day)
    } catch (_: Throwable) {
        return null
    }
    val hourRaw = m.groupValues[4]
    val time24 = if (hourRaw.isEmpty()) {
        null
    } else {
        var hour = hourRaw.toIntOrNull() ?: return null
        when (m.groupValues[6].lowercase()) {
            "p" -> if (hour < 12) hour += 12
            "a" -> if (hour == 12) hour = 0
        }
        String.format(
            java.util.Locale.US,
            "%02d:%02d",
            hour,
            m.groupValues[5].toIntOrNull() ?: 0,
        )
    }
    return eventStartOf(date, time24)
}

/** Day chip, relative label and rendered clock time for a known moment. */
private fun eventStartOf(date: java.time.LocalDate, time24: String?): EventStart {
    val month = date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
    val today = java.time.LocalDate.now()
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date).toInt()
    val relative = when (days) {
        0 -> "Today"
        1 -> "Tomorrow"
        in 2..6 -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        else -> "$month ${date.dayOfMonth}"
    }
    val time = time24?.let { hm ->
        // Rendered in the phone's own 12h/24h style; the server still sends 24h.
        com.nixpuca.home.data.TimeFormat.format(hm)
    }
    return EventStart(
        day = date.dayOfMonth.toString(),
        month = month,
        time = time,
        relative = relative,
        date = date,
        time24 = time24,
    )
}

/** Google Calendar connect: paste the secret iCal address, import, push. */
@Composable
private fun GoogleCalendarCard(vm: NixViewModel) {
    val connected = vm.gcalUrlDraft.isNotBlank() && (vm.googleEvents.isNotEmpty() || vm.gcalNotice?.contains("Imported") == true || vm.gcalNotice?.contains("Connected") == true)

    SectionCard(
        title = "Google Calendar",
        subtitle = "Import events with your secret iCal address",
        loading = vm.gcalBusy,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoogleG(size = 22)
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (connected) "Connected · ${vm.googleEvents.size} events" else "Not connected",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.gcalUrlDraft,
            onValueChange = { vm.gcalUrlDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Secret address in iCal format") },
            placeholder = { Text("https://calendar.google.com/calendar/ical/…/basic.ics") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NixPalette.Accent,
                unfocusedBorderColor = NixPalette.Line,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.connectGoogleCalendar(vm.gcalUrlDraft) },
                enabled = !vm.gcalBusy && vm.gcalUrlDraft.isNotBlank(),
                border = BorderStroke(1.dp, NixPalette.Accent),
            ) {
                Text(if (vm.gcalBusy) "Importing…" else "Connect & import", color = NixPalette.Accent)
            }
            if (connected) {
                OutlinedButton(onClick = { vm.disconnectGoogleCalendar() }) {
                    Text("Disconnect")
                }
            }
        }
        if (vm.googleEvents.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.pushGoogleEventsToNix() },
                enabled = !vm.pushBusy,
                border = BorderStroke(1.dp, NixPalette.Accent),
            ) {
                Text(
                    text = if (vm.pushBusy) "Pushing to NIX…" else "Add these events to NIX's schedule",
                    color = NixPalette.Accent,
                )
            }
        }
        vm.gcalNotice?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = NixPalette.Ok, modifier = Modifier.padding(top = 6.dp))
        }
        vm.pushNotice?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = NixPalette.Ok, modifier = Modifier.padding(top = 4.dp))
        }
        vm.dataError["gcal"]?.let { ErrorNote(it) }
        Text(
            text = "Events import as read-only copies; pushing sends each one through NIX's own " +
                "calendar pipeline so reminders and Actions are created too.",
            style = MaterialTheme.typography.labelSmall,
            color = NixPalette.Muted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Manual add-event: the wheel editor, opened straight from "+ Add". */
@Composable
private fun AddEventDialog(vm: NixViewModel, onDismiss: () -> Unit) {
    EventEditorDialog(
        heading = "New event",
        confirmLabel = "Add to schedule",
        initial = remember {
            EventDraft(
                title = "",
                date = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                time = "",
            )
        },
        busy = vm.manualEventBusy,
        notice = vm.manualEventNotice,
        noticeIsError = vm.manualEventNoticeError,
        onConfirm = { title, date, time -> vm.addManualEvent(title, date, time) },
        onDismiss = onDismiss,
    )
}

/**
 * Phone calendars: read the device's own calendars (with permission) and
 * merge them into the month grid alongside NIX and Google events.
 */
@Composable
private fun LocalCalendarCard(vm: NixViewModel) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        vm.localCalGranted = granted
        if (granted) vm.loadLocalCalendar()
        else vm.localCalNotice = "Calendar permission denied — your phone's events can't be shown."
    }

    SectionCard(
        title = "Your calendars",
        subtitle = "Bring in events from this phone's calendar apps",
        loading = vm.localCalBusy,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📅", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (vm.localEvents.isNotEmpty()) {
                    "${vm.localEvents.size} events imported"
                } else {
                    "Not imported yet"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val already = vm.localCalGranted
                    if (already) vm.loadLocalCalendar()
                    else permissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                },
                enabled = !vm.localCalBusy,
                border = BorderStroke(1.dp, NixPalette.Accent),
            ) {
                Text(
                    text = if (vm.localCalBusy) "Loading…" else if (vm.localEvents.isNotEmpty()) "Refresh" else "Import from this phone",
                    color = NixPalette.Accent,
                )
            }
            if (vm.localEvents.isNotEmpty()) {
                OutlinedButton(onClick = { vm.localEvents = emptyList() }) {
                    Text("Clear")
                }
            }
        }
        vm.localCalNotice?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = NixPalette.Ok, modifier = Modifier.padding(top = 6.dp))
        }
        Text(
            text = "Read-only: events appear on the calendar above with a green dot. " +
                "Google calendars can be connected in the card above.",
            style = MaterialTheme.typography.labelSmall,
            color = NixPalette.Muted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** System: health, models, devices, sessions, pipeline and recent trace. */
@Composable
fun SystemScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        if (vm.modelRows.isEmpty() && vm.deviceRows.isEmpty() && vm.stageRows.isEmpty()) {
            vm.loadSystem()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        SectionCard(
            title = "Health",
            subtitle = "Live system health",
            loading = vm.loading["health"] == true,
            onRefresh = { vm.loadSystem() },
        ) {
            if (vm.healthChips.isEmpty()) {
                Text(
                    text = vm.dataError["health"] ?: "Loading…",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = NixPalette.Muted,
                )
            } else {
                ChipRow(vm.healthChips)
            }
        }

        SectionCard(
            title = "Companion models",
            subtitle = "Available companions",
            loading = vm.loading["models"] == true,
            onRefresh = { vm.loadSystem() },
        ) {
            vm.dataError["models"]?.let { ErrorNote(it) }
            if (vm.modelRows.isEmpty() && vm.dataError["models"] == null) {
                EmptyNote("No model status reported.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.modelRows.take(6).forEach { JsonCardItem(it) }
            }
        }

        SectionCard(
            title = "Devices",
            subtitle = "Connected device skills",
            loading = vm.loading["devices"] == true,
            onRefresh = { vm.loadSystem() },
        ) {
            vm.dataError["devices"]?.let { ErrorNote(it) }
            if (vm.deviceRows.isEmpty() && vm.dataError["devices"] == null) {
                EmptyNote("No connected devices.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.deviceRows.forEach { JsonCardItem(it) }
            }
        }

        SectionCard(
            title = "Pipeline",
            subtitle = "Stages of the last request",
            loading = vm.loading["pipeline"] == true,
            onRefresh = { vm.loadSystem() },
        ) {
            vm.dataError["pipeline"]?.let { ErrorNote(it) }
            if (vm.stageRows.isEmpty() && vm.dataError["pipeline"] == null) {
                EmptyNote("No pipeline activity yet — send a chat message first.")
            }
            vm.stageRows.forEach { stage ->
                Row {
                    Text(
                        text = stage.label,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stage.status + (stage.ms?.let { " · %.0f ms".format(it) } ?: ""),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = NixPalette.Muted,
                    )
                }
            }
        }

        SectionCard(
            title = "Sessions",
            subtitle = "Your past conversations",
            loading = vm.loading["sessions"] == true,
            onRefresh = { vm.loadSystem() },
        ) {
            vm.dataError["sessions"]?.let { ErrorNote(it) }
            if (vm.sessionRows.isEmpty() && vm.dataError["sessions"] == null) {
                EmptyNote("No live sessions.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.sessionRows.take(10).forEach { JsonCardItem(it) }
            }
        }

        SectionCard(
            title = "Recent trace",
            subtitle = "Newest activity first",
            loading = vm.loading["feed"] == true,
            onRefresh = { vm.loadSystem() },
        ) {
            vm.dataError["feed"]?.let { ErrorNote(it) }
            if (vm.traceRows.isEmpty() && vm.dataError["feed"] == null) {
                EmptyNote("The trace feed is empty.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.traceRows.forEach { row ->
                    Text(
                        text = row.fields.joinToString("  ") { "${it.first}: ${it.second}" }
                            .ifBlank { row.title },
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = NixPalette.Muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
