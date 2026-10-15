package com.nixpuca.home.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nixpuca.home.DeviceControl
import com.nixpuca.home.DeviceControlField
import com.nixpuca.home.DeviceItem
import com.nixpuca.home.NixViewModel
import com.nixpuca.home.StatusChip
import com.nixpuca.home.data.NixBleUuids
import com.nixpuca.home.data.NixDevBle
import com.nixpuca.home.data.NixDevInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Devices, Alexa-style: a grid of device tiles that mirror each device's live
 * status. Tapping a tile opens that device's own page with its graphical
 * visual, reported state, controls and settings. Connect, disconnect, trust
 * and every control are confirmed before they touch the host.
 */
@Composable
fun DevicesScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    // Always re-read: brightness/effects changed on the device have to show
    // up when the user comes back, not just on first launch.
    LaunchedEffect(Unit) { vm.loadDevices() }
    // …and again when a device page opens, so its state is the server's
    // current truth rather than the grid's cached snapshot.
    LaunchedEffect(vm.openDeviceId) {
        if (vm.openDeviceId != null) vm.loadDevices()
    }

    var pending by remember { mutableStateOf<DeviceItem?>(null) }
    var pendingTrust by remember { mutableStateOf<DeviceItem?>(null) }

    val openDevice = vm.deviceItems.firstOrNull { it.id == vm.openDeviceId }

    Box(modifier.fillMaxSize()) {
        if (openDevice != null) {
            DeviceDetailPage(
                device = openDevice,
                // Only this device's own round trip blocks its controls — an
                // unrelated install/tune elsewhere must not freeze the deck.
                busy = vm.skillBusyId == openDevice.id,
                loading = vm.skillBusyId == openDevice.id,
                actionError = vm.dataError["skill-action"],
                notice = vm.skillNotice,
                onBack = { vm.openDeviceId = null },
                onRefresh = { vm.loadDevices() },
                onConnectToggle = { pending = openDevice },
                onStatus = {
                    vm.skillAction(
                        openDevice.id,
                        "device_status",
                        notice = "Refreshed ${openDevice.label}",
                    )
                },
                onTrust = { pendingTrust = openDevice },
                onRename = { name ->
                    vm.skillAction(
                        openDevice.id,
                        "device_name",
                        JSONObject().put("device_name", name),
                        "Device name saved for ${openDevice.label}",
                        force = true,
                    )
                },
                onControl = { control, arguments ->
                    vm.skillAction(
                        openDevice.id,
                        "device_control",
                        JSONObject().put("tool", control.tool).put("arguments", arguments),
                        "Sent ${control.label} to ${openDevice.label}",
                    )
                },
                onRingCommand = { action, extra, notice ->
                    vm.ringCommand(action, extra, notice)
                },
                ringEffects = vm.ringEffects,
                ringOn = vm.ringState?.on == true,
                // Live (optimistically updated) ring state so a tapped effect
                // repaints the visual and the selected chip immediately.
                liveRingState = vm.ringState,
            )
        } else {
            DeviceGrid(vm = vm, onOpen = { vm.openDeviceId = it })
        }
    }

    pending?.let { device ->
        val connecting = !device.connected
        ConfirmDialog(
            title = if (connecting) "Connect ${device.label}?" else "Disconnect ${device.label}?",
            message = if (connecting) {
                "NIX will start talking to this device. Its status will appear here once it answers."
            } else {
                "NIX will stop talking to this device. It will show as offline until you connect again."
            },
            confirmLabel = if (connecting) "Connect" else "Disconnect",
            onConfirm = {
                pending = null
                vm.skillAction(
                    device.id,
                    if (connecting) "device_connect" else "disconnect",
                    notice = if (connecting) "${device.label} connecting…" else "${device.label} disconnected",
                    // Device toggles are latency-critical: never dropped.
                    force = true,
                )
            },
            onDismiss = { pending = null },
        )
    }

    pendingTrust?.let { device ->
        ConfirmDialog(
            title = "Trust ${device.name}?",
            message = "Only trust skills from sources you know. A trusted skill runs with your NIX " +
                "user's full access to files and network on the host.",
            confirmLabel = "Trust this skill",
            onConfirm = {
                val digest = device.fingerprint
                pendingTrust = null
                if (digest != null) {
                    vm.skillAction(
                        device.id,
                        "trust",
                        JSONObject().put("confirm_digest", digest),
                        "Trusted ${device.name}",
                        force = true,
                    )
                }
            },
            onDismiss = { pendingTrust = null },
        )
    }
}

// ---------------------------------------------------------------- grid

