package com.nixpuca.home.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Offset
import com.nixpuca.home.NixViewModel
import com.nixpuca.home.SetupField
import com.nixpuca.home.SkillItem
import org.json.JSONObject

/**
 * Skills: install/uninstall marketplace packages and tune each installed
 * skill. Every write goes through POST /api/skills — configure, device_name,
 * auto_update, repair, trust/untrust, connect/disconnect — and consequential
 * taps are confirmed first. Declared setup fields render as typed inputs
 * instead of raw JSON so secrets stay masked.
 */
@Composable
fun SkillsScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        if (vm.skillItems.isEmpty()) vm.loadSkills()
    }

    var pending by remember { mutableStateOf<SkillItem?>(null) }
    var pendingTrust by remember { mutableStateOf<SkillItem?>(null) }
    var pendingUntrust by remember { mutableStateOf<SkillItem?>(null) }
    var pendingRuntime by remember { mutableStateOf<Pair<SkillItem, Boolean>?>(null) }
    var pendingRepair by remember { mutableStateOf<SkillItem?>(null) }
    var tuningId by remember { mutableStateOf<String?>(null) }
    /** Skill whose dedicated detail/tune page is open (null = marketplace). */
    var selectedSkillId by remember { mutableStateOf<String?>(null) }
    var configDraft by remember { mutableStateOf("{}") }
    var nameDraft by remember { mutableStateOf("") }
    var setupDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var draftError by remember { mutableStateOf<String?>(null) }
    /** Marketplace text search and the active category/status filter. */
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }

    val anyBusy = vm.skillBusyId != null

    val filterOptions = remember(vm.skillItems) {
        listOf("All", "Installed", "Running") +
            vm.skillItems.map { it.category }
                .filter { it.isNotBlank() && it != "—" }
                .distinct()
                .sorted()
    }
    val visibleSkills = remember(vm.skillItems, query, filter) {
        val needle = query.trim()
        vm.skillItems.filter { skill ->
            val matchesQuery = needle.isEmpty() ||
                skill.name.contains(needle, ignoreCase = true) ||
                skill.category.contains(needle, ignoreCase = true) ||
                skill.description.contains(needle, ignoreCase = true) ||
                skill.id.contains(needle, ignoreCase = true)
            val matchesFilter = when (filter) {
                "All" -> true
                "Installed" -> skill.installed
                "Running" -> skill.workerRunning
                else -> skill.category.equals(filter, ignoreCase = true)
            }
            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        val selectedSkill = selectedSkillId?.let { id -> vm.skillItems.firstOrNull { it.id == id } }

        if (selectedSkill == null) {
            // ------------------------------------------------ marketplace
            MarketplaceHero(
                installed = vm.skillItems.count { it.installed },
                running = vm.skillItems.count { it.workerRunning },
                available = vm.skillItems.size,
                loading = vm.loading["skills"] == true || vm.loading["skills-refresh"] == true,
                busy = anyBusy,
                refreshBusy = vm.loading["skills-refresh"] == true,
                onRefreshCatalog = { vm.loadSkills() },
            )
            vm.updateNotice?.let { Text(it, color = NixPalette.Ok, style = MaterialTheme.typography.bodySmall) }
            vm.skillNotice?.let { Text(it, color = NixPalette.Ok, style = MaterialTheme.typography.bodySmall) }
            vm.dataError["skills"]?.let { ErrorNote(it) }
            vm.dataError["skill-action"]?.let { ErrorNote(it) }

            // Search box + tag filters so a long catalog stays browsable.
            SkillSearchField(
                value = query,
                onChange = { query = it },
                resultCount = visibleSkills.size,
            )
            SkillFilterRow(
                options = filterOptions,
                selected = filter,
                onSelect = { filter = it },
            )

            if (visibleSkills.isEmpty() && vm.dataError["skills"] == null) {
                SectionCard(
                    title = if (vm.skillItems.isEmpty()) "Catalog" else "No matches",
                    loading = vm.loading["skills"] == true,
                    onRefresh = { vm.loadSkills() },
                ) {
                    EmptyNote(
                        if (vm.skillItems.isEmpty()) {
                            "No skills returned yet — pull the catalog with the refresh action above."
                        } else {
                            "Nothing matches \"$query\" in $filter. Clear the search or pick another tag."
                        },
                    )
                }
            }

            visibleSkills.forEach { skill ->
                SkillGridCard(
                    skill = skill,
                    busy = vm.skillBusyId == skill.id,
                    onClick = {
                        selectedSkillId = skill.id
                        tuningId = skill.id
                        configDraft = skill.configuration
                        nameDraft = skill.deviceName
                        setupDrafts = initialSetupValues(skill)
                        draftError = null
                    },
                )
            }
        } else {
            // -------------------------------------------- skill detail page
            SkillDetailHeader(
                skill = selectedSkill,
                onBack = {
                    selectedSkillId = null
                    tuningId = null
                    draftError = null
                },
            )
            vm.skillNotice?.let { Text(it, color = NixPalette.Ok, style = MaterialTheme.typography.bodySmall) }
            vm.dataError["skill-action"]?.let { ErrorNote(it) }

            vm.skillItems.forEach { skill ->
                if (skill.id != selectedSkill.id) return@forEach
                SkillCard(
                    skill = skill,
                    busy = anyBusy,
                    myBusy = vm.skillBusyId == skill.id,
                    tuning = tuningId == skill.id,
                    onToggleTune = {
                        if (tuningId == skill.id) {
                            tuningId = null
                            draftError = null
                        } else {
                            tuningId = skill.id
                            configDraft = skill.configuration
                            nameDraft = skill.deviceName
                            setupDrafts = initialSetupValues(skill)
                            draftError = null
                        }
                    },
                onInstallToggle = { pending = skill },
                onAutoUpdate = { enabled ->
                    vm.skillAction(
                        skill.id,
                        "auto_update",
                        JSONObject().put("enabled", enabled),
                        if (enabled) "Auto-update enabled for ${skill.name}" else "Auto-update disabled for ${skill.name}",
                        // Fast tuning taps always go through, even while an
                        // install runs elsewhere — a swallowed tap feels laggy.
                        force = true,
                    )
                },
                onRepair = { pendingRepair = skill },
                onTrust = { pendingTrust = skill },
                onUntrust = { pendingUntrust = skill },
                onRuntime = { connecting -> pendingRuntime = skill to connecting },
                configDraft = configDraft,
                onConfigDraft = { configDraft = it },
                nameDraft = nameDraft,
                nameDraftChanged = { nameDraft = it },
                setupDrafts = setupDrafts,
                onSetupDraft = { fieldId, value ->
                    setupDrafts = setupDrafts + (fieldId to value)
                },
                draftError = draftError,
                onSaveConfig = {
                    val raw = configDraft.trim().ifEmpty { "{}" }
                    try {
                        val parsed = JSONObject(raw)
                        // The console redacts secrets on GET (e.g. an API key
                        // comes back as {"configured":true}) but configure only
                        // accepts short text. Echoing non-string values back is
                        // rejected with HTTP 400 "Setup values must be short
                        // text", so send strings only; an omitted/empty secret
                        // keeps its stored value on the host.
                        val flat = JSONObject()
                        val kept = mutableListOf<String>()
                        val keys = parsed.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            when (val value = parsed.get(key)) {
                                is String -> flat.put(key, value)
                                else -> kept += key
                            }
                        }
                        draftError = null
                        vm.skillAction(
                            skill.id,
                            "configure",
                            JSONObject().put("configuration", flat),
                            if (kept.isEmpty()) {
                                "Saved settings for ${skill.name}"
                            } else {
                                "Saved settings for ${skill.name} · kept stored ${kept.joinToString()} " +
                                    "(retype to replace a secret)"
                            },
                            force = true,
                        )
                    } catch (t: Throwable) {
                        draftError = "Settings must be a JSON object: ${t.message ?: "invalid JSON"}"
                    }
                },
                onSaveSetup = {
                    // Typed form: send every declared field as short text and
                    // let an empty secret mean "keep the stored key".
                    val missing = skill.setupFields.firstOrNull { field ->
                        val value = setupDrafts[field.id].orEmpty().trim()
                        value.isEmpty() && field.required &&
                            !(field.secret && secretConfigured(skill, field.id))
                    }
                    if (missing != null) {
                        draftError = "Required setup value missing: ${missing.label}"
                    } else {
                        val flat = JSONObject()
                        skill.setupFields.forEach { field ->
                            flat.put(field.id, setupDrafts[field.id].orEmpty().trim())
                        }
                        draftError = null
                        vm.skillAction(
                            skill.id,
                            "configure",
                            JSONObject().put("configuration", flat),
                            "Saved settings for ${skill.name}",
                            force = true,
                        )
                    }
                },
                onSaveName = {
                    vm.skillAction(
                        skill.id,
                        "device_name",
                        JSONObject().put("device_name", nameDraft.trim()),
                        "Device name saved for ${skill.name}",
                        force = true,
                    )
                },
            )
                // Ring Light gets its own studio: power, effects and the
                // background mirror toggle live with the skill, not Settings.
                if (skill.id.contains("ring", ignoreCase = true) ||
                    skill.name.contains("ring", ignoreCase = true)
                ) {
                    LaunchedEffect(skill.id) {
                        if (vm.deviceItems.isEmpty()) vm.loadDevices()
                    }
                    RingLightStudio(vm)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }

    pending?.let { skill ->
        val installing = !skill.installed
        SkillInstallDialog(
            skill = skill,
            onConfirm = {
                pending = null
                vm.skillAction(
                    skill.id,
                    if (installing) "install" else "uninstall",
                    notice = if (installing) "${skill.name} installed" else "${skill.name} uninstalled",
                )
            },
            onDismiss = { pending = null },
        )
    }

    pendingTrust?.let { skill ->
        ConfirmDialog(
            title = "Trust ${skill.name}?",
            message = "Review the source before trusting digest ${skill.fingerprint ?: "unavailable"}: " +
                "trusted skill code runs as the NIX user with no OS sandbox and can reach that " +
                "user's files and network." +
                if (skill.permissions.isEmpty()) "" else "\n\nDeclared permissions: ${skill.permissions.joinToString()}",
            confirmLabel = "Trust this digest",
            onConfirm = {
                val digest = skill.fingerprint
                pendingTrust = null
                if (digest != null) {
                    vm.skillAction(
                        skill.id,
                        "trust",
                        JSONObject().put("confirm_digest", digest),
                        "Trusted ${skill.name}",
                        force = true,
                    )
                }
            },
            onDismiss = { pendingTrust = null },
        )
    }

    pendingUntrust?.let { skill ->
        ConfirmDialog(
            title = "Remove trust from ${skill.name}?",
            message = "Stops the worker and clears trust for ${skill.id}. Connect again only after re-trusting.",
            confirmLabel = "Untrust",
            onConfirm = {
                pendingUntrust = null
                vm.skillAction(skill.id, "untrust", notice = "Trust removed for ${skill.name}", force = true)
            },
            onDismiss = { pendingUntrust = null },
        )
    }

    pendingRuntime?.let { (skill, connecting) ->
        ConfirmDialog(
            title = if (connecting) "Connect ${skill.name}?" else "Disconnect ${skill.name}?",
            message = if (connecting) {
                "Starts the ${skill.id} runtime on the NIX host. It must be configured and trusted first."
            } else {
                "Stops the ${skill.id} runtime on the NIX host."
            },
            confirmLabel = if (connecting) "Connect" else "Disconnect",
            onConfirm = {
                pendingRuntime = null
                vm.skillAction(
                    skill.id,
                    if (connecting) "connect" else "disconnect",
                    notice = if (connecting) "${skill.name} connecting…" else "${skill.name} disconnected",
                    // Connect/disconnect must never be swallowed by a busy
                    // gate: a dropped toggle is what "keeps disconnecting"
                    // feels like from the other side.
                    force = true,
                )
            },
            onDismiss = { pendingRuntime = null },
        )
    }

    pendingRepair?.let { skill ->
        ConfirmDialog(
            title = "Refresh ${skill.name} package?",
            message = "Re-reads the repository manifest and re-downloads the package files. " +
                "Changes to package files clear prior setup and trust, so you will have to " +
                "review, set up and trust the new digest again.",
            confirmLabel = "Refresh package",
            onConfirm = {
                pendingRepair = null
                vm.skillAction(skill.id, "repair", notice = "Refreshed ${skill.name} package")
            },
            onDismiss = { pendingRepair = null },
        )
    }
}

/** Prefill for the typed setup form: current non-secret values, empty secrets. */
private fun initialSetupValues(skill: SkillItem): Map<String, String> {
    val json = try {
        JSONObject(skill.configuration)
    } catch (t: Throwable) {
        JSONObject()
    }
    return skill.setupFields.associate { field ->
        val value = when {
            field.secret -> ""
            else -> json.opt(field.id)?.takeIf { it !is JSONObject && it !== JSONObject.NULL }
                ?.toString()?.trim('"') ?: ""
        }
        field.id to value
    }
}

/** A secret counts as stored when GET reported {"configured": true}. */
private fun secretConfigured(skill: SkillItem, fieldId: String): Boolean {
    val json = try {
        JSONObject(skill.configuration)
    } catch (t: Throwable) {
        return false
    }
    val value = json.opt(fieldId)
    return value is JSONObject && value.optBoolean("configured", false)
}

/**
 * Ring Light studio: power buttons, the live effect switcher fed by the
 * device's advertised effect list, and the background-mirror toggle — all
 * on the skill's own page where the user expects to tune the hardware.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RingLightStudio(vm: NixViewModel) {
    val ring = vm.ringState
    val busy = vm.skillBusyId != null

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Light studio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (ring != null) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(50),
                        color = if (ring.on) Color(0xFFE2F6EA) else NixPalette.Paper,
                        border = BorderStroke(1.dp, NixPalette.Line),
                    ) {
                        Text(
                            text = if (ring.on) "On · ${ring.effect}" else "Off",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ring.on) Color(0xFF14764A) else NixPalette.Muted,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Changes are sent to the light straight away.",
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
            )
            Spacer(Modifier.height(10.dp))

            // Power.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.ringCommand("on", notice = "Light turned on") },
                    enabled = !busy,
                    border = BorderStroke(1.dp, if (ring?.on == true) NixPalette.Accent else NixPalette.Line),
                ) { Text("Turn on", color = if (ring?.on == true) NixPalette.Accent else NixPalette.Ink) }
                OutlinedButton(
                    onClick = { vm.ringCommand("off", notice = "Light turned off") },
                    enabled = !busy,
                ) { Text("Turn off") }
            }

            // Effect switcher — chips straight off the device's list.
            val effects = vm.ringEffects
            if (effects.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "EFFECTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                    letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    effects.forEach { effect ->
                        val selected = ring?.effect.equals(effect, ignoreCase = true)
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) NixPalette.Ink else NixPalette.Paper,
                            border = BorderStroke(1.dp, if (selected) NixPalette.Ink else NixPalette.Line),
                            modifier = Modifier.clickable {
                                vm.ringCommand(
                                    "effect",
                                    JSONObject().put("effect", effect),
                                    "Effect: $effect",
                                )
                            },
                        ) {
                            Text(
                                text = effect,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) NixPalette.Paper else NixPalette.Ink,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.HorizontalDivider(color = NixPalette.Line)
            Spacer(Modifier.height(10.dp))

            // Background mirror — the ring theming feature, home of the skill.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Mirror this light in the app",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Tint the whole app background with the light's live colour " +
                            "and effect (e.g. Rainbow Twinkle paints slow rainbow bands).",
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Muted,
                    )
                }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Switch(
                    checked = vm.mirrorRing,
                    onCheckedChange = { vm.setMirrorRingEnabled(it) },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = NixPalette.Paper,
                        checkedTrackColor = NixPalette.Accent,
                    ),
                )
            }
        }
    }
}

/**
 * Marketplace hero: an animated aurora banner with live counts as stat
 * tiles and the catalog refresh — an app-store header, not a data card.
 */
@Composable
private fun MarketplaceHero(
    installed: Int,
    running: Int,
    available: Int,
    loading: Boolean,
    busy: Boolean,
    refreshBusy: Boolean,
    onRefreshCatalog: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "hero")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000), RepeatMode.Reverse),
        label = "drift",
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(2_600), RepeatMode.Reverse),
        label = "glow",
    )

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Box {
            // Drifting aurora washes in the accent — slow enough to read as
            // ambience rather than decoration.
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(190.dp)) {
                val w = size.width
                val h = size.height
                drawRect(color = NixPalette.Mist)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(NixPalette.Accent.copy(alpha = glow * 0.35f), Color.Transparent),
                    ),
                    radius = w * (0.42f + drift * 0.16f),
                    center = Offset(w * (0.82f - drift * 0.2f), h * 0.24f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(NixPalette.Accent.copy(alpha = (1f - glow) * 0.30f), Color.Transparent),
                    ),
                    radius = w * (0.36f + (1f - drift) * 0.2f),
                    center = Offset(w * (0.18f + drift * 0.22f), h * 0.86f),
                )
                // Quiet tonal arcs echoing the ring light.
                for (r in floatArrayOf(0.30f, 0.48f, 0.66f)) {
                    drawCircle(
                        color = NixPalette.Accent.copy(alpha = 0.10f),
                        radius = size.minDimension * r,
                        center = Offset(w * 0.86f, h * 0.5f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                    )
                }
            }
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = "Skills marketplace",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NixPalette.Ink,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Extend NIX with new abilities",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NixPalette.Muted,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroStat(
                        value = installed,
                        label = "installed",
                        modifier = Modifier.weight(1f),
                        tint = Color(0xFFE2F6EA),
                        ink = Color(0xFF14764A),
                    )
                    HeroStat(
                        value = running,
                        label = "running",
                        modifier = Modifier.weight(1f),
                        tint = Color(0xFFEDE8FF),
                        ink = Color(0xFF5B3FD6),
                    )
                    HeroStat(
                        value = available,
                        label = "in catalog",
                        modifier = Modifier.weight(1f),
                        tint = Color(0xFFE3F1FF),
                        ink = Color(0xFF0E62B8),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(
                        onClick = onRefreshCatalog,
                        enabled = !busy,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = NixPalette.Ink,
                            contentColor = NixPalette.Paper,
                        ),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = if (loading && !refreshBusy) "Loading…" else "Refresh catalog",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(18.dp).align(Alignment.TopEnd).padding(14.dp),
                    strokeWidth = 2.dp,
                    color = NixPalette.Ink,
                )
            }
        }
    }
}

