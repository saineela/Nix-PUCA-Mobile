package com.nixpuca.home.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nixpuca.home.NixViewModel
import com.nixpuca.home.SkillItem
import com.nixpuca.home.data.NixBleUuids
import com.nixpuca.home.data.NixDevBle
import com.nixpuca.home.data.NixDevInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** One nearby device that answers to the Nix Dev name. */
private data class FoundNixDev(
    val address: String,
    val name: String,
    val rssi: Int,
    /** True when the frame carried our provisioning service UUID. */
    val hasService: Boolean = false,
)

/** The one device type this screen recognises. */
private fun isNixDev(name: String?): Boolean =
    name?.contains("Nix Dev", ignoreCase = true) == true

internal fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        // Pre-12 discovery reports results through location.
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/**
 * Pair a device: a Bluetooth-only screen that gates on permission and the
 * Bluetooth radio being on, sweeps for nearby devices, and lists the ones
 * that identify as Nix Dev.
 */
@Composable
fun PairDeviceScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val permissions = remember { requiredPermissions() }
    var hasPermission by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    var btOn by remember { mutableStateOf(adapter?.isEnabled == true) }
    var scanning by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<FoundNixDev>() }
    var pendingPair by remember { mutableStateOf<FoundNixDev?>(null) }
    // Non-null once the wizard owns the screen; the scan section is left
    // behind until the flow finishes or is cancelled.
    var target by remember { mutableStateOf<FoundNixDev?>(null) }
    // Every address that answered as Nix Dev in this sweep: the wizard tries
    // them in order, because a transient scan artifact can win a row while
    // the unit's real address is the one that actually answers.
    var wizardCandidates by remember { mutableStateOf<List<String>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        hasPermission = results.values.isNotEmpty() && results.values.all { it }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        btOn = adapter?.isEnabled == true
    }

    fun addFound(
        address: String?,
        name: String?,
        rssi: Int,
        fromBle: Boolean,
        hasService: Boolean = false,
    ) {
        if (address == null || !isNixDev(name)) return
        // The name is the filter (it may carry a device ID or custom rename,
        // which pushes the 31-byte frame over budget so the service UUID is
        // dropped). A matching name on a BLE frame is accepted whether or not
        // the UUID is present; hasService is kept only as a hint. Classic
        // answers come from the unit's own BD_ADDR and pass on name alone.
        val entry = FoundNixDev(address, name ?: "Nix Dev", rssi, hasService)
        val index = found.indexOfFirst { it.address == address }
        if (index >= 0) {
            if (found[index].rssi != rssi) found[index] = entry
        } else {
            found.add(entry)
        }
    }

    // Classic discovery results + radio state flips.
    DisposableEffect(adapter) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0).toInt()
                        val name = try {
                            device?.name
                        } catch (e: SecurityException) {
                            null
                        }
                        addFound(device?.address, name, rssi, fromBle = false)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        // Keep sweeping until the user pauses it.
                        if (scanning) {
                            scope.launch {
                                delay(2_500)
                                if (scanning && btOn) {
                                    runCatching { adapter?.startDiscovery() }
                                }
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                        if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_OFF) {
                            btOn = state == BluetoothAdapter.STATE_ON
                        }
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    // Ble results arrive off the main thread on some devices; hop before touching state.
    val bleCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device?.address
                val name = try {
                    result.scanRecord?.deviceName ?: result.device?.name
                } catch (e: SecurityException) {
                    null
                }
                mainHandler.post {
                    val hasService = result.scanRecord?.serviceUuids
                        ?.contains(android.os.ParcelUuid(NixBleUuids.SERVICE)) == true
                    addFound(address, name, result.rssi, fromBle = true, hasService = hasService)
                }
            }

            override fun onScanFailed(errorCode: Int) = Unit
        }
    }

    fun startSweep() {
        val a = adapter ?: return
        found.clear()
        scanning = true
        runCatching { a.cancelDiscovery() }
        runCatching { a.startDiscovery() }
        runCatching { a.bluetoothLeScanner?.startScan(bleCallback) }
    }

    fun stopSweep() {
        scanning = false
        runCatching { adapter?.cancelDiscovery() }
        runCatching { adapter?.bluetoothLeScanner?.stopScan(bleCallback) }
    }

    // Open the gates in order: permission, then radio, then sweep.
    LaunchedEffect(hasPermission, btOn) {
        if (hasPermission && btOn) scanning = true
    }

    LaunchedEffect(scanning, hasPermission, btOn) {
        if (scanning && hasPermission && btOn) {
            startSweep()
        } else if (!scanning) {
            stopSweep()
        } else {
            stopSweep()
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopSweep() }
    }

    val results = found.sortedByDescending { it.rssi }

    // "Change Wi-Fi" on the Devices page hands over one address so the
    // wizard opens straight at that unit. The gates above must be open first
    // — until permission (and the radio) are there, the request waits and
    // fires the moment the grant lands.
    LaunchedEffect(vm.requestedPairAddress, hasPermission, btOn) {
        val address = vm.requestedPairAddress ?: return@LaunchedEffect
        if (!hasPermission || !btOn) return@LaunchedEffect
        vm.requestedPairAddress = null
        wizardCandidates = listOf(address)
        target = FoundNixDev(address, "Nix Dev", 0, hasService = true)
    }

    // A chosen device takes over the screen: stop sweeping first so the
    // scanner and the GATT connection don't contend for the radio.
    LaunchedEffect(target) {
        if (target != null && scanning) scanning = false
    }

    val wizardTarget = target
    if (wizardTarget != null) {
        PairWizard(
            vm = vm,
            device = wizardTarget,
            candidates = wizardCandidates,
            onExit = {
                target = null
                scanning = true
            },
            onFinished = {
                target = null
                scanning = true
                vm.navigate(com.nixpuca.home.Screen.Devices)
            },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.navigate(com.nixpuca.home.Screen.Devices) }) {
                Text(
                    text = "‹  Devices",
                    color = NixPalette.Accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.weight(1f))
            if (scanning) {
                CircularProgressIndicator(
                    Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = NixPalette.Ink,
                )
            }
        }

        Column {
            Text(
                text = "Pair a device",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Hold your Nix Dev close and this phone will look for it.",
                style = MaterialTheme.typography.bodyMedium,
                color = NixPalette.Muted,
            )
        }

        when {
            adapter == null -> GateCard(
                title = "No Bluetooth on this phone",
                body = "This phone can't scan for devices because it has no Bluetooth hardware.",
            )

            !hasPermission -> GateCard(
                title = "Allow nearby devices",
                body = "This phone needs your permission before it can look for devices around you.",
                actionLabel = "Allow",
                onClick = { permissionLauncher.launch(permissions) },
            )

            !btOn -> GateCard(
                title = "Turn on Bluetooth",
                body = "Bluetooth is off. Turn it on so this phone can find a Nix Dev nearby.",
                actionLabel = "Turn on Bluetooth",
                onClick = {
                    runCatching {
                        enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                },
            )

            else -> {
                // ------------------------------------------------- radar
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = NixPalette.Mist,
                    border = BorderStroke(1.dp, NixPalette.Line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ScanRadar(active = scanning)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = if (scanning) "Looking for a Nix Dev…" else "Scan paused",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Only Nix Dev devices are recognised",
                            style = MaterialTheme.typography.bodySmall,
                            color = NixPalette.Muted,
                        )
                        Spacer(Modifier.height(14.dp))
                        if (scanning) {
                            OutlinedButton(onClick = { stopSweep() }) {
                                Text("Stop scanning")
                            }
                        } else {
                            OutlinedButton(onClick = { startSweep() }) {
                                Text("Scan again")
                            }
                        }
                    }
                }

                // ------------------------------------------------- results
                Text(
                    text = if (results.isEmpty()) {
                        "NO NIX DEV FOUND YET"
                    } else {
                        "FOUND NEARBY · ${results.size}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = NixPalette.Muted,
                    letterSpacing = 2.sp,
                )

                if (results.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = NixPalette.Mist,
                        border = BorderStroke(1.dp, NixPalette.Line),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                text = "Nothing here yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Keep your Nix Dev powered on and within a metre of the phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NixPalette.Muted,
                            )
                        }
                    }
                }

                results.forEach { device ->
                    val isPaired = vm.isPaired(device.address)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = NixPalette.Paper,
                        border = BorderStroke(1.5.dp, if (isPaired) NixPalette.Ok else NixPalette.Line),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(NixPalette.Mist),
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
                                    text = device.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = device.address,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NixPalette.Muted,
                                    letterSpacing = 0.5.sp,
                                )
                            }
                            SignalBars(rssi = device.rssi)
                            Spacer(Modifier.width(12.dp))
                            if (isPaired) {
                                // A paired unit stays actionable: the wizard is
                                // also how Wi-Fi is changed later over BLE, so
                                // the row must never become a dead end.
                                Text(
                                    text = "Paired",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NixPalette.Ok,
                                )
                                Spacer(Modifier.width(4.dp))
                                TextButton(onClick = { pendingPair = device }) {
                                    Text(
                                        text = "Manage",
                                        fontWeight = FontWeight.SemiBold,
                                        color = NixPalette.Accent,
                                    )
                                }
                            } else {
                                TextButton(onClick = { pendingPair = device }) {
                                    Text(
                                        text = "Pair",
                                        fontWeight = FontWeight.SemiBold,
                                        color = NixPalette.Accent,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        vm.dataError["pair"]?.let { ErrorNote(it) }
        Spacer(Modifier.height(8.dp))
    }

    pendingPair?.let { device ->
        ConfirmDialog(
            title = "Pair with ${device.name}?",
            message = "This phone will connect ${device.name} to your Wi-Fi, pair it with your " +
                "NIX console and install its official Echo Connect skill — all in one flow.",
            confirmLabel = "Pair",
            onConfirm = {
                pendingPair = null
                wizardCandidates =
                    (listOf(device.address) + results.map { it.address }).distinct()
                target = device
            },
            onDismiss = { pendingPair = null },
        )
    }
}

/** Full-width card used for the permission / Bluetooth-off gates. */
@Composable
private fun GateCard(
    title: String,
    body: String,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(NixPalette.Paper),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Bluetooth,
                        contentDescription = null,
                        tint = if (onClick != null) NixPalette.Muted else NixPalette.Line,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = NixPalette.Muted,
            )
            if (actionLabel != null && onClick != null) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    androidx.compose.material3.Button(onClick = onClick) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

/** Pulsing rings that sweep outward while the scan runs. */
@Composable
private fun ScanRadar(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2600, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "sweep",
    )
    val accent = NixPalette.Accent

    Canvas(Modifier.size(130.dp)) {
        val maxR = size.minDimension / 2f
        listOf(0.34f, 0.66f, 1f).forEach { f ->
            drawCircle(
                color = accent.copy(alpha = 0.16f),
                radius = maxR * f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
            )
        }
        if (active) {
            repeat(3) { i ->
                val p = (sweep + i / 3f) % 1f
                drawCircle(
                    color = accent.copy(alpha = (1f - p) * 0.55f),
                    radius = (0.22f + 0.78f * p) * maxR,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()),
                )
            }
        }
        drawCircle(color = accent.copy(alpha = 0.22f), radius = 18.dp.toPx())
        drawCircle(color = accent, radius = 8.dp.toPx())
    }
}