/** Tile grid of every device skill, with a friendly empty state. */
@Composable
private fun DeviceGrid(vm: NixViewModel, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Devices",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                val total = vm.deviceItems.size
                val online = vm.deviceItems.count {
                    it.connected && it.deviceConnected != false
                }
                Text(
                    text = when {
                        vm.loading["devices"] == true -> "Looking for devices…"
                        total == 0 -> "No devices connected yet"
                        else -> "$online of $total online"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NixPalette.Muted,
                )
            }
            if (vm.loading["devices"] == true) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NixPalette.Ink)
            } else {
                IconButton(onClick = { vm.loadDevices() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh devices")
                }
            }
        }

        // Entry point for the Bluetooth pairing screen.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.navigate(com.nixpuca.home.Screen.PairDevice) },
            shape = RoundedCornerShape(18.dp),
            color = NixPalette.Mist,
            border = BorderStroke(1.dp, NixPalette.Line),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NixPalette.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        color = NixPalette.OnAccent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Pair a device",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Find a nearby Nix Dev over Bluetooth",
                        style = MaterialTheme.typography.bodySmall,
                        color = NixPalette.Muted,
                    )
                }
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = NixPalette.Muted,
                )
            }
        }

        // The Nix Dev hardware itself: pairing, Wi-Fi and account state.
        NixDevSection(vm = vm)

        if (vm.deviceItems.isEmpty() && vm.loading["devices"] != true) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = NixPalette.Mist,
                border = BorderStroke(1.dp, NixPalette.Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No devices yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Install a skill that connects a device from the Skills page, " +
                            "then it will show up here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NixPalette.Muted,
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = { vm.navigate(com.nixpuca.home.Screen.PairDevice) }) {
                        Text("Pair a device")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.navigate(com.nixpuca.home.Screen.Skills) }) {
                        Text("Browse skills")
                    }
                }
            }
        }

        vm.deviceItems.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { device ->
                    DeviceTile(
                        device = device,
                        // Ring tiles read the view model's live state so a
                        // just-tapped effect shows in the grid immediately.
                        liveState = vm.ringState,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen(device.id) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        vm.dataError["devices"]?.let { ErrorNote(it) }
        vm.dataError["skill-action"]?.let { ErrorNote(it) }
        vm.skillNotice?.let { Text(it, color = NixPalette.Ok) }

        Spacer(Modifier.height(4.dp))
    }
}

// ------------------------------------------------------------ Nix Dev units

/** Live BLE probe result for one paired Nix Dev unit. */
internal data class DevProbe(
    val info: NixDevInfo? = null,
    val error: String? = null,
    val busy: Boolean = false,
)

/**
 * The Nix Dev hardware section: every unit this account has paired, probed
 * live over Bluetooth. Each row is a compact index — the unit's own state
 * plus a tap target into its full detail page (stats, rename, Wi-Fi,
 * unpair). A unit claimed by a different account shows "no access".
 */
