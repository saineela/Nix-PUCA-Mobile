package com.nixpuca.home.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume

/** UUIDs of the Nix Dev provisioning service (device side: techo5-prov). */
object NixBleUuids {
    val SERVICE: UUID = UUID.fromString("a11e0001-b007-4a11-a11e-6e6978646576")
    val INFO: UUID = UUID.fromString("a11e0002-b007-4a11-a11e-6e6978646576")
    val SSID: UUID = UUID.fromString("a11e0003-b007-4a11-a11e-6e6978646576")
    val PASS: UUID = UUID.fromString("a11e0004-b007-4a11-a11e-6e6978646576")
    val STATUS: UUID = UUID.fromString("a11e0005-b007-4a11-a11e-6e6978646576")
    val SUBMIT: UUID = UUID.fromString("a11e0006-b007-4a11-a11e-6e6978646576")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

/**
 * The INFO characteristic as JSON: identity plus everything the pairing
 * wizard needs. [psk] is the device's Noise key (base64) — the exact value
 * the echo-connect skill's `api_key` setup field expects; it only ever
 * crosses BLE range once, during pairing. [paired]/[owner] say whether a
 * NIX account has already claimed the unit and which one, so a unit bound
 * to another account can be shown as off-limits instead of re-paired.
 */
data class NixDevInfo(
    val name: String,
    val id: String = "",
    val model: String,
    val mac: String,
    val ip: String,
    val ssid: String,
    val status: String,
    val psk: String,
    val paired: Boolean = false,
    val owner: String = "",
) {
    /** True when the unit already holds an address on some network. */
    val online: Boolean get() = ip.isNotBlank()

    companion object {
        fun parse(json: String): NixDevInfo {
            val o = JSONObject(json)
            return NixDevInfo(
                name = o.optStr("name").ifBlank { "Nix Dev" },
                id = o.optStr("id"),
                model = o.optStr("model").ifBlank { "—" },
                mac = o.optStr("mac"),
                ip = o.optStr("ip"),
                ssid = o.optStr("ssid"),
                status = o.optStr("status"),
                psk = o.optStr("psk"),
                // A pre-owner firmware reports no flag at all: treat the
                // absence as unpaired so pairing never blocks on old data.
                paired = o.optBoolean("paired", false),
                owner = o.optStr("owner"),
            )
        }
    }
}

/**
 * One GATT conversation with a Nix Dev's provisioning service.
 *
 * Operations are suspended and awaited one at a time, which matches the
 * GATT contract: the stack allows a single outstanding request, so a
 * read/write/notify-enable issued while another is in flight would silently
 * fail. Callbacks arrive on a binder thread — completions are thread-safe
 * ([CompletableDeferred]) and callers that touch Compose state must hop to
 * the main thread themselves.
 *
 * Requires BLUETOOTH_CONNECT, which PairDeviceScreen gates on before any of
 * this runs.
 */
@SuppressLint("MissingPermission")
class NixDevBle(private val context: Context) {

    private var gatt: BluetoothGatt? = null
    private var connectGate: CompletableDeferred<Unit>? = null
    private var servicesGate: CompletableDeferred<Unit>? = null
    private var mtuGate: CompletableDeferred<Int>? = null
    private var readGate: CompletableDeferred<ByteArray>? = null
    private var writeGate: CompletableDeferred<Unit>? = null
    private var descGate: CompletableDeferred<Unit>? = null