/** Three-bar signal strength readout for a scan result. */
@Composable
private fun SignalBars(rssi: Int) {
    val level = when (rssi) {
        !in -100..0 -> 1
        in -60..0 -> 3
        in -75..-61 -> 2
        else -> 1
    }
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(3) { i ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((6 + i * 5).dp)
                    .background(
                        color = if (i < level) NixPalette.Accent else NixPalette.Line,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

// ======================================================== pairing wizard

/** Where the pairing conversation is after the scan hands over. */
private enum class WizardStage { CONNECTING, ACCESS_DENIED, WIFI, SETUP, DONE }

/** The official skill every paired Nix Dev receives (repo: ring-light-skill). */
private const val ECHO_SKILL_ID = "echo-connect"

/**
 * The real pairing flow, end to end:
 *
 *  1. CONNECTING — GATT link to the unit, read INFO (identity, current IP,
 *     and the Noise key the skill will need), subscribe to STATUS.
 *  2. WIFI — put the unit on the network: write SSID + passphrase, SUBMIT,
 *     and follow the device's own status notifications until it reports an
 *     address (or skip when it already has one).
 *  3. SETUP — let the console install → configure → trust → start the
 *     echo-connect skill, driven off what the server actually reports so a
 *     retry after a failure only repeats what is still missing. Configure
 *     carries the device IP from step 2 and the API key read in step 1.
 *  4. DONE — tell the unit it is paired (this retires its boot greeting),
 *     record it in the app, and celebrate.
 *
 * Everything the unit reports comes straight from its own characteristics:
 * the app never guesses a state it has not read.
 */
@Composable
private fun PairWizard(
    vm: NixViewModel,
    device: FoundNixDev,
    candidates: List<String>,
    onExit: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val ble = remember { NixDevBle(context) }

    var stage by remember { mutableStateOf(WizardStage.CONNECTING) }
    var info by remember { mutableStateOf<NixDevInfo?>(null) }
    var bleError by remember { mutableStateOf<String?>(null) }
    var bleStatus by remember { mutableStateOf("") }
    var connectRun by remember { mutableStateOf(0) }
    // The address that actually answered; pairing is recorded against it.
    var pairedAddress by remember { mutableStateOf(device.address) }

    var ssidDraft by remember { mutableStateOf("") }
    var passDraft by remember { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }
    var joinedIp by remember { mutableStateOf("") }

    var setupMsg by remember { mutableStateOf("Checking what your console already knows…") }
    var setupStep by remember { mutableStateOf(0) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var setupRun by remember { mutableStateOf(0) }

    var celebrated by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        // Notifications arrive on the binder thread; hop before touching state.
        ble.onStatus = { text -> mainHandler.post { bleStatus = text } }
        onDispose { ble.close() }
    }

    // ---------------------------------------------------------- step 1: connect
    LaunchedEffect(device.address, connectRun) {
        stage = WizardStage.CONNECTING
        bleError = null
        // Try every address that answered as Nix Dev, best one first: a
        // stale scan artifact must not strand the wizard on a timeout when
        // the unit's real address is a row away. LE also flakes — a link
        // left half-open times out at 25 s — so run the whole order twice
        // with a quiet beat between passes: an environmental blip then
        // reads as a normal wait instead of the error step.
        val order = (listOf(device.address) + candidates).distinct()
        var connectedTo: String? = null
        var lastError: Throwable? = null
        pass@ for (pass in 1..2) {
            for (address in order) {
                try {
                    ble.connect(address)
                    connectedTo = address
                    break@pass
                } catch (t: Throwable) {
                    lastError = t
                }
            }
            if (pass < 2) delay(1_500)
        }
        if (connectedTo == null) {
            bleError = lastError?.message ?: "Could not reach the device over Bluetooth."
            return@LaunchedEffect
        }
        pairedAddress = connectedTo
        try {
            val read = ble.readInfo()
            info = read
            bleStatus = read.status
            joinedIp = read.ip
            ble.enableStatusNotifications()
            ssidDraft = read.ssid.ifBlank { ssidDraft }
            // A unit claimed by a different NIX account stops the flow here:
            // pairing it would silently take it from the account that owns it.
            if (read.paired && !vm.ownsPairing(read.owner, connectedTo)) {
                stage = WizardStage.ACCESS_DENIED
                return@LaunchedEffect
            }
            stage = WizardStage.WIFI
        } catch (t: Throwable) {
            bleError = t.message ?: "Could not read the device card."
        }
    }

    // ----------------------------------------------------- step 2: status watch
    LaunchedEffect(bleStatus) {
        if (!joining) return@LaunchedEffect
        when {
            bleStatus.startsWith("connected ") -> {
                joinedIp = bleStatus.removePrefix("connected ").trim()
                info = info?.copy(ip = joinedIp)
                joining = false
                joinError = null
                stage = WizardStage.SETUP
            }
            bleStatus.startsWith("failed") -> {
                joinError = bleStatus.removePrefix("failed").trim().removePrefix(":").trim()
                joining = false
            }
        }
    }

    // The unit answers within ~95 s at worst (its own join timeout); a link
    // that stays silent longer than that is a failure, not a wait.
    LaunchedEffect(joining) {
        if (joining) {
            delay(120_000)
            if (joining) {
                joining = false
                joinError = "The device never reported a result — check the password and try again."
            }
        }
    }

    fun joinNetwork() {
        val ssid = ssidDraft.trim()
        val pass = passDraft
        val problem = when {
            ssid.isEmpty() -> "Enter the network name."
            pass.length < 8 -> "The password needs at least 8 characters."
            pass.length > 63 -> "That password is longer than 63 characters."
            else -> null
        }
        if (problem != null) {
            joinError = problem
            return
        }
        joinError = null
        joining = true
        bleStatus = "sending the network to the device"
        scope.launch {
            try {
                ble.write(NixBleUuids.SSID, ssid.toByteArray())
                ble.write(NixBleUuids.PASS, pass.toByteArray())
                // Empty submit = "join the network you just wrote".
                ble.write(NixBleUuids.SUBMIT, ByteArray(0))
            } catch (t: Throwable) {
                joining = false
                joinError = t.message ?: "Could not send the network settings."
            }
        }
    }

    // ------------------------------------------------ step 3: skill setup chain
    LaunchedEffect(stage, setupRun) {
        if (stage != WizardStage.SETUP) return@LaunchedEffect
        setupError = null
        setupStep = 0
        setupMsg = "Checking what your console already knows…"

        val ip = joinedIp.ifBlank { info?.ip.orEmpty() }
        val psk = info?.psk.orEmpty()
        if (ip.isBlank()) {
            setupError = "The device has no network address yet — join Wi-Fi first."
            return@LaunchedEffect
        }
        if (psk.isBlank()) {
            setupError = "The device didn't share its API key over Bluetooth."
            return@LaunchedEffect
        }

        suspend fun reload(): SkillItem? {
            val skill = vm.readSkill(ECHO_SKILL_ID)
            if (skill == null && setupError == null) {
                setupError = vm.dataError["skills"]
                    ?: "The console didn't answer — check the server URL in Settings."
            }
            return skill
        }

        var skill = reload()
        if (skill == null) return@LaunchedEffect

        if (!skill.installed) {
            setupMsg = "Installing Echo Connect on your console…"
            val ok = vm.skillActionAwait(
                skill.id, "install", notice = "Echo Connect installed",
            )
            if (!ok) {
                setupError = vm.dataError["skill-action"] ?: "The console refused the install."
                return@LaunchedEffect
            }
            skill = reload() ?: return@LaunchedEffect
        }

        setupStep = 1
        if (!skill.configured) {
            setupMsg = "Pointing the skill at this device…"
            val ok = vm.skillActionAwait(
                skill.id,
                "configure",
                JSONObject().put(
                    "configuration",
                    JSONObject().put("device_ip", ip).put("api_key", psk),
                ),
                notice = "Echo Connect configured",
            )
            if (!ok) {
                setupError = vm.dataError["skill-action"] ?: "The console refused the settings."
                return@LaunchedEffect
            }
            skill = reload() ?: return@LaunchedEffect
        }

        setupStep = 2
        if (!skill.trusted) {
            val digest = skill.fingerprint
            if (digest.isNullOrBlank()) {
                setupError = "The console reported no package fingerprint to trust."
                return@LaunchedEffect
            }
            setupMsg = "Trusting the signed Echo Connect package…"
            val ok = vm.skillActionAwait(
                skill.id,
                "trust",
                JSONObject().put("confirm_digest", digest),
                notice = "Echo Connect trusted",
            )
            if (!ok) {
                setupError = vm.dataError["skill-action"] ?: "The console refused trust."
                return@LaunchedEffect
            }
            skill = reload() ?: return@LaunchedEffect
        }

        setupStep = 3
        if (!skill.workerRunning) {
            setupMsg = "Starting the device runtime…"
            val ok = vm.skillActionAwait(
                skill.id, "connect", notice = "Echo Connect connected",
            )
            if (!ok) {
                setupError = vm.dataError["skill-action"] ?: "The runtime didn't start."
                return@LaunchedEffect
            }
        }

        // ------------------------------------------------------------ finish
        setupStep = 4
        setupMsg = "Wrapping up…"
        // Tell the unit it is paired — with this account as its owner: from
        // here the boot greeting stays quiet and other accounts see a claim.
        runCatching { ble.write(NixBleUuids.SUBMIT, "paired ${vm.hubIdentity()}".toByteArray()) }
        vm.markPaired(pairedAddress)
        vm.loadDevices()
        stage = WizardStage.DONE
    }

    // ------------------------------------------------------------------ render
    Box(modifier.fillMaxSize()) {
        if (stage == WizardStage.DONE) {
            FallingPaperSparkles(Modifier.fillMaxSize())
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (stage == WizardStage.DONE) onFinished() else onExit() }) {
                    Text(
                        text = if (stage == WizardStage.DONE) "‹  Devices" else "‹  Cancel",
                        color = NixPalette.Accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (stage != WizardStage.DONE &&
                    stage != WizardStage.ACCESS_DENIED &&
                    !(stage == WizardStage.CONNECTING && bleError != null)
                ) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = NixPalette.Ink,
                    )
                }
            }

            when (stage) {
                WizardStage.CONNECTING -> ConnectStep(
                    device = device,
                    error = bleError,
                    onRetry = { connectRun++ },
                    onCancel = onExit,
                )
                WizardStage.WIFI -> WifiStep(
                    device = device,
                    info = info,
                    ssidDraft = ssidDraft,
                    onSsid = { ssidDraft = it },
                    passDraft = passDraft,
                    onPass = { passDraft = it },
                    joining = joining,
                    bleStatus = bleStatus,
                    joinError = joinError,
                    onJoin = { joinNetwork() },
                    onKeep = { stage = WizardStage.SETUP },
                )
                WizardStage.SETUP -> SetupStep(
                    message = setupMsg,
                    step = setupStep,
                    error = setupError,
                    onRetry = { setupRun++ },
                    onCancel = onExit,
                )
                WizardStage.DONE -> DoneStep(
                    device = device,
                    ip = joinedIp,
                    celebrated = celebrated,
                    onCelebrate = { celebrated = true },
                    onFinish = onFinished,
                    onPairAnother = onExit,
                )
                WizardStage.ACCESS_DENIED -> AccessDeniedStep(
                    owner = info?.owner.orEmpty(),
                    onBack = onExit,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Shown instead of the flow when the scanned unit is already claimed by a
 * different NIX account: a Nix Dev belongs to exactly one account, so this
 * one is visible but off-limits until its owner releases it.
 */
@Composable
private fun AccessDeniedStep(
    owner: String,
    onBack: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = NixPalette.Bad,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Connected to a different account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "This Nix Dev already belongs to a NIX account — no access from here. " +
                    "A unit can only be connected to one account at a time.",
                style = MaterialTheme.typography.bodyMedium,
                color = NixPalette.Muted,
                textAlign = TextAlign.Center,
            )
            if (owner.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = NixPalette.Paper,
                    border = BorderStroke(1.dp, NixPalette.Line),
                ) {
                    Text(
                        text = "owner: ${ownerLabel(owner)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Muted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Ask the account that owns it to unpair the device (Devices → " +
                    "Nix Dev → Unpair), or reset its connection on the unit, then try again.",
                style = MaterialTheme.typography.bodySmall,
                color = NixPalette.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}

/** Host part of a stored owner URL, for friendly display (fallback: raw). */
internal fun ownerLabel(owner: String): String =
    owner.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')

/** Step 1: opening the Bluetooth link. */
@Composable
private fun ConnectStep(
    device: FoundNixDev,
    error: String?,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (error == null) {
                CircularProgressIndicator(
                    Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = NixPalette.Ink,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Connecting to ${device.name}…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Opening a Bluetooth link and reading the device card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NixPalette.Muted,
                    textAlign = TextAlign.Center,
                )
            } else {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = NixPalette.Bad,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Couldn't reach ${device.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                ErrorNote(error)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRetry) { Text("Try again") }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}

/** Step 2: the Wi-Fi form, with the unit's own status shown live. */
@Composable
private fun WifiStep(
    device: FoundNixDev,
    info: NixDevInfo?,
    ssidDraft: String,
    onSsid: (String) -> Unit,
    passDraft: String,
    onPass: (String) -> Unit,
    joining: Boolean,
    bleStatus: String,
    joinError: String?,
    onJoin: () -> Unit,
    onKeep: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text(
                text = "Set up ${device.name}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Put your Nix Dev on the network so your console can reach it.",
                style = MaterialTheme.typography.bodyMedium,
                color = NixPalette.Muted,
            )
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = NixPalette.Paper,
            border = BorderStroke(1.dp, NixPalette.Line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NixPalette.Mist),
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
                        text = device.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = "${info?.model ?: "—"} · ${device.address}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Muted,
                        letterSpacing = 0.5.sp,
                    )
                }
                if (info?.online == true) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = NixPalette.Paper,
                        border = BorderStroke(1.dp, NixPalette.Ok),
                    ) {
                        Text(
                            text = info.ip,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = NixPalette.Ok,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }

        if (info?.online == true && !joining) {
            Button(onClick = onKeep, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (info.ssid.isBlank()) {
                        "Continue with the current network"
                    } else {
                        "Continue with “${info.ssid}”"
                    },
                )
            }
            Text(
                text = "or join a different network below",
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = ssidDraft,
            onValueChange = onSsid,
            label = { Text("Network name") },
            singleLine = true,
            enabled = !joining,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = passDraft,
            onValueChange = onPass,
            label = { Text("Password") },
            singleLine = true,
            enabled = !joining,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Sent straight to your Nix Dev over Bluetooth — this app never stores it.",
            style = MaterialTheme.typography.bodySmall,
            color = NixPalette.Muted,
        )

        joinError?.let { ErrorNote(it) }

        if (joining) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = NixPalette.Mist,
                border = BorderStroke(1.dp, NixPalette.Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = NixPalette.Ink,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = bleStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        } else {
            Button(
                onClick = onJoin,
                enabled = ssidDraft.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Join network")
            }
        }
    }
}

/** Step 3: the console-side setup, one row per performed-or-pending step. */
@Composable
private fun SetupStep(
    message: String,
    step: Int,
    error: String?,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val rows = listOf(
        "Install Echo Connect",
        "Configure it for this device",
        "Trust the signed package",
        "Start the device runtime",
    )
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Pairing with your console",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NixPalette.Muted,
            )
            Spacer(Modifier.height(14.dp))
            rows.forEachIndexed { i, label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 5.dp),
                ) {
                    when {
                        i < step -> Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "done",
                            tint = NixPalette.Ok,
                            modifier = Modifier.size(18.dp),
                        )
                        i == step && error == null -> CircularProgressIndicator(
                            Modifier.size(14.dp),
                            strokeWidth = 1.8.dp,
                            color = NixPalette.Ink,
                        )
                        i == step && error != null -> Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = "failed",
                            tint = NixPalette.Bad,
                            modifier = Modifier.size(18.dp),
                        )
                        else -> Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(NixPalette.Line),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (i <= step) NixPalette.Ink else NixPalette.Muted,
                    )
                }
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                ErrorNote(it)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRetry) { Text("Try again") }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}