@Composable
private fun NixDevSection(vm: NixViewModel) {
    val context = LocalContext.current
    val addresses = vm.pairedAddresses
    if (addresses.isEmpty()) return

    var probes by remember(addresses) { mutableStateOf(emptyMap<String, DevProbe>()) }

    // Probe every paired unit once per list change — and after a rename or a
    // detail-page action bumps the shared tick — so the row always carries
    // the device's own current truth (it is the source of truth for pairing).
    LaunchedEffect(addresses, vm.nixDevTick) {
        val granted = requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!granted) {
            probes = addresses.associateWith {
                DevProbe(error = "Open “Pair a device” once to grant this app Bluetooth access.")
            }
            return@LaunchedEffect
        }
        probes = addresses.associateWith { DevProbe(busy = true) }
        val ble = NixDevBle(context)
        try {
            for (addr in addresses) {
                // LE links flake (the phone often holds an A2DP audio link to
                // the same unit): a couple of quiet retries turn a dropped
                // first attempt into a normal load instead of an error card.
                var done: DevProbe? = null
                var failure: Throwable? = null
                for (attempt in 1..3) {
                    try {
                        ble.connect(addr)
                        val info = ble.readInfo()
                        done = when {
                            !info.paired -> {
                                // The unit was reset elsewhere: drop our record.
                                if (vm.isPaired(addr)) vm.unmarkPaired(addr)
                                DevProbe(info = info)
                            }
                            info.owner.isBlank() && vm.isPaired(addr) -> {
                                // Legacy unit with no recorded owner: claim it
                                // for this account so ownership survives.
                                runCatching {
                                    ble.write(
                                        NixBleUuids.SUBMIT,
                                        "paired ${vm.hubIdentity()}".toByteArray(),
                                    )
                                }
                                DevProbe(info = info.copy(owner = vm.hubIdentity()))
                            }
                            else -> DevProbe(info = info)
                        }
                        break
                    } catch (t: Throwable) {
                        failure = t
                        if (attempt < 3) delay(1_500)
                    } finally {
                        ble.close()
                    }
                }
                probes = probes +
                    (addr to (done ?: DevProbe(
                        error = failure?.message ?: "Not reachable over Bluetooth.",
                    )))
            }
        } finally {
            ble.close()
        }
    }

    Text(
        text = "NIX DEV",
        style = MaterialTheme.typography.labelMedium,
        color = NixPalette.Muted,
        letterSpacing = 2.sp,
    )

    addresses.forEach { addr ->
        val probe = probes[addr] ?: DevProbe(busy = true)
        val info = probe.info
        val foreign = info?.paired == true &&
            info.owner.isNotBlank() &&
            !vm.ownsPairing(info.owner, addr)
        val resetOnUnit = info?.paired == false

        // Compact index row — tap through to the unit's own page for the
        // full hardware readout, controls and dialogs.
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = NixPalette.Mist,
            border = BorderStroke(1.5.dp, if (foreign) NixPalette.Line else NixPalette.Ok),
            modifier = Modifier
                .fillMaxWidth()
                .tapScale { vm.openNixDev(addr) },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NixPalette.Paper),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Bluetooth,
                            contentDescription = null,
                            tint = NixPalette.Accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            // The unit's own advertised name — carries its
                            // ID ("Nix Dev AE72") or a custom rename.
                            text = info?.name ?: "Nix Dev",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildString {
                                if (!info?.id.isNullOrBlank()) append("ID ${info?.id} · ")
                                append(addr)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = NixPalette.Muted,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    when {
                        probe.busy -> CircularProgressIndicator(
                            Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = NixPalette.Ink,
                        )
                        probe.error != null -> Text(
                            text = "!",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NixPalette.Bad,
                        )
                        foreign -> Text(
                            text = "No access",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NixPalette.Bad,
                        )
                        resetOnUnit -> Text(
                            text = "Setup mode",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NixPalette.Muted,
                        )
                        info?.online == true -> Text(
                            text = "Online",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NixPalette.Ok,
                        )
                        else -> Text(
                            text = "Offline",
                            style = MaterialTheme.typography.labelMedium,
                            color = NixPalette.Muted,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleLarge,
                        color = NixPalette.Muted,
                    )
                }

                val note = when {
                    probe.error != null -> probe.error
                    foreign -> "Connected to a different account — no access"
                    resetOnUnit -> "Pairing was cleared on the unit — tap to pair again."
                    info == null -> null
                    else -> buildString {
                        append("Paired")
                        if (info.owner.isNotBlank()) append(" to ${ownerLabel(info.owner)}")
                        if (info.online) append(" · online at ${info.ip}")
                        else append(" · no network address")
                        if (info.ssid.isNotBlank()) append(" · ${info.ssid}")
                    }
                }
                if (note != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            probe.error != null -> NixPalette.Bad
                            foreign -> NixPalette.Bad
                            info?.online == true -> NixPalette.Ok
                            else -> NixPalette.Muted
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Two-column hardware + live-stat readout for one unit, straight from its
 * INFO block: model, MAC, IP, network and current provisioning status.
 */
@Composable
internal fun DevStatsGrid(info: NixDevInfo) {
    val rows = listOf(
        "Model" to info.model,
        "MAC" to info.mac,
        "IP" to info.ip.ifBlank { "—" },
        "Network" to info.ssid.ifBlank { "—" },
        "Status" to info.status,
        "Owner" to (info.owner.ifBlank { "—" }),
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NixPalette.Paper,
        border = BorderStroke(1.dp, NixPalette.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            rows.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = NixPalette.Muted,
                                letterSpacing = 1.sp,
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** One device tile: live glyph, name and status; tap to open its page. */
@Composable
private fun DeviceTile(
    device: DeviceItem,
    modifier: Modifier = Modifier,
    liveState: NixViewModel.RingState? = null,
    onClick: () -> Unit,
) {
    val reported = remember(device.status) { parseRingState(device.status) }
    val state = if (remember(device.controls) { device.controls.any { it.tool == "control_ring" } }) {
        liveState ?: reported
    } else {
        reported
    }
    val isConnected = device.connected && device.deviceConnected != false
    val on = state?.on == true && isConnected
    val statusText = when {
        !device.connected -> "offline"
        device.deviceConnected == false -> "not responding"
        state == null -> "connected"
        !state.on -> "off"
        else -> state.effect.takeIf { it.isNotBlank() && !it.equals("none", true) } ?: "on"
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DeviceGlyph(state = state, connected = isConnected)
            Spacer(Modifier.height(10.dp))
            Text(
                text = device.alias.ifBlank { device.label },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    on -> state?.rgb?.let {
                        Color(
                            red = it.getOrElse(0) { 255 } / 255f,
                            green = it.getOrElse(1) { 255 } / 255f,
                            blue = it.getOrElse(2) { 255 } / 255f,
                        )
                    } ?: NixPalette.Ok
                    device.connected -> NixPalette.Muted
                    else -> NixPalette.Muted
                },
            )
        }
    }
}

// ------------------------------------------------------------ detail page

/**
 * One device's own page: big graphical visual, live status, controls and
 * settings, reached by tapping its tile in the grid.
 */
@Composable
private fun DeviceDetailPage(
    device: DeviceItem,
    busy: Boolean,
    loading: Boolean,
    actionError: String?,
    notice: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onConnectToggle: () -> Unit,
    onStatus: () -> Unit,
    onTrust: () -> Unit,
    onRename: (String) -> Unit,
    onControl: (DeviceControl, JSONObject) -> Unit,
    onRingCommand: (String, JSONObject, String) -> Unit,
    ringEffects: List<String>,
    ringOn: Boolean,
    liveRingState: NixViewModel.RingState?,
) {
    var nameDraft by remember(device.id, device.alias) { mutableStateOf(device.alias) }
    // Rename lives behind the pencil next to the title, not a form at the end.
    var renaming by remember(device.id) { mutableStateOf(false) }
    // For the ring light the live view model state is fresher than the last
    // polled status: it already carries the effect the user just tapped.
    val reportedState = remember(device.status) { parseRingState(device.status) }
    val isRingDevice = remember(device.controls) { device.controls.any { it.tool == "control_ring" } }
    val state = if (isRingDevice) liveRingState ?: reportedState else reportedState
    val isConnected = device.connected && device.deviceConnected != false
    // The graphical deck already covers on/off/colour/brightness/effects for
    // the ring light — those specific forms would just repeat it. Every other
    // declared control (volume, mute, speech, sound tuning, output routing,
    // Bluetooth) still gets its generic form below.
    val deckActions = remember { setOf("on", "off", "color", "brightness", "effect") }
    val otherControls = remember(device.controls) {
        device.controls.filter { control ->
            if (control.tool != "control_ring") return@filter true
            control.baseArguments.optString("action") !in deckActions
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹  Devices", color = NixPalette.Accent, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NixPalette.Ink)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh device")
                }
            }
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.alias.ifBlank { device.label },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        nameDraft = device.alias.ifBlank { device.label }
                        renaming = true
                    },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit device name",
                        tint = NixPalette.Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = when {
                    !device.connected -> "Offline"
                    device.deviceConnected == false -> "Connected, not responding"
                    else -> "Connected and responding"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isConnected) NixPalette.Ok else NixPalette.Muted,
            )
        }

        // Graphical device (Echo Dot ring) mirroring the live state.
        EchoDotVisual(
            state = state,
            connected = isConnected,
            label = device.alias.ifBlank { device.label },
            modifier = Modifier.fillMaxWidth(),
        )

        // Graphical control deck: power, brightness dial, colours, effects.
        if (device.controls.any { it.tool == "control_ring" }) {
            LightControlDeck(
                connected = isConnected,
                ringOn = ringOn,
                effects = ringEffects,
                currentEffect = state?.effect.orEmpty(),
                initialBrightness = state?.brightness,
                currentRgb = state?.rgb,
                onCommand = onRingCommand,
            )
        }

        ChipRow(
            listOf(
                StatusChip("Setup", if (device.configured) "complete" else "required", device.configured),
                StatusChip("Trust", if (device.trusted) "trusted" else "not trusted", device.trusted),
                StatusChip("Power", when {
                    !isConnected -> "—"
                    state?.on == true -> "on"
                    else -> "off"
                }, if (isConnected) state?.on == true else null),
            ),
        )

        // Declared status rows (device_ui fields): power, volume, external
        // speaker, output mode, EQ … whatever the skill publishes.
        if (device.fields.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = NixPalette.Mist,
                border = BorderStroke(1.dp, NixPalette.Line),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    device.fields.forEach { field ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = field.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = NixPalette.Muted,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                field.color?.let { hex ->
                                    Box(
                                        Modifier
                                            .size(12.dp)
                                            .background(parseHexColor(hex), CircleShape),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = field.text,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        device.runtimeError?.let { ErrorNote(it) }

        // Controls ------------------------------------------------------
        if (device.connected && device.deviceConnected != false && otherControls.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = NixPalette.Mist,
                border = BorderStroke(1.dp, NixPalette.Line),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Each change is sent to the device straight away.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Muted,
                    )
                    otherControls.forEach { control ->
                        DeviceControlCard(device, control, busy, onControl)
                    }
                }
            }
        }

        // Settings ------------------------------------------------------
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = NixPalette.Mist,
            border = BorderStroke(1.dp, NixPalette.Line),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "Device settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Rename with the pencil next to the name above.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onConnectToggle, enabled = !busy) {
                        Text(if (device.connected) "Disconnect" else "Connect")
                    }
                    OutlinedButton(onClick = onStatus, enabled = !busy && device.connected) {
                        Text("Refresh status")
                    }
                    if (device.configured && !device.trusted && device.fingerprint != null) {
                        OutlinedButton(onClick = onTrust, enabled = !busy) { Text("Trust") }
                    }
                }
            }
        }

        actionError?.let { ErrorNote(it) }
        notice?.let { Text(it, color = NixPalette.Ok) }
        Spacer(Modifier.height(4.dp))
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Edit device name", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Device name") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = nameDraft.trim()
                        renaming = false
                        if (trimmed.isNotBlank() && trimmed != device.alias) onRename(trimmed)
                    },
                    enabled = !busy && nameDraft.trim().isNotBlank() && nameDraft.trim() != device.alias,
                ) { Text("Save", color = NixPalette.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text("Cancel") }
            },
        )
    }
}