/** One number-over-label tile in the marketplace hero. */
@Composable
private fun HeroStat(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color,
    ink: Color,
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = NixPalette.Paper.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Animated count so a catalog refresh visibly ticks over.
            val shown by animateIntAsState(targetValue = value, label = "statCount")
            Text(
                text = shown.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = ink,
            )
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tint)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = ink)
            }
        }
    }
}

/** Search box for the catalog; live-filters as the user types. */
@Composable
private fun SkillSearchField(value: String, onChange: (String) -> Unit, resultCount: Int) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search skills, categories, ids…") },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = NixPalette.Muted,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            } else {
                Text(
                    text = "$resultCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NixPalette.Accent,
            unfocusedBorderColor = NixPalette.Line,
            focusedContainerColor = NixPalette.Mist,
            unfocusedContainerColor = NixPalette.Mist,
        ),
    )
}

/** Tag-style filter pills: All / Installed / Running / one per category. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SkillFilterRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            val tint = if (active) NixPalette.Accent else NixPalette.Paper
            Surface(
                shape = RoundedCornerShape(50),
                color = tint,
                border = BorderStroke(1.dp, if (active) NixPalette.Accent else NixPalette.Line),
                modifier = Modifier.tapScale { onSelect(option) },
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) NixPalette.OnAccent else NixPalette.Ink,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** Quiet hue family used for a skill's icon tile and hero wash. */
private val SKILL_TINTS = listOf(
    Color(0xFFEDE8FF),
    Color(0xFFE3F1FF),
    Color(0xFFFFF2DF),
    Color(0xFFFFE8EF),
    Color(0xFFE2F6EA),
)