    /** Latest STATUS notification, delivered on the binder thread. */
    var onStatus: ((String) -> Unit)? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> connectGate?.complete(Unit)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val err = IOException("Bluetooth link dropped (status $status)")
                    connectGate?.completeExceptionally(err)
                    readGate?.completeExceptionally(err)
                    writeGate?.completeExceptionally(err)
                    descGate?.completeExceptionally(err)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                servicesGate?.complete(Unit)
            } else {
                servicesGate?.completeExceptionally(IOException("Service discovery failed (status $status)"))
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) mtuGate?.complete(mtu) else mtuGate?.complete(-1)
        }

        // API 33+ signature. The framework calls this one on new runtimes;
        // the deprecated pair below covers older ones. Overriding both is
        // safe: exactly one fires per read because neither calls super.
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) = finishRead(value, status)

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) = finishRead(characteristic.value ?: ByteArray(0), status)

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeGate?.complete(Unit)
            } else {
                writeGate?.completeExceptionally(IOException("Bluetooth write failed (status $status)"))
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                descGate?.complete(Unit)
            } else {
                descGate?.completeExceptionally(IOException("Notify subscription failed (status $status)"))
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = deliverStatus(value)

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) = deliverStatus(characteristic.value ?: ByteArray(0))
    }

    private fun finishRead(value: ByteArray, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            readGate?.complete(value)
        } else {
            readGate?.completeExceptionally(IOException("Bluetooth read failed (status $status)"))
        }
    }

    private fun deliverStatus(value: ByteArray) {
        onStatus?.invoke(value.decodeToString())
    }

    /**
     * Connect, discover the provisioning service, and ask for a large MTU so
     * the INFO payload (now ~190 bytes incl. psk) reads in one round trip.
     * The MTU step is best effort — a stack that refuses still works, just
     * with blob reads.
     */
    suspend fun connect(address: String) {
        // A retry must not stack connections: drop any half-open link first.
        close()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IOException("This phone has no Bluetooth adapter.")
        if (adapter.isEnabled != true) throw IOException("Bluetooth is off.")
        // Shake the A2DP (audio) link off first: while this phone streams
        // BR/EDR audio to the unit, its controller degrades LE advertising
        // and every provisioning attempt from the owning phone fails.
        // Android also re-connects audio the moment a link comes up, so this
        // runs before each attempt — a beat of music for a reachable device.
        dropAudioLink(address)
        val device: BluetoothDevice = adapter.getRemoteDevice(address)

        connectGate = CompletableDeferred()
        servicesGate = CompletableDeferred()
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)

        withTimeout(CONNECT_TIMEOUT_MS) { connectGate!!.await() }
        // Discovery only starts when asked for; without this the services
        // gate would wait out its full timeout on every connection.
        if (gatt?.discoverServices() != true) {
            close()
            throw IOException("Bluetooth refused service discovery.")
        }
        withTimeout(SERVICE_TIMEOUT_MS) { servicesGate!!.await() }

        val service = gatt?.getService(NixBleUuids.SERVICE)
            ?: run { close(); throw IOException("This device doesn't offer the NIX setup service.") }

        // Best effort; some stacks ignore the request entirely.
        mtuGate = CompletableDeferred()
        val requested = gatt?.requestMtu(517) == true
        if (requested) {
            try {
                withTimeout(4_000) { mtuGate!!.await() }
            } catch (_: Throwable) {
                // Default 23-byte MTU: long values still arrive via blob reads.
            }
        }
        if (service.getCharacteristic(NixBleUuids.INFO) == null) {
            close()
            throw IOException("The setup service is missing its characteristics.")
        }
    }

    /**
     * Disconnect this phone's A2DP (audio) link to [address], if one is up.
     *
     * The TECHO5's controller degrades its connectable LE advertising while
     * it carries a BR/EDR audio stream from this phone, which makes the
     * provisioning service unreachable from exactly the phone that owns the
     * unit (observed live: connects and reads fail while audio is linked,
     * succeed the moment it drops). Android also auto-reconnects A2DP as
     * soon as an ACL to a bonded device appears, so the link has to be
     * shaken off before every GATT attempt. Best effort: a stack that
     * refuses the profile proxy simply continues.
     */
    @SuppressLint("MissingPermission")
    private suspend fun dropAudioLink(address: String) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        runCatching {
            withTimeout(A2DP_DROP_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    var settled = false
                    val listener = object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                            runCatching {
                                val a2dp = proxy as? BluetoothA2dp ?: return@runCatching
                                val dev = adapter.getRemoteDevice(address)
                                val connected =
                                    a2dp.getConnectionState(dev) == BluetoothProfile.STATE_CONNECTED
                                if (connected) {
                                    // disconnect() is @SystemApi — reach it reflectively.
                                    // A stack that refuses the hidden call just leaves
                                    // audio linked; this whole step is best effort.
                                    a2dp.javaClass
                                        .getMethod("disconnect", BluetoothDevice::class.java)
                                        .invoke(a2dp, dev)
                                }
                            }
                            runCatching { adapter.closeProfileProxy(profile, proxy) }
                            if (!settled) {
                                settled = true
                                cont.resume(Unit)
                            }
                        }

                        override fun onServiceDisconnected(profile: Int) {
                            if (!settled) {
                                settled = true
                                cont.resume(Unit)
                            }
                        }
                    }
                    val asked = runCatching {
                        adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
                    }.getOrDefault(false)
                    if (!asked && !settled) {
                        settled = true
                        cont.resume(Unit)
                    }
                }
            }
        }
        // The controller needs a beat to resume connectable advertising.
        delay(A2DP_DROP_SETTLE_MS)
    }

    suspend fun readInfo(): NixDevInfo {
        val raw = read(NixBleUuids.INFO)
        return try {
            NixDevInfo.parse(raw.decodeToString())
        } catch (t: Throwable) {
            throw IOException("The device sent an unreadable info block.")
        }
    }

    suspend fun read(uuid: UUID): ByteArray {
        val g = gatt ?: throw IOException("Not connected.")
        val ch = g.getService(NixBleUuids.SERVICE)?.getCharacteristic(uuid)
            ?: throw IOException("Characteristic missing.")
        val gate = CompletableDeferred<ByteArray>()
        readGate = gate
        if (!g.readCharacteristic(ch)) {
            readGate = null
            throw IOException("Bluetooth refused the read.")
        }
        return withTimeout(OP_TIMEOUT_MS) { gate.await() }
    }

    suspend fun write(uuid: UUID, value: ByteArray) {
        val g = gatt ?: throw IOException("Not connected.")
        val ch = g.getService(NixBleUuids.SERVICE)?.getCharacteristic(uuid)
            ?: throw IOException("Characteristic missing.")
        val gate = CompletableDeferred<Unit>()
        writeGate = gate
        val accepted = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            ch.value = value
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
        if (!accepted) {
            writeGate = null
            throw IOException("Bluetooth refused the write.")
        }
        withTimeout(OP_TIMEOUT_MS) { gate.await() }
    }

    /**
     * Subscribe to STATUS notifications: provisioning progress arrives here
     * ("saving …", "joining …", "connected <ip>", "failed: …", "paired").
     * The device pushes its current value immediately on subscribe, so the
     * caller always starts from a known state.
     */
    suspend fun enableStatusNotifications() {
        val g = gatt ?: throw IOException("Not connected.")
        val ch = g.getService(NixBleUuids.SERVICE)?.getCharacteristic(NixBleUuids.STATUS)
            ?: throw IOException("Status characteristic missing.")
        if (!g.setCharacteristicNotification(ch, true)) {
            throw IOException("This phone refused status notifications.")
        }
        val cccd = ch.getDescriptor(NixBleUuids.CCCD)
            ?: throw IOException("Status descriptor missing.")
        val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val gate = CompletableDeferred<Unit>()
        descGate = gate
        val accepted = if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(cccd, enable) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = enable
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
        if (!accepted) {
            descGate = null
            throw IOException("Bluetooth refused the status subscription.")
        }
        withTimeout(OP_TIMEOUT_MS) { gate.await() }
    }

    /**
     * Drop the link. Safe to call more than once, and safe between wizard
     * runs: the status listener survives so a retried connect starts with
     * its notifications wired.
     */
    fun close() {
        val g = gatt
        gatt = null
        if (g != null) {
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 25_000L
        const val SERVICE_TIMEOUT_MS = 12_000L
        const val OP_TIMEOUT_MS = 12_000L
        const val A2DP_DROP_TIMEOUT_MS = 4_000L
        const val A2DP_DROP_SETTLE_MS = 400L
    }
}