/** One declared control: its inputs plus the Apply button that sends them. */
@Composable
private fun DeviceControlCard(
    device: DeviceItem,
    control: DeviceControl,
    busy: Boolean,
    onControl: (DeviceControl, JSONObject) -> Unit,
) {
    var values by remember(device.id, control.tool) {
        mutableStateOf(initialControlValues(device, control))
    }
    var error by remember(device.id, control.tool) { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = RoundedCornerShape(12.dp),
        color = NixPalette.Paper,
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = control.label,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            control.fields.forEach { field ->
                ControlFieldInput(
                    field = field,
                    value = values[field.name].orEmpty(),
                    enabled = !busy,
                    onChange = { new ->
                        values = values + (field.name to new)
                        error = null
                    },
                )
            }
            error?.let { ErrorNote(it) }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    val arguments = buildControlArguments(control, values)
                    if (arguments == null) {
                        error = controlError(control, values)
                            ?: "Some values are missing or invalid."
                    } else {
                        error = null
                        onControl(control, arguments)
                    }
                },
            ) { Text(if (busy) "Sending…" else "Apply") }
        }
    }
}

/**
 * Graphical light controls: big power buttons, a hand-draggable circular
 * brightness dial, colour presets and the live effect switcher — everything
 * a user touches daily without reading a single form field.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LightControlDeck(
    connected: Boolean,
    ringOn: Boolean,
    effects: List<String>,
    currentEffect: String,
    initialBrightness: Float?,
    currentRgb: List<Int>? = null,
    onCommand: (String, JSONObject, String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { onCommand("on", JSONObject(), "Light turned on") },
                    enabled = connected,
                    border = BorderStroke(1.dp, if (ringOn) NixPalette.Ok else NixPalette.Line),
                ) {
                    Text("On", color = if (ringOn) NixPalette.Ok else NixPalette.Ink)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onCommand("off", JSONObject(), "Light turned off") },
                    enabled = connected,
                ) { Text("Off") }
            }

            Spacer(Modifier.height(14.dp))

            // Hand-draggable circular brightness dial, seeded from the
            // device's reported level so a reopened page shows the saved value.
            BrightnessDial(
                enabled = connected,
                initial = initialBrightness,
                onCommit = { fraction ->
                    // The skill expects 0.05 … 1.0; the dial reads as the level.
                    val level = fraction.coerceIn(0.05f, 1f)
                    onCommand(
                        "brightness",
                        JSONObject().put("brightness", level.toDouble()),
                        "Brightness set to ${Math.round(level * 100)}%",
                    )
                },
            )

            Spacer(Modifier.height(14.dp))

            // Colour presets: one tap sends the colour.
            Text(
                text = "COLOUR",
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
                letterSpacing = 1.4.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LIGHT_COLORS.forEach { (hex, color) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .background(color, RoundedCornerShape(10.dp))
                            .clickable(enabled = connected) {
                                val rgb = parseHexRgb(hex) ?: return@clickable
                                onCommand(
                                    "color",
                                    JSONObject().put("rgb", JSONArray().put(rgb[0]).put(rgb[1]).put(rgb[2])),
                                    "Colour set",
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!ringOn) {
                            Text("·", color = Color(0x66000000), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }

            // Custom colour: a hue ring with a saturation/brightness square
            // inside it. Drag on the ring for hue, inside the square for
            // saturation and brightness; the colour is sent once when the
            // finger lifts so the wire is never spammed mid-drag.
            Spacer(Modifier.height(16.dp))
            Text(
                text = "CUSTOM COLOUR",
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
                letterSpacing = 1.4.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            ColorWheelPicker(
                enabled = connected,
                initialRgb = currentRgb,
                onCommit = { rgb ->
                    onCommand(
                        "color",
                        JSONObject().put(
                            "rgb",
                            JSONArray().put(rgb[0]).put(rgb[1]).put(rgb[2]),
                        ),
                        "Colour set",
                    )
                },
            )

            // Effect switcher — the device advertises its own list.
            if (effects.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "EFFECTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    effects.forEach { effect ->
                        val selected = currentEffect.equals(effect, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) NixPalette.Ink else NixPalette.Paper,
                            border = BorderStroke(1.dp, if (selected) NixPalette.Ink else NixPalette.Line),
// Effects never wait on another skill round trip: the selection is
                            // mirrored locally the instant it is tapped.
                            modifier = Modifier.clickable(enabled = connected) {
                                onCommand(
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
        }
    }
}

/** HSV triple backing the custom colour wheel. */
private data class WheelHsv(val h: Float, val s: Float, val v: Float)

