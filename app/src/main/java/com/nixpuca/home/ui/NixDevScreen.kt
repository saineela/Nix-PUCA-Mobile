package com.nixpuca.home.ui

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nixpuca.home.NixViewModel
import com.nixpuca.home.Screen
import com.nixpuca.home.data.NixBleUuids
import com.nixpuca.home.data.NixDevBle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Nix Dev unit's own page: a gradient hero carrying the unit's live
 * identity (advertised name, stable ID, online state), its hardware/stats
 * readout, and every owner action as a full-width graphical row. The Devices
 * list stays a scannable index; everything about one unit lives here.
 */
@Composable
fun NixDevScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val address = vm.nixDevAddress

    // System back mirrors the in-page "‹ Devices" affordance.
    BackHandler { vm.navigate(Screen.Devices) }

    if (address == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No unit selected.", color = NixPalette.Muted)
        }
        return
    }

    var probe by remember(address) { mutableStateOf(DevProbe(busy = true)) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var pendingUnpair by remember { mutableStateOf(false) }
    val actionBle = remember { NixDevBle(context) }

    DisposableEffect(Unit) { onDispose { actionBle.close() } }

    // Probe this unit on open and whenever a tick lands (refresh, rename,
    // unpair) — the unit's own INFO is the source of truth for everything
    // this page renders.
    LaunchedEffect(address, vm.nixDevTick) {
        val granted = requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!granted) {
            probe = DevProbe(error = "Open “Pair a device” once to grant this app Bluetooth access.")
            return@LaunchedEffect
        }
        probe = DevProbe(busy = true)
        val ble = NixDevBle(context)
        try {
            // LE links flake (the phone often holds an A2DP audio link to
            // the same unit): quiet retries turn a dropped first attempt
            // into a normal load instead of an error page.
            var done: DevProbe? = null
            var failure: Throwable? = null
            for (attempt in 1..3) {
                try {
                    ble.connect(address)
                    val info = ble.readInfo()
                    done = when {
                        !info.paired -> {
                            if (vm.isPaired(address)) vm.unmarkPaired(address)
                            DevProbe(info = info)
                        }
                        info.owner.isBlank() && vm.isPaired(address) -> {
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
            probe = done ?: DevProbe(
                error = failure?.message ?: "Not reachable over Bluetooth.",
            )
        } finally {
            ble.close()
        }
    }

    val info = probe.info
    val foreign = info?.paired == true &&
        info.owner.isNotBlank() &&
        !vm.ownsPairing(info.owner, address)
    val resetOnUnit = info?.paired == false
    val controlsEnabled = info != null && !foreign && !resetOnUnit &&
        probe.error == null && !probe.busy

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(2.dp))

        // Back to the device index.
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .tapScale { vm.navigate(Screen.Devices) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", style = MaterialTheme.typography.titleMedium, color = NixPalette.Accent)
            Spacer(Modifier.width(4.dp))
            Text(
                "Devices",
                style = MaterialTheme.typography.labelLarge,
                color = NixPalette.Accent,
            )
        }

        // ------------------------------------------------------------ hero
        UnitHero(
            name = info?.name ?: "Nix Dev",
            id = info?.id.orEmpty(),
            mac = info?.mac.orEmpty(),
            model = info?.model.orEmpty(),
            busy = probe.busy,
            online = info?.online == true,
            ip = info?.ip.orEmpty(),
            foreign = foreign,
            resetOnUnit = resetOnUnit,
            ownerLabel = if (info != null && info.owner.isNotBlank()) ownerLabel(info.owner) else "",
        )

        // ----------------------------------------------------------- stats
        when {
            probe.busy -> StatusCard(text = "Checking the unit over Bluetooth…")
            probe.error != null -> {
                StatusCard(text = probe.error!!, danger = true)
                OutlinedButton(
                    onClick = { vm.nixDevTick++ },
                    border = BorderStroke(1.dp, NixPalette.Accent),
                ) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp), tint = NixPalette.Accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Try again", color = NixPalette.Accent)
                }
            }
            info != null && !foreign -> DevStatsGrid(info)
        }

        actionError?.let { ErrorNote(it) }

        // -------------------------------------------------------- controls
        if (controlsEnabled && info != null) {
            SectionCard(
                title = "Controls",
                subtitle = "Everything you can do with this unit",
            ) {
                ControlRow(
                    icon = Icons.Outlined.Wifi,
                    title = "Change Wi-Fi",
                    hint = "Reconnect the unit to another network",
                    enabled = !actionBusy,
                    onClick = {
                        vm.requestedPairAddress = address
                        vm.navigate(Screen.PairDevice)
                    },
                )
                ControlRow(
                    icon = Icons.Outlined.Edit,
                    title = "Rename",
                    hint = "Set the Bluetooth name other devices see",
                    enabled = !actionBusy,
                    onClick = {
                        renameDraft = info.name
                        showRename = true
                    },
                )
                ControlRow(
                    icon = Icons.Outlined.Refresh,
                    title = "Re-check",
                    hint = "Read the unit's live state again",
                    enabled = !actionBusy && !probe.busy,
                    onClick = { vm.nixDevTick++ },
                )
                ControlRow(
                    icon = Icons.Outlined.Delete,
                    title = "Unpair",
                    hint = "Release the unit so any account can pair it",
                    danger = true,
                    enabled = !actionBusy,
                    onClick = { pendingUnpair = true },
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (actionBusy) {
                        CircularProgressIndicator(
                            Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = NixPalette.Ink,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = "Bluetooth · keep the unit nearby — BLE address $address",
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Muted,
                    )
                }
            }
        }

        if (resetOnUnit) {
            SectionCard(title = "Pairing cleared", subtitle = "The unit is back in setup mode") {
                Text(
                    "This unit's pairing was cleared on the device itself. Pair it " +
                        "again to bring it back under this account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NixPalette.Muted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        vm.requestedPairAddress = address
                        vm.navigate(Screen.PairDevice)
                    },
                    border = BorderStroke(1.dp, NixPalette.Accent),
                ) {
                    Text("Pair it again", color = NixPalette.Accent)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
    }

    // --------------------------------------------------- rename / unpair
    if (showRename) {
        val draft = renameDraft.trim()
        AlertDialog(
            onDismissRequest = { if (!actionBusy) showRename = false },
            title = { Text("Rename this Nix Dev", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it.take(32) },
                        label = { Text("Bluetooth name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = if (draft.isEmpty()) {
                            "The unit advertises this name to every phone nearby."
                        } else {
                            "Advertises as “$draft”" +
                                if (draft.length > 26) " — the unit keeps the first 26 characters." else ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Muted,
                    )
                    if (actionBusy) {
                        Text("Saving…", style = MaterialTheme.typography.bodySmall, color = NixPalette.Muted)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRename = false
                        actionBusy = true
                        actionError = null
                        scope.launch {
                            try {
                                actionBle.connect(address)
                                actionBle.write(NixBleUuids.SUBMIT, "name $draft".toByteArray())
                                actionBle.close()
                                vm.nixDevTick++
                            } catch (t: Throwable) {
                                actionError = t.message ?: "Could not rename over Bluetooth."
                            } finally {
                                actionBusy = false
                            }
                        }
                    },
                    enabled = draft.isNotEmpty() && !actionBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NixPalette.Ink,
                        contentColor = NixPalette.Paper,
                    ),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            },
        )
    }

    if (pendingUnpair) {
        ConfirmDialog(
            title = "Unpair this Nix Dev?",
            message = "The unit is released from this account and can be paired by any " +
                "NIX Home app — including a different account. Its Wi-Fi settings stay " +
                "on the unit; the Echo Connect skill stays on your console.",
            confirmLabel = "Unpair",
            onConfirm = {
                pendingUnpair = false
                actionBusy = true
                actionError = null
                scope.launch {
                    try {
                        actionBle.connect(address)
                        actionBle.write(NixBleUuids.SUBMIT, "unpair".toByteArray())
                        actionBle.close()
                        vm.unmarkPaired(address)
                        vm.nixDevTick++
                    } catch (t: Throwable) {
                        actionError = t.message ?: "Could not unpair over Bluetooth."
                    } finally {
                        actionBusy = false
                    }
                }
            },
            onDismiss = { pendingUnpair = false },
        )
    }
}

/**
 * Gradient identity panel: breathing Bluetooth glyph, the unit's advertised
 * name, its stable ID, and a pulsing online pill — the one place that answers
 * "which unit is this and is it here right now?".
 */
@Composable
private fun UnitHero(
    name: String,
    id: String,
    mac: String,
    model: String,
    busy: Boolean,
    online: Boolean,
    ip: String,
    foreign: Boolean,
    resetOnUnit: Boolean,
    ownerLabel: String,
) {
    val transition = rememberInfiniteTransition(label = "hero")
    val breathe by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "breathe",
    )
    val dotAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "dot",
    )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, NixPalette.Accent.copy(alpha = 0.55f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            NixPalette.Accent,
                            NixPalette.Accent.copy(alpha = 0.78f),
                        ),
                    ),
                ),
        ) {
            // Soft decorative rings for depth (offset, not padding —
            // Compose rejects negative padding).
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 26.dp, y = (-34).dp)
                    .size(140.dp)
                    .background(Color.White.copy(alpha = 0.09f), CircleShape),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-30).dp, y = 42.dp)
                    .size(120.dp)
                    .background(Color.White.copy(alpha = 0.07f), CircleShape),
            )

            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Breathing glyph disc.
                    Box(
                        Modifier
                            .size(52.dp * breathe)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Bluetooth,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildList {
                                if (model.isNotBlank() && model != "—") add(model)
                                if (mac.isNotBlank()) add(mac)
                            }.joinToString(" · ").ifBlank { "Nix Dev unit" },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (id.isNotBlank()) {
                        HeroChip("ID $id")
                    }
                    when {
                        busy -> HeroChip("Checking…")
                        foreign -> HeroChip("No access")
                        resetOnUnit -> HeroChip("Setup mode")
                        online -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .background(
                                            Color(0xFF3BE07C).copy(alpha = dotAlpha),
                                            CircleShape,
                                        ),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (ip.isNotBlank()) "Online · $ip" else "Online",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                        }
                        else -> HeroChip("Offline")
                    }
                }

                Spacer(Modifier.height(10.dp))

                val footNote = when {
                    foreign -> "Connected to a different account — no access" +
                        if (ownerLabel.isNotBlank()) " (owner: $ownerLabel)" else ""
                    resetOnUnit -> "Pairing was cleared on the unit — pair it again to reconnect."
                    else -> buildString {
                        append(if (ownerLabel.isNotBlank()) "Paired to $ownerLabel" else "Paired")
                        if (online && ip.isNotBlank()) append(" · online at $ip")
                    }
                }
                Text(
                    text = footNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }
    }
}

/** Small translucent pill inside the hero. */
@Composable
private fun HeroChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.18f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Small status panel used while probing or when the probe failed. */
@Composable
private fun StatusCard(text: String, danger: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, if (danger) NixPalette.Bad else NixPalette.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (danger) NixPalette.Bad else NixPalette.Muted,
            )
        }
    }
}

/** One full-width control row: icon disc, label + hint, chevron. */
@Composable
private fun ControlRow(
    icon: ImageVector,
    title: String,
    hint: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val tint = if (danger) NixPalette.Bad else NixPalette.Accent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .tapScale(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = if (danger) 0.13f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (danger) NixPalette.Bad else NixPalette.Ink,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
            )
        }
        Icon(
            Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = NixPalette.Muted,
            modifier = Modifier.size(20.dp),
        )
    }
}