/** Step 4: congratulations — falling paper, a popping check, the summary. */
@Composable
private fun DoneStep(
    device: FoundNixDev,
    ip: String,
    celebrated: Boolean,
    onCelebrate: () -> Unit,
    onFinish: () -> Unit,
    onPairAnother: () -> Unit,
) {
    LaunchedEffect(Unit) { onCelebrate() }
    val pop by animateFloatAsState(
        targetValue = if (celebrated) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "donePop",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(NixPalette.Ok.copy(alpha = 0.14f))
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = NixPalette.Ok,
                modifier = Modifier.size(64.dp),
            )
        }
        Text(
            text = "You're paired!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${device.name} is on your network and linked to your NIX console. " +
                "Its official skill is installed and running.",
            style = MaterialTheme.typography.bodyMedium,
            color = NixPalette.Muted,
            textAlign = TextAlign.Center,
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = NixPalette.Paper,
            border = BorderStroke(1.dp, NixPalette.Line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(vertical = 16.dp, horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = listOfNotNull(ip.ifBlank { null }, device.address).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                    letterSpacing = 0.5.sp,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = NixPalette.Ok,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Echo Connect installed, trusted and running",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
        ) {
            Text("Go to Devices")
        }
        TextButton(onClick = onPairAnother) {
            Text("Pair another device", color = NixPalette.Accent)
        }
    }
}