private fun rgbToHsv(rgb: List<Int>?): WheelHsv {
    val out = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        rgb?.getOrNull(0)?.coerceIn(0, 255) ?: 255,
        rgb?.getOrNull(1)?.coerceIn(0, 255) ?: 255,
        rgb?.getOrNull(2)?.coerceIn(0, 255) ?: 255,
        out,
    )
    return WheelHsv(out[0], out[1], out[2])
}

private fun hsvToRgb(c: WheelHsv): List<Int> {
    val argb = android.graphics.Color.HSVToColor(
        floatArrayOf(c.h.coerceIn(0f, 360f), c.s.coerceIn(0f, 1f), c.v.coerceIn(0f, 1f)),
    )
    return listOf(
        android.graphics.Color.red(argb),
        android.graphics.Color.green(argb),
        android.graphics.Color.blue(argb),
    )
}

/**
 * Custom colour picker: a hue ring with a saturation/brightness square
 * inside. Drag on the ring to choose the hue, drag inside the square for
 * saturation and brightness — and the colour is sent exactly once, when
 * the finger lifts, so the device isn't spammed during a drag.
 */
@Composable
private fun ColorWheelPicker(
    enabled: Boolean,
    initialRgb: List<Int>?,
    onCommit: (List<Int>) -> Unit,
) {
    var hsv by remember(initialRgb) { mutableStateOf(rgbToHsv(initialRgb)) }
    var lastSent by remember { mutableStateOf(hsvToRgb(rgbToHsv(initialRgb))) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val current = Color.hsv(
        hsv.h.coerceIn(0f, 360f),
        hsv.s.coerceIn(0f, 1f),
        hsv.v.coerceIn(0f, 1f),
    )

    fun applyPosition(x: Float, y: Float) {
        val w = boxSize.width
        val h = boxSize.height
        if (w == 0 || h == 0) return
        val cx = w / 2f
        val cy = h / 2f
        val outerR = min(cx, cy)
        val innerR = outerR - outerR * 0.26f
        val dx = x - cx
        val dy = y - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist >= innerR) {
            // On the ring: angle from the top, clockwise.
            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
            if (angle < 0f) angle += 360f
            if (angle >= 360f) angle -= 360f
            hsv = hsv.copy(h = angle)
        } else {
            // Inside: saturation left→right, brightness top→bottom.
            val half = innerR * 0.68f
            hsv = hsv.copy(
                s = ((dx + half) / (2f * half)).coerceIn(0f, 1f),
                v = (1f - (dy + half) / (2f * half)).coerceIn(0f, 1f),
            )
        }
    }

    fun commit() {
        if (!enabled) return
        val rgb = hsvToRgb(hsv)
        if (rgb != lastSent) {
            lastSent = rgb
            onCommit(rgb)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(216.dp)
                .onSizeChanged { boxSize = it }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { pos ->
                        applyPosition(pos.x, pos.y)
                        commit()
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragEnd = { commit() },
                        onDragCancel = { commit() },
                    ) { change, _ ->
                        change.consume()
                        applyPosition(change.position.x, change.position.y)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerR = min(cx, cy)
                val ringThickness = outerR * 0.26f
                val innerR = outerR - ringThickness
                val half = innerR * 0.68f

                // Hue ring: 72 coloured arc segments (no conic brush in
                // this Compose version, so segments approximate it).
                val arcSize = Size(outerR * 2f, outerR * 2f)
                val arcTopLeft = Offset(cx - outerR, cy - outerR)
                val segments = 72
                repeat(segments) { i ->
                    val hueDeg = i * 360f / segments
                    drawArc(
                        color = Color.hsv(hueDeg, 1f, 1f),
                        startAngle = -90f + hueDeg,
                        sweepAngle = 360f / segments + 0.9f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = ringThickness),
                    )
                }

                // Saturation × brightness square inside the ring.
                val sqTopLeft = Offset(cx - half, cy - half)
                val sqSize = Size(half * 2f, half * 2f)
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.White, Color.hsv(hsv.h, 1f, 1f))),
                    topLeft = sqTopLeft,
                    size = sqSize,
                )
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                    topLeft = sqTopLeft,
                    size = sqSize,
                )

                // Hue marker sitting on the ring.
                val markerRadius = outerR - ringThickness / 2f
                val angleRad = Math.toRadians((hsv.h - 90.0))
                val hueMarker = Offset(
                    cx + (cos(angleRad) * markerRadius.toDouble()).toFloat(),
                    cy + (sin(angleRad) * markerRadius.toDouble()).toFloat(),
                )
                drawCircle(color = Color.White, radius = ringThickness / 2f, center = hueMarker)
                drawCircle(
                    color = Color.hsv(hsv.h, 1f, 1f),
                    radius = ringThickness / 2f - 3f,
                    center = hueMarker,
                )

                // Saturation/brightness marker inside the square.
                val svMarker = Offset(
                    cx - half + hsv.s * half * 2f,
                    cy - half + (1f - hsv.v) * half * 2f,
                )
                drawCircle(color = Color.White, radius = 11f, center = svMarker)
                drawCircle(color = current, radius = 8f, center = svMarker)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(current)
                    .border(1.dp, NixPalette.Line, RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(8.dp))
            val rgb = hsvToRgb(hsv)
            Text(
                text = "#%02x%02x%02x".format(rgb[0], rgb[1], rgb[2]),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = NixPalette.Ink,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (enabled) "drag the wheel to pick" else "connect the light first",
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
            )
        }
    }
}