private val SKILL_INKS = listOf(
    Color(0xFF5B3FD6),
    Color(0xFF0E62B8),
    Color(0xFF9A6206),
    Color(0xFFB8285A),
    Color(0xFF14764A),
)

/** One skill's (tint, ink) pair — stable per skill across the whole UI. */
private fun skillTint(skill: SkillItem): Pair<Color, Color> {
    val idx = (skill.name + skill.id).hashCode().mod(SKILL_TINTS.size)
    return SKILL_TINTS[idx] to SKILL_INKS[idx]
}

/** A dot that breathes while a skill's worker is live. */
@Composable
private fun PulseDot(color: Color, size: androidx.compose.ui.unit.Dp = 7.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        Modifier
            .size(size)
            .background(color.copy(alpha = alpha), CircleShape),
    )
}

/** Play-Store-style row: soft tonal icon tile, name, meta, install pill. */
@Composable
private fun SkillGridCard(skill: SkillItem, busy: Boolean, onClick: () -> Unit) {
    // Soft accent-toned icon backgrounds — no rainbow contrast against the
    // rest of the page; one quiet hue family per skill.
    val tints = listOf(
        Color(0xFFEDE8FF),
        Color(0xFFE3F1FF),
        Color(0xFFFFF2DF),
        Color(0xFFFFE8EF),
        Color(0xFFE2F6EA),
    )
    val inkTones = listOf(
        Color(0xFF5B3FD6),
        Color(0xFF0E62B8),
        Color(0xFF9A6206),
        Color(0xFFB8285A),
        Color(0xFF14764A),
    )
    val idx = (skill.name + skill.id).hashCode().mod(tints.size)
    val tint = tints[idx]
    val inkTone = inkTones[idx]

    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .tapScale(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = NixPalette.Paper,
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Soft app-tile with the skill's initial over a tint gradient.
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.45f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = skill.name.firstOrNull()?.uppercase() ?: "✦",
                    color = inkTone,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (skill.workerRunning) {
                        PulseDot(Color(0xFF1B8A4B))
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = skill.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    text = "${skill.category} · v${skill.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                    maxLines = 1,
                )
                if (skill.description.isNotBlank()) {
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = NixPalette.Muted,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(50),
                        color = when {
                            skill.workerRunning -> Color(0xFFE2F6EA)
                            skill.installed -> NixPalette.Mist
                            else -> Color(0xFFFFF2DF)
                        },
                    ) {
                        Text(
                            text = when {
                                skill.workerRunning -> "running"
                                skill.installed -> "installed"
                                else -> "available"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                skill.workerRunning -> Color(0xFF14764A)
                                skill.installed -> NixPalette.Muted
                                else -> Color(0xFF8A6A1F)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    if (skill.installed && skill.trusted) {
                        Text(
                            text = "trusted",
                            style = MaterialTheme.typography.labelSmall,
                            color = NixPalette.Muted,
                        )
                    }
                    if (skill.runtimeError != null) {
                        Text(
                            text = "error",
                            style = MaterialTheme.typography.labelSmall,
                            color = NixPalette.Bad,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            // Play-style action pill: Install / Open.
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(50),
                color = if (skill.installed) NixPalette.Mist else NixPalette.Ink,
            ) {
                Text(
                    text = if (skill.installed) "Open" else "Install",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (skill.installed) NixPalette.Ink else NixPalette.Paper,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** Hero header for a skill's dedicated page, with a back affordance. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SkillDetailHeader(skill: SkillItem, onBack: () -> Unit) {
    val tints = listOf(
        Color(0xFFEDE8FF),
        Color(0xFFE3F1FF),
        Color(0xFFFFF2DF),
        Color(0xFFFFE8EF),
        Color(0xFFE2F6EA),
    )
    val inkTones = listOf(
        Color(0xFF5B3FD6),
        Color(0xFF0E62B8),
        Color(0xFF9A6206),
        Color(0xFFB8285A),
        Color(0xFF14764A),
    )
    val idx = (skill.name + skill.id).hashCode().mod(tints.size)
    val tint = tints[idx]
    val inkTone = inkTones[idx]

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onBack) { Text("‹ Skills") }
    }
    Spacer(Modifier.height(4.dp))
    // Gradient hero instead of a flat block of text: tinted panel, big
    // glyph, meta pills and the description as a quote.
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Box {
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(tint.copy(alpha = 0.9f), Color.Transparent),
                    ),
                    radius = size.minDimension * 0.9f,
                    center = Offset(size.width * 0.92f, 0f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(tint.copy(alpha = 0.55f), Color.Transparent),
                    ),
                    radius = size.minDimension * 0.6f,
                    center = Offset(size.width * 0.06f, size.height),
                )
            }
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.5f)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = skill.name.firstOrNull()?.uppercase() ?: "✦",
                            color = inkTone,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = skill.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = NixPalette.Ink,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${skill.category} · v${skill.version} · " +
                                if (skill.community) "community" else "NIX starter",
                            style = MaterialTheme.typography.labelSmall,
                            color = NixPalette.Muted,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetaPill(
                                text = if (skill.installed) "installed" else "not installed",
                                color = if (skill.installed) Color(0xFF14764A) else Color(0xFF8A6A1F),
                                background = if (skill.installed) Color(0xFFE2F6EA) else Color(0xFFFFF2DF),
                            )
                            if (skill.workerRunning) {
                                MetaPill(text = "running", color = Color(0xFF14764A), background = Color(0xFFE2F6EA))
                            }
                            if (skill.permissions.isNotEmpty()) {
                                MetaPill(
                                    text = "${skill.permissions.size} permission${if (skill.permissions.size == 1) "" else "s"}",
                                    color = inkTone,
                                    background = tint,
                                )
                            }
                        }
                    }
                }
                if (skill.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(inkTone),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = skill.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NixPalette.Ink,
                        )
                    }
                }
                if (skill.repositoryUrl.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Source: ${skill.repositoryUrl}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Accent,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                // Visual setup pipeline so "what do I still need to do?"
                // reads at a glance instead of from prose.
                if (skill.installed) {
                    Spacer(Modifier.height(14.dp))
                    SkillProgressRail(skill)
                }
                if (skill.permissions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "PERMISSIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Muted,
                        letterSpacing = 1.4.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        skill.permissions.forEach { permission ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = tint,
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = inkTone,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = permission,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = inkTone,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** Small rounded pill used in the skill hero. */
@Composable
private fun MetaPill(text: String, color: Color, background: Color) {
    Surface(shape = RoundedCornerShape(50), color = background) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/**
 * Installed → Set up → Trusted → Running: the skill's lifecycle drawn as a
 * rail of nodes so the next required step is obvious without reading.
 */
@Composable
private fun SkillProgressRail(skill: SkillItem) {
    val steps = buildList {
        add("Installed" to skill.installed)
        if (skill.installed && skill.runtimeDeclared) {
            add("Set up" to skill.configured)
            add("Trusted" to skill.trusted)
            add("Running" to skill.workerRunning)
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        steps.forEachIndexed { index, (label, done) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (done) NixPalette.Ok else NixPalette.Paper)
                        .border(1.5.dp, if (done) NixPalette.Ok else NixPalette.Line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = label,
                            tint = NixPalette.Paper,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(NixPalette.Line, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (done) NixPalette.Ink else NixPalette.Muted,
                    fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
            if (index != steps.lastIndex) {
                Box(
                    Modifier
                        .weight(0.6f)
                        .padding(top = 12.dp)
                        .height(2.dp)
                        .background(if (done) NixPalette.Ok else NixPalette.Line),
                )
            }
        }
    }
}

/** One action button in the skill's tile grid. */
private data class SkillActionSpec(
    val label: String,
    val sublabel: String,
    val enabled: Boolean,
    val emphasis: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun SkillActionButton(spec: SkillActionSpec, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.tapScale(enabled = spec.enabled, onClick = spec.onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (spec.emphasis) NixPalette.Ink else NixPalette.Paper,
        border = BorderStroke(1.dp, if (spec.emphasis) NixPalette.Ink else NixPalette.Line),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = spec.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (spec.emphasis) NixPalette.Paper else NixPalette.Ink,
                maxLines = 1,
            )
            Text(
                text = spec.sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (spec.emphasis) NixPalette.Paper.copy(alpha = 0.7f) else NixPalette.Muted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SkillCard(
    skill: SkillItem,
    busy: Boolean,
    myBusy: Boolean,
    tuning: Boolean,
    onToggleTune: () -> Unit,
    onInstallToggle: () -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onRepair: () -> Unit,
    onTrust: () -> Unit,
    onUntrust: () -> Unit,
    onRuntime: (Boolean) -> Unit,
    configDraft: String,
    onConfigDraft: (String) -> Unit,
    nameDraft: String,
    nameDraftChanged: (String) -> Unit,
    setupDrafts: Map<String, String>,
    onSetupDraft: (String, String) -> Unit,
    draftError: String?,
    onSaveConfig: () -> Unit,
    onSaveSetup: () -> Unit,
    onSaveName: () -> Unit,
) {
    SectionCard(
        title = skill.name,
        subtitle = "${skill.version} · ${skill.category} · ${if (skill.installed) "installed" else "not installed"}",
        loading = myBusy,
        modifier = Modifier.animateContentSize(),
    ) {
        // Lifecycle rail — the answer to "where does this skill stand?"
        SkillProgressRail(skill)
        if (skill.description.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = skill.description,
                style = MaterialTheme.typography.bodySmall,
                color = NixPalette.Muted,
            )
        }
        skill.runtimeError?.let { ErrorNote(it) }
        if (skill.installed && skill.runtimeDeclared && skill.fingerprint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Verified package · ${skill.fingerprint}",
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
            )
        }
        Spacer(Modifier.height(10.dp))
        // Actions as a tile grid — labelled buttons instead of a wall of
        // identical outlined pills.
        val actions = buildList {
            if (!skill.installed) {
                add(SkillActionSpec("Install", "add to this host", !busy, true, onInstallToggle))
            } else {
                add(SkillActionSpec("Uninstall", "remove from host", !busy, false, onInstallToggle))
                if (skill.autoUpdateSupported) {
                    add(
                        SkillActionSpec(
                            if (skill.autoUpdate) "Auto-update on" else "Auto-update off",
                            "keep the package fresh",
                            !busy,
                            false,
                        ) { onAutoUpdate(!skill.autoUpdate) },
                    )
                }
                if (skill.metadataRefreshSupported) {
                    add(SkillActionSpec("Repair", "re-download package", !busy, false, onRepair))
                }
                if (skill.runtimeDeclared) {
                    if (skill.configured && !skill.trusted && skill.fingerprint != null) {
                        add(SkillActionSpec("Trust package", "review digest first", !busy, false, onTrust))
                    }
                    if (skill.trusted) {
                        add(SkillActionSpec("Untrust", "stop and clear trust", !busy, false, onUntrust))
                    }
                    if (skill.trusted && skill.configured) {
                        add(
                            SkillActionSpec(
                                if (skill.workerRunning) "Disconnect" else "Connect",
                                if (skill.workerRunning) "worker is live" else "start the worker",
                                !busy,
                                !skill.workerRunning,
                            ) { onRuntime(!skill.workerRunning) },
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { spec ->
                        SkillActionButton(spec, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (skill.installed) {
            TextButton(onClick = onToggleTune) {
                Text(if (tuning) "Hide settings" else "Tune settings")
            }
            if (tuning) {
                if (skill.setupFields.isNotEmpty()) {
                    Text(
                        text = "Setup values required by this skill. Secrets are stored on the " +
                            "host and never shown back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NixPalette.Muted,
                    )
                    skill.setupFields.forEach { field ->
                        SetupFieldInput(
                            field = field,
                            value = setupDrafts[field.id].orEmpty(),
                            stored = field.secret && secretConfigured(skill, field.id),
                            enabled = !busy,
                            onChange = { onSetupDraft(field.id, it) },
                        )
                    }
                    draftError?.let { ErrorNote(it) }
                    OutlinedButton(onClick = onSaveSetup, enabled = !busy) { Text("Save setup") }
                } else {
                    OutlinedTextField(
                        value = configDraft,
                        onValueChange = onConfigDraft,
                        label = { Text("Configuration (JSON)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                    )
                    draftError?.let { ErrorNote(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSaveConfig, enabled = !busy) { Text("Save settings") }
                    }
                }
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = nameDraftChanged,
                    label = { Text("Device name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
                OutlinedButton(onClick = onSaveName, enabled = !busy) { Text("Save name") }
            }
        }
    }
}

/** One declared setup field: masked input for secrets, help text below. */
@Composable
private fun SetupFieldInput(
    field: SetupField,
    value: String,
    stored: Boolean,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(field.label + if (field.required && !stored) " *" else "") },
        placeholder = {
            Text(
                when {
                    field.secret && stored -> "Saved · leave blank to keep"
                    field.help.isNotBlank() -> field.help
                    else -> ""
                },
            )
        },
        supportingText = if (field.help.isNotBlank() && !field.secret) {
            { Text(field.help) }
        } else null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (field.secret) {
            PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (field.secret) KeyboardType.Password else KeyboardType.Text,
        ),
    )
}

/**
 * Install / uninstall confirmation, drawn as a card instead of a wall of
 * text: icon, meta pills, a plain-language checklist of what will happen
 * and the declared permissions up front.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SkillInstallDialog(
    skill: SkillItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val installing = !skill.installed
    val (tint, ink) = skillTint(skill)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.5f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = skill.name.firstOrNull()?.uppercase() ?: "✦",
                    color = ink,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        title = {
            Text(
                text = if (installing) "Install ${skill.name}?" else "Uninstall ${skill.name}?",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    MetaPill("v${skill.version}", ink, tint)
                    MetaPill(skill.category, ink, tint)
                    if (skill.community) MetaPill("community", ink, tint)
                }
                if (skill.description.isNotBlank()) {
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = NixPalette.Muted,
                    )
                }
                val steps = if (installing) {
                    listOf(
                        "Copies ${skill.id} onto the NIX host",
                        "Installing never runs the skill's code",
                        "Trust is a separate review step afterwards",
                    )
                } else {
                    listOf(
                        "Removes ${skill.id} from the host",
                        "Configuration and data are kept unless the skill deletes them",
                    )
                }
                steps.forEach { step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = if (installing) ink else NixPalette.Muted,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall,
                            color = NixPalette.Ink,
                        )
                    }
                }
                if (installing && skill.permissions.isNotEmpty()) {
                    Text(
                        text = "DECLARED PERMISSIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Muted,
                        letterSpacing = 1.4.sp,
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        skill.permissions.forEach { permission ->
                            Surface(shape = RoundedCornerShape(50), color = tint) {
                                Text(
                                    text = permission,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ink,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = NixPalette.Ink,
                    contentColor = NixPalette.Paper,
                ),
                shape = RoundedCornerShape(50),
            ) {
                Text(if (installing) "Install" else "Uninstall")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