/** The six quick colours, kept consistent across controls. */
private val LIGHT_COLORS = listOf(
    "#ffffff" to Color(0xFFF5F5F0),
    "#ffd9a0" to Color(0xFFFFD9A0),
    "#ff8000" to Color(0xFFFF8000),
    "#00ff00" to Color(0xFF00E05A),
    "#0080ff" to Color(0xFF0080FF),
    "#ff00ff" to Color(0xFFFF00C8),
)

/**
 * Circular brightness dial: drag a finger around the ring to set the level.
 * Purely graphical — commits once per drag so the device isn't spammed.
 */
@Composable
private fun BrightnessDial(enabled: Boolean, initial: Float? = null, onCommit: (Float) -> Unit) {
    // Seed from the device's reported brightness (falls back to full).
    var fraction by remember(initial) { mutableStateOf(initial?.coerceIn(0f, 1f) ?: 1f) }
    var dragging by remember { mutableStateOf(false) }

    Box(
        Modifier
            .size(170.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onCommit(fraction)
                    },
                ) { change, _ ->
                    change.consume()
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = change.position.x - cx
                    val dy = change.position.y - cy
                    // Angle from the top, clockwise, mapped to 0…1.
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()).toFloat().toDouble())
                    angle += 90.0
                    if (angle < 0) angle += 360.0
                    fraction = (angle / 360.0).toFloat().coerceIn(0f, 1f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 22f, cap = StrokeCap.Round)
            val inset = 24f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            // Track.
            drawArc(
                color = NixPalette.Line,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            // Level.
            drawArc(
                color = NixPalette.Accent,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${Math.round(fraction * 100)}%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NixPalette.Ink,
            )
            Text(
                text = if (dragging) "release to set" else "drag around the ring",
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
            )
        }
    }
}

/** Default draft for one control: current device colour, first option, blank. */
private fun initialControlValues(device: DeviceItem, control: DeviceControl): Map<String, String> {
    val currentColor = device.fields.firstNotNullOfOrNull { it.color }
    return control.fields.associate { field ->
        field.name to when (field.widget) {
            "boolean" -> "false"
            "select" -> field.options.firstOrNull().orEmpty()
            "color" -> currentColor ?: "#ffffff"
            else -> ""
        }
    }
}

/**
 * Merge the control's fixed arguments with the typed inputs, converted to the
 * types the skill's JSON schema expects. Returns null when a value is invalid.
 */
private fun buildControlArguments(
    control: DeviceControl,
    values: Map<String, String>,
): JSONObject? {
    val args = try {
        JSONObject(control.baseArguments.toString())
    } catch (t: Throwable) {
        JSONObject()
    }
    control.fields.forEach { field ->
        val raw = values[field.name].orEmpty().trim()
        when (field.widget) {
            "boolean" -> args.put(field.name, raw == "true")
            "integer" -> {
                val parsed = raw.toIntOrNull() ?: return null
                args.put(field.name, parsed)
            }
            "number" -> {
                val parsed = raw.toDoubleOrNull() ?: return null
                args.put(field.name, parsed)
            }
            "color" -> {
                val rgb = parseHexRgb(raw) ?: return null
                args.put(field.name, JSONArray().put(rgb[0]).put(rgb[1]).put(rgb[2]))
            }
            else -> args.put(field.name, raw)
        }
    }
    return args
}

private fun controlError(control: DeviceControl, values: Map<String, String>): String? {
    control.fields.forEach { field ->
        val raw = values[field.name].orEmpty().trim()
        when (field.widget) {
            "integer" -> if (raw.toIntOrNull() == null) return "${field.label} must be a whole number."
            "number" -> if (raw.toDoubleOrNull() == null) return "${field.label} must be a number."
            "color" -> if (parseHexRgb(raw) == null) return "${field.label} must be a colour like #ff8800."
            "select" -> if (raw.isBlank()) return "${field.label} needs a value."
        }
    }
    return null
}

@Composable
private fun ControlFieldInput(
    field: DeviceControlField,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    when (field.widget) {
        "boolean" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = value == "true",
                onCheckedChange = { onChange(if (it) "true" else "false") },
                enabled = enabled,
            )
            Text(field.label, style = MaterialTheme.typography.bodyMedium)
        }

        "select" -> {
            var expanded by remember(field.name, field.options) { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = field.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = NixPalette.Muted,
                    modifier = Modifier.width(96.dp),
                )
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
                        Text(value.ifBlank { "choose…" })
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        field.options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onChange(option)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        "color" -> Column {
            Text(
                text = field.label,
                style = MaterialTheme.typography.bodySmall,
                color = NixPalette.Muted,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                COLOR_PRESETS.forEach { preset ->
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(parseHexColor(preset), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.TextButton(
                            onClick = { onChange(preset) },
                            enabled = enabled,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) {
                            Text(
                                text = if (value.equals(preset, true)) "✓" else " ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text("Hex colour") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
        }

        else -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(field.label) },
            supportingText = when {
                field.minimum != null && field.maximum != null -> {
                    { Text("Allowed: ${field.minimum} … ${field.maximum}") }
                }
                else -> null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field.widget == "integer" || field.widget == "number") {
                    KeyboardType.Decimal
                } else {
                    KeyboardType.Text
                },
            ),
        )
    }
}

private val COLOR_PRESETS = listOf(
    "#ffffff",
    "#ffd9a0",
    "#ff0000",
    "#ff8000",
    "#ffff00",
    "#00ff00",
    "#00ffff",
    "#0080ff",
    "#8000ff",
    "#ff00ff",
)

private fun parseHexRgb(hex: String): IntArray? {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6 || cleaned.any { it !in "0123456789abcdefABCDEF" }) return null
    return intArrayOf(
        cleaned.substring(0, 2).toInt(16),
        cleaned.substring(2, 4).toInt(16),
        cleaned.substring(4, 6).toInt(16),
    )
}

private fun parseHexColor(hex: String): Color {
    val rgb = parseHexRgb(hex) ?: return NixPalette.Muted
    return Color(android.graphics.Color.rgb(rgb[0], rgb[1], rgb[2]))
}

/** Read the pretty-printed device_status back into a ring state for the visual. */
internal fun parseRingState(status: String): NixViewModel.RingState? = try {
    val root = org.json.JSONObject(status)
    val state = root.optJSONObject("state") ?: return null
    val rgb = state.optJSONArray("rgb")
    NixViewModel.RingState(
        on = state.opt("on") == true,
        rgb = listOf(
            rgb?.optInt(0) ?: 255,
            rgb?.optInt(1) ?: 255,
            rgb?.optInt(2) ?: 255,
        ),
        effect = state.optString("effect"),
        brightness = state.optDouble("brightness", 1.0).toFloat().coerceIn(0f, 1f),
    )
} catch (t: Throwable) {
    null
}
