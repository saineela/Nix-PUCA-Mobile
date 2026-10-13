package com.nixpuca.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nixpuca.home.data.ApiResult
import com.nixpuca.home.data.Json
import com.nixpuca.home.data.JsonCard
import com.nixpuca.home.data.JsonRender
import com.nixpuca.home.data.NixApi
import com.nixpuca.home.data.NixConfig
import com.nixpuca.home.data.optStr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject

enum class Screen(val label: String) {
    Home("Home"),
    Chat("Chat"),
    Knowledge("Knowledge"),
    Schedule("Schedule"),
    Skills("Skills"),
    Devices("Devices"),
    Music("Music"),
    NixDev("Nix Dev"),
    PairDevice("Pair a device"),
    System("System"),
    Hardware("Hardware"),
    Settings("Settings"),
    About("About"),
}

/** One chat bubble plus the provenance Core returned with it. */
data class ChatMessage(
    val id: Long,
    val role: String, // user | assistant | error
    val text: String,
    val route: String? = null,
    val rule: String? = null,
    val latencyMs: Double? = null,
    val pipeline: List<Pair<String, String>> = emptyList(),
    /** Assistant that produced the reply (details.assistant_name: Luna/Casper). */
    val assistant: String? = null,
    /** Official model id that produced it (details.official_model). */
    val model: String? = null,
)

/** One selectable official assistant model from GET /api/models. */
data class ModelChoice(
    val id: String,
    val name: String,
    val label: String,
    val present: Boolean,
    val active: Boolean,
)

/** Health/status pill on the Home screen. */
data class StatusChip(val label: String, val value: String, val ok: Boolean?)

/** Pipeline stage row (label, status, milliseconds). */
data class StageRow(val label: String, val status: String, val ms: Double?)

/** One line of the Settings "test connection" report. */
data class TestLine(val text: String, val ok: Boolean?)

/** One past conversation from GET /api/sessions. */
data class ConversationItem(
    val id: String,
    val conversationId: String?,
    val title: String,
    val preview: String,
    val source: String,
    val turnCount: Int,
    val lastTurn: String,
    val turns: List<ChatMessage>,
)

/** One calendar event on the Schedule grid (NIX or Google-sourced). */
data class CalendarEvent(
    val id: String,
    val title: String,
    /** ISO-8601 start string as reported by the source. */
    val start: String,
    val end: String?,
    /** Epoch millis used for grouping on the calendar (0 = undated). */
    val dayMillis: Long,
    val allDay: Boolean,
    val source: String, // nix | google
    val actions: Int = 0,
)

/** One declared setup field (manifest `setup_fields` entry). */
data class SetupField(
    val id: String,
    val label: String,
    val help: String,
    val required: Boolean,
    val secret: Boolean,
)

/** One marketplace entry from GET /api/skills. */
data class SkillItem(
    val id: String,
    val name: String,
    val version: String,
    val category: String,
    val description: String,
    val installed: Boolean,
    val community: Boolean,
    val runnable: Boolean,
    val implementationStatus: String,
    val configuration: String,
    val deviceName: String,
    val fingerprint: String? = null,
    val trusted: Boolean = false,
    val configured: Boolean = false,
    val workerRunning: Boolean = false,
    val runtimeDeclared: Boolean = false,
    val runtimeError: String? = null,
    val autoUpdate: Boolean = false,
    val autoUpdateSupported: Boolean = false,
    val metadataRefreshSupported: Boolean = false,
    val repositoryUrl: String = "",
    val zipUpload: Boolean = false,
    val permissions: List<String> = emptyList(),
    val setupFields: List<SetupField> = emptyList(),
)

/** One declared device state row (device_ui `fields` entry), resolved. */
data class DeviceField(
    val label: String,
    val text: String,
    /** Parsed #rrggbb colour when the row is a colour channel. */
    val color: String? = null,
)

/** One input inside a declared device control (device_ui `controls[].fields`). */
data class DeviceControlField(
    val name: String,
    val label: String,
    /** text | integer | number | boolean | select | color */
    val widget: String,
    val options: List<String>,
    val minimum: Double?,
    val maximum: Double?,
)

/** One skill-declared device control (a tool plus its fixed arguments). */
data class DeviceControl(
    val tool: String,
    val label: String,
    val baseArguments: JSONObject,
    val fields: List<DeviceControlField>,
)

/** One connected device skill from GET /api/devices. */
data class DeviceItem(
    val id: String,
    val label: String,
    val name: String,
    val icon: String,
    val connected: Boolean,
    val declared: Boolean,
    val status: String,
    val alias: String = "",
    val configured: Boolean = false,
    val trusted: Boolean = false,
    val fingerprint: String? = null,
    val runtimeError: String? = null,
    /** device_status.device_connected when the skill reported status. */
    val deviceConnected: Boolean? = null,
    val fields: List<DeviceField> = emptyList(),
    val controls: List<DeviceControl> = emptyList(),
)

/**
 * Single source of truth for the connector: connection config, per-screen
 * loaders, chat state and the connection test. Every network call runs on
 * [Dispatchers.IO] and every payload is treated as untrusted display data.
 */
class NixViewModel(application: Application) : AndroidViewModel(application) {

    val config = NixConfig(application)
    val api = NixApi(config)

    /**
     * Observable mirror of "a server URL is configured". [NixConfig.baseUrl]
     * lives in SharedPreferences, which Compose cannot observe — without this
     * the app shell would freeze on Settings after the first save.
     */
    var configured by mutableStateOf(config.baseUrl.isNotBlank())
    var screen by mutableStateOf(if (config.baseUrl.isBlank()) Screen.Settings else Screen.Home)
    val loading = mutableStateMapOf<String, Boolean>()
    val dataError = mutableStateMapOf<String, String>()

    // ---------------------------------------------------------------- home
    var profileName by mutableStateOf("")
    var healthChips by mutableStateOf<List<StatusChip>>(emptyList())
    var homeCopy by mutableStateOf<JSONObject?>(null)
    var homeCopySource by mutableStateOf("bundled")

    // Rich health values behind the Home stat tiles.
    var gpuName by mutableStateOf<String?>(null)
    /** Last raw /api/health payload — drives the Hardware monitor page. */
    var healthPayload by mutableStateOf<JSONObject?>(null)
        private set
    var knowledgeUp by mutableStateOf<Boolean?>(null)
    var actionsUp by mutableStateOf<Boolean?>(null)
    var actionsPending by mutableStateOf<Int?>(null)
    /** Rolling reply latencies (ms) for the Home response-time graph. */
    val latencyHistory = mutableStateListOf<Double>()

    // ----------------------------------------------------------- cold start
    /** True once [bootstrap] has fired — guards a double start. */
    private var bootstrapped = false

    /**
     * Session gate: false when a configured hub was unreachable at cold start.
     * The app then signs the user out (locks to the connection screen) but
     * keeps the cached connection so re-signing-in is just confirming the saved
     * address — nothing is cleared, so an offline hub never costs the user their
     * settings.
     */
    var sessionActive by mutableStateOf(true)

    /**
     * Kick off the cold-start probes (profile, health, model) in parallel so
     * Home's tiles fill in the moment they answer. Nothing here gates
     * rendering: there is no splash screen — the shell opens straight to its
     * content and these results simply land behind it.
     *
     * A configured-but-unreachable hub signs the user out first ([sessionActive]
     * false → the shell locks to the connection screen) and skips the probes,
     * since they would only fail too. The cached connection stays intact.
     */
    fun bootstrap() {
        if (bootstrapped || !configured) return
        bootstrapped = true
        viewModelScope.launch {
            // Session gate: one cheap reachability probe before anything else.
            val reachable = withContext(Dispatchers.IO) { api.reachable() }
            if (!reachable) {
                withContext(Dispatchers.Main) { sessionActive = false }
                return@launch
            }
            withContext(Dispatchers.Main) { sessionActive = true }
            if (mirrorRing) startMirrorPolling()
            val profileJob = launch(Dispatchers.IO) {
                val r = api.profile()
                if (r.isHttpOk) {
                    val name = Json.str(r.json(), "user_name")
                    withContext(Dispatchers.Main) {
                        profileName = name
                        seedProfiles(name)
                    }
                }
            }
            val healthJob = launch(Dispatchers.IO) {
                val r = api.health()
                if (r.isHttpOk) withContext(Dispatchers.Main) { applyHealth(r) }
            }
            val modelsJob = launch(Dispatchers.IO) {
                val r = api.models()
                if (r.isHttpOk) withContext(Dispatchers.Main) { applyModelStatus(r.json()) }
            }
            profileJob.join()
            healthJob.join()
            modelsJob.join()
            if (healthChips.isEmpty()) refreshHome()
        }
    }

    // ------------------------------------------------------------- profiles
    /** Ordered profile names; index 0 is the default that loads first. */
    var profiles by mutableStateOf<List<String>>(emptyList())
    var activeProfile by mutableStateOf("")

    private fun seedProfiles(serverName: String) {
        val stored = config.profiles
        profiles = if (stored.isNotEmpty()) stored else listOf(serverName.ifBlank { "NIX user" })
        activeProfile = config.activeProfile.ifBlank { profiles.first() }
        if (serverName.isNotBlank()) profileName = serverName else profileName = activeProfile
    }

    /** Switch the active profile and push the name to the NIX instance. */
    fun selectProfile(name: String) {
        if (name.isBlank()) return
        activeProfile = name
        config.activeProfile = name
        profileName = name
        if (profiles.isEmpty()) profiles = listOf(name)
        viewModelScope.launch {
            fetch("profile-save", { api.saveProfile(name) }) { r ->
                if (!r.isHttpOk) dataError["profile-save"] = r.hint()
            }
        }
    }

    fun addProfile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        profiles = (profiles + trimmed).distinct()
        config.profiles = profiles
        selectProfile(trimmed)
    }

    // ------------------------------------------------- appearance / theming
    var accentId by mutableStateOf(config.accentId)
    var mirrorRing by mutableStateOf(config.mirrorRingEffect)
    var darkTheme by mutableStateOf(config.darkTheme)

    fun setAccent(id: String) {
        accentId = id
        config.accentId = id
        com.nixpuca.home.ui.NixPalette.applyAccent(id)
    }

    fun setMirrorRingEnabled(enabled: Boolean) {
        mirrorRing = enabled
        config.mirrorRingEffect = enabled
        if (enabled) startMirrorPolling() else stopMirrorPolling()
    }

    private var mirrorPoll: kotlinx.coroutines.Job? = null

    /**
     * While the background mirror is on, keep re-reading the ring's reported
     * state so effects started elsewhere (voice, console, another app) show
     * up in the app instead of freezing on the last known state.
     */
    private fun startMirrorPolling() {
        if (mirrorPoll != null) return
        mirrorPoll = viewModelScope.launch {
            // Prime right away: on a cold start deviceItems is empty, so
            // waiting for a non-empty list meant the mirror never began
            // until the user happened to open the Devices tab.
            loadDevices()
            while (true) {
                kotlinx.coroutines.delay(10_000)
                loadDevices()
            }
        }
    }

    private fun stopMirrorPolling() {
        mirrorPoll?.cancel()
        mirrorPoll = null
    }

    fun setDarkThemeEnabled(enabled: Boolean) {
        darkTheme = enabled
        config.darkTheme = enabled
        com.nixpuca.home.ui.NixPalette.applyTheme(enabled)
    }

    // ------------------------------------------------------- first-run setup
    /** True until the guided welcome/setup flow has been finished or skipped. */
    var setupNeeded by mutableStateOf(!config.setupComplete)

    fun finishSetup() {
        config.setupComplete = true
        setupNeeded = false
    }

    /**
     * Re-run the cold-start session gate from the connection screen. Called
     * when a signed-out user (hub was unreachable) wants back in: re-probes
     * reachability and, on success, flips [sessionActive] so the shell
     * unlocks. The cached connection is reused unless they edited it first.
     */
    fun reconnect() {
        if (testBusy) return
        testBusy = true
        testLines = listOf(TestLine("Contacting ${serverUrl.ifBlank { "(no URL set)" }} …", null))
        viewModelScope.launch {
            // Persist any edited address so the probe uses what's on screen.
            config.baseUrl = serverUrl
            configured = config.baseUrl.isNotBlank()
            val reachable = withContext(Dispatchers.IO) { api.reachable() }
            withContext(Dispatchers.Main) {
                testBusy = false
                if (reachable) {
                    sessionActive = true
                    testLines = listOf(TestLine("Connected — signing you back in.", true))
                    // Re-kick the cold-start probes so Home fills in again.
                    bootstrapped = false
                    bootstrap()
                } else {
                    testLines = listOf(
                        TestLine("Still unreachable — check the address and your network.", false),
                    )
                }
            }
        }
    }

    // ----------------------------------------------------------- pairing
    /**
     * Observable copy of [NixConfig.pairedDevices] so the Devices page can
     * list the Nix Dev units this account owns and repaint the moment one is
     * paired or unpaired.
     */
    var pairedAddresses by mutableStateOf(config.pairedDevices.toList())
        private set

    /** True once this app has finished the full pairing flow for a unit. */
    fun isPaired(address: String): Boolean = config.pairedDevices.contains(address)

    /** Record a finished pairing so the device list and wizard agree. */
    fun markPaired(address: String) {
        config.pairedDevices = config.pairedDevices + address
        pairedAddresses = config.pairedDevices.toList()
    }

    /** Forget a unit (unpaired from the device, or released from this app). */
    fun unmarkPaired(address: String) {
        config.pairedDevices = config.pairedDevices - address
        pairedAddresses = config.pairedDevices.toList()
    }

    /**
     * This account's identity, written into the unit's pairing flag when it
     * is claimed (`paired <url>`). Compared case-insensitively, ignoring a
     * trailing slash, so settings edits don't orphan a paired unit.
     */
    fun hubIdentity(): String = config.baseUrl.trim().trimEnd('/')

    /**
     * True when the unit's recorded owner is this account. A legacy unit
     * (paired before owners were recorded) has no owner and can be claimed
     * by whoever pairs it next — blocking those would make an upgraded unit
     * unpairable forever.
     */
    fun ownsPairing(owner: String, address: String): Boolean {
        val recorded = owner.trim().trimEnd('/')
        if (recorded.isEmpty()) return true
        return recorded.equals(hubIdentity(), ignoreCase = true)
    }

    /**
     * Set by the Devices page's "Change Wi-Fi", consumed once by
     * PairDeviceScreen, which opens the wizard straight at that unit.
     */
    var requestedPairAddress by mutableStateOf<String?>(null)

    // ---------------------------------------------------------------- chat
    val messages = mutableStateListOf<ChatMessage>()
    var draft by mutableStateOf("")
    var chatBusy by mutableStateOf(false)
    private var messageCounter = 0L

    /**
     * Active assistant identity as the server reports it (Luna vs Casper).
     * The chat copy must follow this instead of assuming Casper — with the
     * official Luna model active every "Casper is thinking…" label was wrong.
     */
    var assistantName by mutableStateOf<String?>(null)
    var activeModelId by mutableStateOf<String?>(null)

    /** Display name used across the chat copy, derived when the server omits it. */
    val companionName: String
        get() {
            assistantName?.takeIf { it.isNotBlank() }?.let { return it }
            val id = activeModelId ?: return "NIX"
            return when {
                id.contains("luna", ignoreCase = true) -> "Luna"
                id.contains("casper", ignoreCase = true) -> "Casper"
                else -> "NIX"
            }
        }

    // ------------------------------------------------- assistant model list
    var modelChoices by mutableStateOf<List<ModelChoice>>(emptyList())
    var modelSelectable by mutableStateOf(false)
    var modelBusy by mutableStateOf(false)
    var modelNotice by mutableStateOf<String?>(null)

    // ------------------------------------------------------------ knowledge
    var memoryText by mutableStateOf<String?>(null)
    var kbCards by mutableStateOf<List<JsonCard>>(emptyList())
    var peopleCards by mutableStateOf<List<JsonCard>>(emptyList())
    var knowledgeTab by mutableStateOf(0)

    // ------------------------------------------------------------- schedule
    var scheduleUpcoming by mutableStateOf<List<JsonCard>>(emptyList())
    var schedulePast by mutableStateOf<List<JsonCard>>(emptyList())
    var scheduleActions by mutableStateOf<List<JsonCard>>(emptyList())
    /** Structured events for the calendar grid (start parsed to a day). */
    var calendarEvents by mutableStateOf<List<CalendarEvent>>(emptyList())
    /** Events imported from the connected Google Calendar secret address. */
    var googleEvents by mutableStateOf<List<CalendarEvent>>(emptyList())
    var gcalBusy by mutableStateOf(false)
    var gcalNotice by mutableStateOf<String?>(null)
    var gcalUrlDraft by mutableStateOf(config.googleCalendarUrl)
    var pushBusy by mutableStateOf(false)
    var pushNotice by mutableStateOf<String?>(null)
    /** Events read from the phone's own calendars (CalendarContract). */
    var localEvents by mutableStateOf<List<CalendarEvent>>(emptyList())
    var localCalBusy by mutableStateOf(false)
    var localCalNotice by mutableStateOf<String?>(null)
    /** True once the user granted READ_CALENDAR for the local import. */
    var localCalGranted by mutableStateOf(false)
    /** Manual "add event" dialog state. */
    var manualEventBusy by mutableStateOf(false)
    var manualEventNotice by mutableStateOf<String?>(null)
    /** True when [manualEventNotice] holds a failure (so the UI can show it in
     * the error color instead of mistaking it for a success). */
    var manualEventNoticeError by mutableStateOf(false)

    // -------------------------------------------------------------- chat list
    /** Past conversations from GET /api/sessions, newest first. */
    var conversations by mutableStateOf<List<ConversationItem>>(emptyList())
    var conversationsBusy by mutableStateOf(false)
    /** Conversation whose transcript is open in the Chat history panel. */
    var openConversationId by mutableStateOf<String?>(null)

    fun loadConversations() {
        if (conversationsBusy) return
        conversationsBusy = true
        viewModelScope.launch {
            fetch("sessions", api::sessions) { r ->
                if (r.isHttpOk) {
                    val list = r.json()?.optJSONArray("sessions")
                    conversations = if (list == null) emptyList() else (0 until list.length()).mapNotNull { i ->
                        val o = list.optJSONObject(i) ?: return@mapNotNull null
                        val id = o.optStr("id").ifBlank { o.optStr("session_tag") }
                        if (id.isBlank()) null else ConversationItem(
                            id = id,
                            conversationId = o.optStr("conversation_id").ifBlank { null },
                            title = o.optStr("title").ifBlank { "Untitled conversation" },
                            preview = o.optStr("preview"),
                            source = o.optStr("source").ifBlank { "Conversation" },
                            turnCount = o.optInt("turn_count"),
                            lastTurn = o.optStr("last_turn"),
                            turns = readTurns(o.optJSONArray("turns")),
                        )
                    }
                } else dataError["sessions"] = r.hint()
                conversationsBusy = false
            }
        }
    }

    private fun readTurns(arr: JSONArray?): List<ChatMessage> {
        if (arr == null) return emptyList()
        var localId = -1L
        return (0 until arr.length()).mapNotNull { i ->
            val t = arr.optJSONObject(i) ?: return@mapNotNull null
            val role = t.optStr("role")
            val content = t.optStr("content")
            if (content.isBlank()) null else ChatMessage(
                id = localId--,
                role = if (role == "user") "user" else "assistant",
                text = content,
            )
        }
    }

    /** Toggle the history panel for one past conversation (null clears it). */
    fun openConversation(id: String?) {
        openConversationId = if (id == null || openConversationId == id) null else id
    }

    /** Load a past conversation's turns into the live composer thread. */
    fun continueConversation(item: ConversationItem) {
        messages.clear()
        item.turns.forEach { turn -> messages.add(turn.copy(id = ++messageCounter)) }
        item.conversationId?.let { config.conversationId = it }
        openConversationId = null
        dataError.remove("chat")
    }

    // ---------------------------------------------------------------- system
    var modelRows by mutableStateOf<List<JsonCard>>(emptyList())
    var deviceRows by mutableStateOf<List<JsonCard>>(emptyList())
    var sessionRows by mutableStateOf<List<JsonCard>>(emptyList())
    var stageRows by mutableStateOf<List<StageRow>>(emptyList())
    var traceRows by mutableStateOf<List<JsonCard>>(emptyList())

    // -------------------------------------------------------------- settings
    var serverUrl by mutableStateOf(config.baseUrl)
    var location by mutableStateOf(config.location)
    var cfClientId by mutableStateOf(config.cfClientId)
    var cfClientSecret by mutableStateOf(config.cfClientSecret)
    var bearerToken by mutableStateOf(config.bearerToken)
    var testLines by mutableStateOf<List<TestLine>>(emptyList())
    var testBusy by mutableStateOf(false)
    var saved by mutableStateOf(false)

    /** Keys whose refresh arrived while a call for that key was in flight. */
    private val rerunKeys = mutableSetOf<String>()

    private suspend fun fetch(
        key: String,
        call: () -> ApiResult,
        handle: (ApiResult) -> Unit,
    ) {
        if (loading[key] == true) {
            // A refresh raced an already-running one. Dropping it left the
            // screen showing the previous payload — stale data reads as
            // lag — so queue it and run it again the moment this returns.
            rerunKeys.add(key)
            return
        }
        loading[key] = true
        try {
            do {
                rerunKeys.remove(key)
                dataError.remove(key)
                val result = withContext(Dispatchers.IO) { call() }
                handle(result)
            } while (rerunKeys.contains(key))
        } catch (t: Throwable) {
            dataError[key] = t.message ?: "Unexpected error"
        } finally {
            loading[key] = false
            rerunKeys.remove(key)
        }
    }

    /**
     * Same as [fetch], but on its own coroutine. Independent reads then
     * overlap and the screen waits for the slowest call instead of their
     * sum — Home used to serialise profile → health (60 s budget) → copy.
     */
    private fun fetchAsync(key: String, call: () -> ApiResult, handle: (ApiResult) -> Unit) {
        viewModelScope.launch { fetch(key, call, handle) }
    }

    // ------------------------------------------------------------------ home

    fun refreshHome() {
        fetchAsync("profile", api::profile) { r ->
            if (r.isHttpOk) profileName = Json.str(r.json(), "user_name")
        }
        fetchAsync("health", api::health, ::applyHealth)
        fetchAsync("home-copy", api::homeCopy) { r ->
            if (r.isHttpOk) {
                val payload = r.json()
                val bundle = payload?.optJSONObject("data")
                if (bundle != null) {
                    homeCopy = bundle
                    homeCopySource = "server"
                }
            }
            if (homeCopy == null) homeCopySource = "bundled"
        }
    }

    /** Re-read just the health payload (drives the Hardware monitor). */
    fun loadHealth() {
        viewModelScope.launch { fetch("health", api::health, ::applyHealth) }
    }

    private fun applyHealth(r: ApiResult) {
        val obj = r.json()
        if (!r.isHttpOk || obj == null) {
            dataError["health"] = r.hint()
            healthChips = emptyList()
            return
        }
        // Raw payload kept for the Hardware monitor, which reads whatever
        // gpu/cpu/memory blocks this console build reports.
        healthPayload = obj
        val chips = mutableListOf<StatusChip>()
        val knowledge = obj.optJSONObject("knowledge")
        chips.add(
            StatusChip(
                "Knowledge",
                when {
                    knowledge == null -> "unknown"
                    knowledge.optBoolean("ok", false) -> "up"
                    else -> "down"
                },
                if (knowledge == null) null else knowledge.optBoolean("ok", false),
            )
        )
        val actions = obj.optJSONObject("actions")
        val pending = actions?.optJSONObject("stats")?.opt("pending")
        chips.add(
            StatusChip(
                "Actions",
                when {
                    actions == null -> "unknown"
                    actions.optBoolean("ok", false) ->
                        if (pending != null) "up · $pending pending" else "up"
                    else -> "down"
                },
                if (actions == null) null else actions.optBoolean("ok", false),
            )
        )
        val assistant = obj.optJSONObject("assistant")
        // assistant_models is the pure selection status; health's "assistant"
        // block overrides `active` with the legacy ollama model name.
        val status = obj.optJSONObject("assistant_models") ?: assistant
        val active = status?.optStr("active")?.ifBlank { null }
            ?: assistant?.optStr("active")?.ifBlank { null }
            ?: obj.optJSONObject("casper")?.optStr("model")?.ifBlank { null }
        val name = status?.optStr("assistant_name")?.ifBlank { null }
            ?: assistant?.optStr("assistant_name")?.ifBlank { null }
        if (name != null) assistantName = name
        if (active != null) activeModelId = active
        val companion = when {
            name != null && active != null -> "$name · $active"
            name != null -> name
            else -> active
        }
        chips.add(
            StatusChip(
                "Companion",
                companion ?: "unreported",
                if (name == null && active == null) null else true,
            )
        )
        val gpu = obj.optJSONObject("gpu")
        val gpuName = gpu?.optStr("name")?.ifBlank { null }
        chips.add(
            StatusChip(
                "GPU",
                gpuName ?: "none reported",
                if (gpuName == null) null else true,
            )
        )
        val tz = obj.optStr("timezone").ifBlank { null }
        if (tz != null) chips.add(StatusChip("Timezone", tz, true))
        healthChips = chips
        applyHealthValues(obj)
    }

    /** Health payload also feeds the Home tiles with typed values. */
    private fun applyHealthValues(obj: JSONObject?) {
        if (obj == null) return
        knowledgeUp = obj.optJSONObject("knowledge")?.optBoolean("ok")
        val actions = obj.optJSONObject("actions")
        actionsUp = actions?.optBoolean("ok")
        actionsPending = actions?.optJSONObject("stats")?.opt("pending")
            ?.toString()?.toIntOrNull()
        gpuName = obj.optJSONObject("gpu")?.optStr("name")?.ifBlank { null }
    }

    /** Bundled copy used when the extension route is unavailable. */
    fun ensureHomeCopy() {
        if (homeCopy == null) {
            homeCopy = DEFAULT_HOME_COPY
            homeCopySource = "bundled"
        }
    }

    fun useSuggestion(prompt: String) {
        draft = prompt
        screen = Screen.Chat
    }

    // ------------------------------------------------------------------ chat

    fun sendDraft() {
        val text = draft.trim()
        if (text.isEmpty() || chatBusy) return
        draft = ""
        messages.add(ChatMessage(++messageCounter, "user", text))
        chatBusy = true
        dataError.remove("chat")
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                api.send(text, config.conversationId)
            }
            chatBusy = false
            if (!r.isHttpOk) {
                messages.add(ChatMessage(++messageCounter, "error", r.hint()))
                dataError["chat"] = r.hint()
                return@launch
            }
            val payload = r.json()
            if (payload == null || !payload.optBoolean("ok", false)) {
                val message = payload?.optStr("error")?.ifBlank { null }
                    ?: "The console answered without a reply payload."
                messages.add(ChatMessage(++messageCounter, "error", message))
                dataError["chat"] = message
                return@launch
            }
            val latency = payload.opt("latency_ms")?.toString()?.toDoubleOrNull()
            latency?.let {
                latencyHistory.add(it)
                while (latencyHistory.size > 40) latencyHistory.removeAt(0)
            }
            // `details` carries the identity of the model that answered, so a
            // Luna reply is labelled Luna even if health was never read.
            val details = payload.optJSONObject("details")
            val repliedAs = details?.optStr("assistant_name")?.ifBlank { null }
            val repliedModel = details?.optStr("official_model")?.ifBlank { null }
            if (repliedAs != null) assistantName = repliedAs
            if (repliedModel != null) activeModelId = repliedModel
            messages.add(
                ChatMessage(
                    id = ++messageCounter,
                    role = "assistant",
                    text = payload.optStr("reply"),
                    route = payload.optStr("route").ifBlank { null },
                    rule = payload.optStr("rule").ifBlank { null },
                    latencyMs = latency,
                    pipeline = readPipeline(payload.opt("pipeline")),
                    assistant = repliedAs,
                    model = repliedModel,
                )
            )
        }
    }

    fun newConversation() {
        messages.clear()
        dataError.remove("chat")
        config.resetConversation()
    }

    private fun readPipeline(value: Any?): List<Pair<String, String>> {
        val rows = JsonRender.extractList(value).filterIsInstance<JSONObject>()
        return rows.map { stage ->
            val label = stage.optStr("label").ifBlank { stage.optStr("id") }
            val status = stage.optStr("status").ifBlank { "ok" }
            val ms = stage.opt("ms")?.toString()?.toDoubleOrNull()
            label to if (ms != null) "$status · ${"%.0f".format(ms)} ms" else status
        }
    }

    /** GET /api/models — active assistant identity plus the selectable list. */
    fun loadModels() {
        viewModelScope.launch {
            fetch("models", api::models) { r ->
                if (r.isHttpOk) {
                    dataError.remove("models")
                    applyModelStatus(r.json())
                    modelRows = JsonRender.cards(r.json())
                } else {
                    dataError["models"] = r.hint()
                }
            }
        }
    }

    private fun applyModelStatus(obj: JSONObject?) {
        if (obj == null) return
        obj.optStr("assistant_name").ifBlank { null }?.let { assistantName = it }
        obj.optStr("active").ifBlank { null }?.let { activeModelId = it }
        modelSelectable = obj.optBoolean("selectable", false) &&
            obj.optStr("backend").equals("transformers", ignoreCase = true)
        val arr = obj.optJSONArray("models") ?: return
        val active = activeModelId
        modelChoices = (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = m.optStr("id")
            if (id.isBlank()) null else ModelChoice(
                id = id,
                name = m.optStr("name").ifBlank { id },
                label = m.optStr("label").ifBlank { m.optStr("name").ifBlank { id } },
                present = m.optBoolean("present", true),
                active = m.optBoolean("active", false) || id == active,
            )
        }
    }

    /** POST /api/model — switch the official model the pipeline answers with. */
    fun selectModel(modelId: String) {
        if (modelBusy || modelId.isBlank()) return
        modelBusy = true
        modelNotice = null
        dataError.remove("model-select")
        viewModelScope.launch {
            fetch("model-select", { api.selectModel(modelId) }) { r ->
                if (r.isHttpOk) {
                    val obj = r.json()
                    obj?.optStr("assistant_name")?.ifBlank { null }?.let { assistantName = it }
                    activeModelId = obj?.optStr("active")?.ifBlank { null } ?: modelId
                    modelNotice = "Now chatting with $companionName · $activeModelId"
                    loadModels()
                } else {
                    dataError["model-select"] = r.hint()
                }
            }
            modelBusy = false
        }
    }

    // ------------------------------------------------------------- knowledge

    fun loadKnowledge() {
        when (knowledgeTab) {
            0 -> loadMemory()
            1 -> loadKb()
            2 -> loadPeople()
        }
    }

    fun loadMemory() = viewModelScope.launch {
        fetch("memory", api::memory) { r ->
            if (r.isHttpOk) {
                val obj = r.json()
                memoryText = when {
                    obj == null -> r.body
                    else -> obj.opt("memory")?.let { JsonRender.pretty(it) }
                        ?: r.body
                }
            } else dataError["memory"] = r.hint()
        }
    }

    fun loadKb() = viewModelScope.launch {
        fetch("kb", api::kb) { r ->
            if (r.isHttpOk) kbCards = JsonRender.cards(r.json()?.opt("records") ?: r.json())
            else dataError["kb"] = r.hint()
        }
    }

    fun loadPeople() = viewModelScope.launch {
        fetch("people", api::people) { r ->
            if (r.isHttpOk) peopleCards = JsonRender.cards(r.json()?.opt("people") ?: r.json())
            else dataError["people"] = r.hint()
        }
    }

    // -------------------------------------------------------------- schedule

    fun loadSchedule() = viewModelScope.launch {
        fetch("schedule", api::schedule) { r ->
            if (r.isHttpOk) {
                val obj = r.json()
                scheduleUpcoming = JsonRender.cards(obj?.opt("upcoming"))
                schedulePast = JsonRender.cards(obj?.opt("past"))
                scheduleActions = JsonRender.cards(obj?.opt("standalone_actions"))
                calendarEvents = buildCalendarEvents(obj)
            } else dataError["schedule"] = r.hint()
        }
    }

    /** Flatten the schedule payload into dated rows for the calendar grid. */
    private fun buildCalendarEvents(obj: JSONObject?): List<CalendarEvent> {
        if (obj == null) return emptyList()
        val rows = mutableListOf<CalendarEvent>()
        for (key in listOf("upcoming", "past")) {
            val arr = obj.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val start = o.optStr("start")
                rows.add(
                    CalendarEvent(
                        id = "nix-${o.optStr("id").ifBlank { "$key-$i" }}",
                        title = o.optStr("title").ifBlank { "(untitled)" },
                        start = start,
                        end = o.optStr("end").ifBlank { null },
                        dayMillis = isoDayMillis(start),
                        allDay = o.optBoolean("all_day"),
                        source = "nix",
                        actions = JsonRender.extractList(o.opt("actions")).size,
                    ),
                )
            }
        }
        return rows.sortedBy { if (it.dayMillis == 0L) Long.MAX_VALUE else it.dayMillis }
    }

    /** Parse the leading date of an ISO-8601 string to local midnight millis. */
    private fun isoDayMillis(iso: String): Long = try {
        val cleaned = iso.trim()
        if (cleaned.length < 10) 0L else {
            val parts = cleaned.substring(0, 10).split('-')
            if (parts.size != 3) 0L else {
                val cal = java.util.Calendar.getInstance().apply {
                    clear()
                    set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                }
                cal.timeInMillis
            }
        }
    } catch (t: Throwable) {
        0L
    }

    /**
     * Save the Google Calendar secret iCal address and import its events.
     * The feed is fetched directly from Google; NIX never sees the URL.
     */
    fun connectGoogleCalendar(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || gcalBusy) return
        gcalBusy = true
        gcalNotice = null
        dataError.remove("gcal")
        config.googleCalendarUrl = trimmed
        gcalUrlDraft = trimmed
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    val conn = java.net.URL(trimmed).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 20_000
                    conn.setRequestProperty("User-Agent", "NixHome/1.0 (Android)")
                    val code = conn.responseCode
                    val body = if (code in 200..299) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else ""
                    conn.disconnect()
                    if (body.isBlank()) ApiResult(code, "", "Google Calendar returned HTTP $code.")
                    else ApiResult(code, body)
                } catch (t: Throwable) {
                    ApiResult(-1, "", t.message ?: "Could not reach Google Calendar.")
                }
            }
            if (!r.isHttpOk) {
                dataError["gcal"] = r.hint()
            } else {
                val parsed = parseICal(r.body)
                googleEvents = parsed
                gcalNotice = if (parsed.isEmpty()) {
                    "Connected, but no dated events were found in the feed."
                } else {
                    "Imported ${parsed.size} event${if (parsed.size == 1) "" else "s"} from Google Calendar."
                }
            }
            gcalBusy = false
        }
    }

    fun disconnectGoogleCalendar() {
        googleEvents = emptyList()
        config.googleCalendarUrl = ""
        gcalUrlDraft = ""
        gcalNotice = "Google Calendar disconnected."
    }

    /**
     * Read the phone's own calendars (CalendarContract) for the next 60
     * days. Requires READ_CALENDAR, requested by the caller first.
     */
    fun loadLocalCalendar() {
        if (localCalBusy) return
        val app = getApplication<android.app.Application>()
        val granted = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            androidx.core.content.ContextCompat.checkSelfPermission(
                app,
                android.Manifest.permission.READ_CALENDAR,
            )
        localCalGranted = granted
        if (!granted) {
            localCalNotice = "Calendar permission is needed to read your phone's calendars."
            return
        }
        localCalBusy = true
        localCalNotice = null
        viewModelScope.launch {
            val events = withContext(Dispatchers.IO) {
                try {
                    val start = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -7) }.timeInMillis
                    val end = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 60) }.timeInMillis
                    val projection = arrayOf(
                        android.provider.CalendarContract.Instances.EVENT_ID,
                        android.provider.CalendarContract.Instances.TITLE,
                        android.provider.CalendarContract.Instances.BEGIN,
                        android.provider.CalendarContract.Instances.END,
                        android.provider.CalendarContract.Instances.ALL_DAY,
                        android.provider.CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                    )
                    val cursor = android.provider.CalendarContract.Instances.query(
                        app.contentResolver,
                        projection,
                        start,
                        end,
                    )
                    val out = mutableListOf<CalendarEvent>()
                    cursor.use { c ->
                        val idIx = c.getColumnIndex(android.provider.CalendarContract.Instances.EVENT_ID)
                        val titleIx = c.getColumnIndex(android.provider.CalendarContract.Instances.TITLE)
                        val beginIx = c.getColumnIndex(android.provider.CalendarContract.Instances.BEGIN)
                        val endIx = c.getColumnIndex(android.provider.CalendarContract.Instances.END)
                        val allDayIx = c.getColumnIndex(android.provider.CalendarContract.Instances.ALL_DAY)
                        val calIx = c.getColumnIndex(android.provider.CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                        while (c.moveToNext()) {
                            val begin = c.getLong(beginIx)
                            val title = c.getString(titleIx)?.ifBlank { "(untitled)" } ?: "(untitled)"
                            val calendarName = if (calIx >= 0) c.getString(calIx).orEmpty() else ""
                            out += CalendarEvent(
                                id = "local-${c.getLong(idIx)}",
                                title = title,
                                start = java.time.Instant.ofEpochMilli(begin)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                end = null,
                                dayMillis = java.time.Instant.ofEpochMilli(begin)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                                    .let { date ->
                                        java.time.LocalDateTime.of(date, java.time.LocalTime.MIN)
                                            .atZone(java.time.ZoneId.systemDefault())
                                            .toInstant().toEpochMilli()
                                    },
                                allDay = allDayIx >= 0 && c.getInt(allDayIx) == 1,
                                source = if (calendarName.isBlank()) "local" else "local · $calendarName",
                                actions = 0,
                            )
                        }
                    }
                    out
                } catch (t: Throwable) {
                    emptyList()
                }
            }
            localEvents = events
            localCalNotice = if (events.isEmpty()) {
                "No events found in your phone's calendars for the next 60 days."
            } else {
                "Found ${events.size} event${if (events.size == 1) "" else "s"} in your calendars."
            }
            localCalBusy = false
        }
    }

    /**
     * Create a calendar event by talking to NIX the same way the user would.
     * There is no direct event-create endpoint, so this rides the brain's
     * add-event path through POST /api/send.
     */
    fun addManualEvent(title: String, dateText: String, timeText: String) {
        val cleanTitle = title.trim()
        val cleanDate = dateText.trim()
        if (cleanTitle.isEmpty() || cleanDate.isEmpty() || manualEventBusy) return
        manualEventBusy = true
        manualEventNotice = null
        manualEventNoticeError = false
        val whenText = if (timeText.isBlank()) cleanDate else "$cleanDate at ${timeText.trim()}"
        val message = "Create a calendar event titled \"$cleanTitle\" on $whenText."
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { api.send(message, "manual-event") }
            val ok = r.isHttpOk && r.json()?.optBoolean("ok", false) == true
            manualEventNotice = if (ok) {
                "\"$cleanTitle\" added to your schedule."
            } else {
                r.hint()
            }
            manualEventNoticeError = !ok
            manualEventBusy = false
            loadSchedule()
        }
    }

    /** Editing/deleting an event rides the same natural-language path. */
    var eventEditBusy by mutableStateOf(false)
    var eventEditNotice by mutableStateOf<String?>(null)

    /**
     * Update one of the user's events. The console exposes no direct
     * event-update route, so this asks Core the same way [addManualEvent]
     * creates events — while the calendar repaints optimistically so the
     * tap feels instant instead of waiting out the pipeline.
     */
    fun updateEvent(
        oldTitle: String,
        oldDate: String,
        newTitle: String,
        newDate: String,
        newTime: String,
    ) {
        val title = newTitle.trim()
        val date = newDate.trim()
        val time = newTime.trim()
        if (title.isEmpty() || date.isEmpty() || eventEditBusy) return
        eventEditBusy = true
        eventEditNotice = null
        // Optimistic: move the event on the grid before the round trip.
        val newStart = if (time.isEmpty()) date else "$date".plus("T").plus(time.take(5))
        calendarEvents = calendarEvents.map { event ->
            if (event.title == oldTitle && event.source == "nix") {
                val millis = isoDayMillis(newStart)
                event.copy(
                    title = title,
                    // A cleared time means a date-only event; don't carry
                    // the old clock time over into the new start.
                    start = if (newStart.length >= 10) newStart else event.start,
                    dayMillis = if (millis != 0L) millis else event.dayMillis,
                )
            } else {
                event
            }
        }
        val message = buildString {
            append("Update my calendar event titled \"$oldTitle\"")
            if (oldDate.isNotBlank()) append(" on $oldDate")
            append(": rename it to \"$title\" and move it to $date")
            if (time.isNotBlank()) append(" at $time")
            append(".")
        }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { api.send(message, "event-edit") }
            eventEditNotice = if (r.isHttpOk && r.json()?.optBoolean("ok", false) == true) {
                "\"$title\" updated."
            } else {
                r.hint()
            }
            eventEditBusy = false
            loadSchedule()
        }
    }

    /** Delete one of the user's events through the same spoken-word path. */
    fun deleteEvent(title: String, date: String) {
        if (eventEditBusy || title.isBlank()) return
        eventEditBusy = true
        eventEditNotice = null
        calendarEvents = calendarEvents.filterNot { it.title == title && it.source == "nix" }
        viewModelScope.launch {
            val whenText = if (date.isBlank()) "" else " on ${date.trim()}"
            val r = withContext(Dispatchers.IO) {
                api.send("Delete my calendar event titled \"$title\"$whenText.", "event-delete")
            }
            eventEditNotice = if (r.isHttpOk && r.json()?.optBoolean("ok", false) == true) {
                "\"$title\" removed from your schedule."
            } else {
                r.hint()
            }
            eventEditBusy = false
            loadSchedule()
        }
    }

    /** Minimal VEVENT reader for Google's secret iCal feeds. */
    private fun parseICal(text: String): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        var uid = ""
        var summary = ""
        var start = ""
        var end = ""
        var allDay = false
        var inEvent = false
        // Unfold continuation lines (RFC 5545 lines beginning with space/tab).
        val lines = text.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "").split("\n")
        for (raw in lines) {
            val line = raw.trim()
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true; uid = ""; summary = ""; start = ""; end = ""; allDay = false
                }
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    if (inEvent && start.isNotBlank()) {
                        events.add(
                            CalendarEvent(
                                id = "gcal-${uid.ifBlank { summary + start }}",
                                title = summary.ifBlank { "(untitled)" },
                                start = start,
                                end = end.ifBlank { null },
                                dayMillis = isoDayMillis(start),
                                allDay = allDay,
                                source = "google",
                            ),
                        )
                    }
                    inEvent = false
                }
                inEvent && line.startsWith("UID:") -> uid = line.substring(4)
                inEvent && line.startsWith("SUMMARY:") -> summary = line.substring(8)
                inEvent && line.startsWith("DTSTART") -> {
                    val value = line.substringAfter(':', "")
                    allDay = line.contains("VALUE=DATE") && !line.contains("T")
                    start = normalizeIcalDate(value, allDay)
                }
                inEvent && line.startsWith("DTEND") -> {
                    val value = line.substringAfter(':', "")
                    end = normalizeIcalDate(value, endIsDate = line.contains("VALUE=DATE"))
                }
            }
        }
        return events.sortedBy { if (it.dayMillis == 0L) Long.MAX_VALUE else it.dayMillis }
    }

    private fun normalizeIcalDate(value: String, endIsDate: Boolean): String {
        val v = value.trim()
        if (v.length >= 10 && v[8] == 'T') {
            // 20261005T143000Z → 2026-10-05T14:30
            return "${v.substring(0, 4)}-${v.substring(4, 6)}-${v.substring(6, 8)}T${v.substring(9, 11)}:${v.substring(11, 13)}"
        }
        if (v.length == 8) return "${v.substring(0, 4)}-${v.substring(4, 6)}-${v.substring(6, 8)}"
        return v
    }

    /**
     * Push imported Google events into NIX's own schedule by asking Core to
     * create them — the same natural-language path the dashboard uses.
     */
    fun pushGoogleEventsToNix() {
        if (pushBusy || googleEvents.isEmpty()) return
        val pending = googleEvents.filter { it.dayMillis != 0L }.take(20)
        if (pending.isEmpty()) {
            pushNotice = "No dated Google events to push."
            return
        }
        pushBusy = true
        pushNotice = null
        viewModelScope.launch {
            var okCount = 0
            for (event in pending) {
                val whenText = buildString {
                    append(event.start.take(10))
                    if (!event.allDay && event.start.length >= 16) {
                        append(" at ")
                        append(event.start.substring(11, 16))
                    }
                }
                val message = "Create a calendar event titled \"${event.title}\" on $whenText."
                val r = withContext(Dispatchers.IO) { api.send(message, "gcal-import") }
                if (r.isHttpOk && r.json()?.optBoolean("ok", false) == true) okCount++
            }
            pushNotice = "Pushed $okCount of ${pending.size} events into NIX's schedule."
            pushBusy = false
            loadSchedule()
        }
    }

    // ---------------------------------------------------------------- system

    fun loadSystem() {
        viewModelScope.launch {
            // Every one of these reads is independent: fan them out so the
            // diagnostics page fills in as fast as the network allows.
            fetchAsync("health", api::health, ::applyHealth)
            fetchAsync("models", api::models) { r ->
                modelRows = if (r.isHttpOk) JsonRender.cards(r.json()) else emptyList()
                if (!r.isHttpOk) dataError["models"] = r.hint()
            }
            fetchAsync("devices", api::devices) { r ->
                deviceRows = if (r.isHttpOk) JsonRender.cards(r.json()?.opt("devices") ?: r.json()) else emptyList()
                if (!r.isHttpOk) dataError["devices"] = r.hint()
            }
            fetchAsync("sessions", api::sessions) { r ->
                sessionRows = if (r.isHttpOk) JsonRender.cards(r.json()?.opt("sessions") ?: r.json()) else emptyList()
                if (!r.isHttpOk) dataError["sessions"] = r.hint()
            }
            fetchAsync("pipeline", api::pipeline) { r ->
                stageRows = if (r.isHttpOk) {
                    JsonRender.extractList(r.json()?.opt("stages"))
                        .filterIsInstance<JSONObject>()
                        .map { stage ->
                            StageRow(
                                label = stage.optStr("label").ifBlank { stage.optStr("id") },
                                status = stage.optStr("status").ifBlank { "—" },
                                ms = stage.opt("ms")?.toString()?.toDoubleOrNull(),
                            )
                        }
                } else emptyList()
                if (!r.isHttpOk) dataError["pipeline"] = r.hint()
            }
            fetchAsync("feed", api::feed) { r ->
                traceRows = if (r.isHttpOk) {
                    JsonRender.cards(r.json()?.opt("trace")).take(20)
                } else emptyList()
                if (!r.isHttpOk) dataError["feed"] = r.hint()
            }
        }
    }

    // ---------------------------------------------------------------- skills
    var skillItems by mutableStateOf<List<SkillItem>>(emptyList())
    var skillRepos by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var skillNotice by mutableStateOf<String?>(null)
    var updateNotice by mutableStateOf<String?>(null)
    /** Skill id currently busy with an install/tune request, if any. */
    var skillBusyId by mutableStateOf<String?>(null)

    // --------------------------------------------------------------- devices
    var deviceItems by mutableStateOf<List<DeviceItem>>(emptyList())
    /** Device whose own page is open on the Devices tab (null = grid). */
    var openDeviceId by mutableStateOf<String?>(null)

    /** Live ring-light effect, mirrored into the background when enabled. */
    data class RingState(val on: Boolean, val rgb: List<Int>, val effect: String, val brightness: Float = 1f)

    var ringState by mutableStateOf<RingState?>(null)
        private set

    /** Effect names the connected ring light advertises (empty when absent). */
    var ringEffects by mutableStateOf<List<String>>(emptyList())
        private set

    /**
     * Effect the user just picked, held until the device reports it back.
     * A rejected direct control falls back to the brain's pipeline (seconds)
     * and the device only confirms once it actually switches, so without this
     * the chip selection, the Echo Dot visual and the mirror background all
     * snap back to the old effect on the next status refresh and the tap
     * looks like it did nothing.
     */
    private var pendingRingEffect: String? = null
    private var pendingRingEffectAt = 0L

    /** How long an unacknowledged effect selection is trusted for. */
    private val PENDING_EFFECT_WINDOW_MS = 20_000L

    /** Pull rgb/on/effect (plus the advertised effect list) from the device. */
    private fun updateRingState() {
        val device = ringDevice() ?: deviceItems.firstOrNull { it.connected } ?: deviceItems.firstOrNull()
        val root = device?.let {
            try {
                JSONObject(it.status)
            } catch (t: Throwable) {
                null
            }
        }
        ringEffects = JsonRender.extractList(root?.opt("available_effects")).map { it.toString() }
        val reported = root?.optJSONObject("state")
        ringState = if (reported == null) {
            // No reported state, but we may have just commanded the light:
            // keep the optimistic selection instead of blanking the visual.
            val pending = pendingRingEffect
            if (pending != null &&
                System.currentTimeMillis() - pendingRingEffectAt < PENDING_EFFECT_WINDOW_MS
            ) {
                (ringState?.copy(effect = pending) ?: RingState(true, listOf(255, 255, 255), pending))
            } else {
                null
            }
        } else {
            val rgb = reported.optJSONArray("rgb")
            val reportedEffect = reported.optStr("effect")
            val pending = pendingRingEffect
            val effect = when {
                // Keep showing the tapped effect until the device confirms it
                // (or the window lapses) instead of flashing back to the old one.
                pending != null &&
                    System.currentTimeMillis() - pendingRingEffectAt < PENDING_EFFECT_WINDOW_MS &&
                    !reportedEffect.equals(pending, ignoreCase = true) -> pending
                pending != null && reportedEffect.equals(pending, ignoreCase = true) -> {
                    pendingRingEffect = null
                    reportedEffect
                }
                pending != null &&
                    System.currentTimeMillis() - pendingRingEffectAt >= PENDING_EFFECT_WINDOW_MS -> {
                    pendingRingEffect = null
                    reportedEffect
                }
                else -> reportedEffect
            }
            RingState(
                on = reported.opt("on") == true,
                rgb = listOf(
                    rgb?.optInt(0) ?: 255,
                    rgb?.optInt(1) ?: 255,
                    rgb?.optInt(2) ?: 255,
                ),
                effect = effect,
                brightness = reported.optDouble("brightness", 1.0).toFloat().coerceIn(0f, 1f),
            )
        }
    }

    /** First device that exposes the control_ring tool (the ring light). */
    private fun ringDevice(): DeviceItem? =
        deviceItems.firstOrNull { d -> d.controls.any { it.tool == "control_ring" } }
            ?: deviceItems.firstOrNull { it.id.contains("ring", ignoreCase = true) }

    /** Fire one control_ring action: on / off / color / brightness / effect. */
    fun ringCommand(action: String, extra: JSONObject = JSONObject(), notice: String) {
        val device = ringDevice() ?: return
        // The skill manifest declares UI controls for on/off/colour/brightness
        // and (on current consoles) effects. Effects try that same direct path
        // colours use first; a console whose manifest still omits the effect
        // field rejects the shape (HTTP 400), and that rejection falls back to
        // the companion's own pipeline, where the brain grounds exact names
        // from the device's advertised catalog and confirms the reported state.
        if (action == "effect") {
            val name = extra.optString("effect")
            if (name.isBlank()) return
            // Mirror locally so the app reacts before the round-trip.
            pendingRingEffect = name
            pendingRingEffectAt = System.currentTimeMillis()
            ringState = (ringState ?: RingState(on = true, rgb = listOf(255, 255, 255), effect = ""))
                .copy(effect = name)
            skillNotice = "$notice ✓"

            // Last resort: the console refused the direct control, so ground
            // the exact name through the brain like before — slower (a full
            // chat round trip), but the only path an older manifest has.
            fun brainFallback() {
                dataError.remove("skill-action")
                skillNotice = "$notice ✓"
                viewModelScope.launch {
                    // Refresh immediately: whatever the server already knows
                    // about the device shows up right away instead of after.
                    loadDevices()
                    val sent = withContext(Dispatchers.IO) {
                        api.send("Set the ring light to the $name effect right now.", "ring-effect")
                    }
                    if (!sent.isHttpOk) {
                        // Don't keep a ✓ on screen for an effect that never left.
                        dataError["skill-action"] = sent.hint()
                        pendingRingEffect = null
                    }
                    // Pull the fresh reported state once the device switched.
                    kotlinx.coroutines.delay(1_200)
                    loadDevices()
                }
            }

            // Try the same direct control path the colour chips use: one HTTP
            // hop to the skill, no brain in the middle. Effects used to ride
            // the chat pipeline end-to-end, which made a colour → effect switch
            // take seconds while a colour switch was instant.
            skillAction(
                device.id,
                "device_control",
                JSONObject()
                    .put("tool", "control_ring")
                    .put("arguments", JSONObject().put("action", "effect").put("effect", name)),
                notice,
                // Effects must never queue behind an install, like colours.
                force = true,
            ) { ok ->
                if (ok) {
                    // The skill accepted it; re-read after a beat so the pending
                    // selection clears as soon as the device pushes its new
                    // state back.
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(1_200)
                        loadDevices()
                    }
                } else {
                    brainFallback()
                }
            }
            return
        }
        val arguments = JSONObject().put("action", action)
        val keys = extra.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            arguments.put(key, extra.opt(key))
        }
        skillAction(
            device.id,
            "device_control",
            JSONObject().put("tool", "control_ring").put("arguments", arguments),
            notice,
            // Ring taps always go through; a busy install must not swallow them.
            force = true,
        )
        if (action == "brightness") {
            val level = extra.optDouble("brightness", Double.NaN)
            if (!level.isNaN()) {
                ringState?.let { ringState = it.copy(brightness = level.toFloat().coerceIn(0f, 1f)) }
            }
        }
        if (action == "color") {
            val rgb = extra.optJSONArray("rgb")
            if (rgb != null && rgb.length() == 3) {
                ringState?.let {
                    ringState = it.copy(
                        rgb = listOf(rgb.optInt(0), rgb.optInt(1), rgb.optInt(2)),
                    )
                }
            }
        }
        if (action == "on" || action == "off") {
            ringState?.let { ringState = it.copy(on = action == "on") }
        }
    }

    /** Read GET /api/skills (or the ?refresh=1 update check) into state. */
    fun loadSkills(refresh: Boolean = false) {
        val key = if (refresh) "skills-refresh" else "skills"
        viewModelScope.launch {
            fetch(key, { if (refresh) api.skillsRefresh() else api.skills() }) { r ->
                if (r.isHttpOk) {
                    applySkills(r.json())
                    if (refresh) {
                        val check = r.json()?.optJSONObject("auto_update_check")
                        updateNotice = when (check?.optBoolean("checked")) {
                            true -> {
                                val updated = JsonRender.extractList(check.opt("updated"))
                                if (updated.isEmpty()) {
                                    "All installed skills are up to date."
                                } else {
                                    "Updated: ${updated.joinToString { it.toString() }}"
                                }
                            }
                            else -> "Update check unavailable on this console."
                        }
                    }
                } else dataError["skills"] = r.hint()
            }
        }
    }

    private fun applySkills(root: JSONObject?) {
        if (root == null) return
        root.optJSONArray("skills")?.let { arr ->
            val items = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optStr("id")
                if (id.isBlank()) return@mapNotNull null
                SkillItem(
                    id = id,
                    name = o.optStr("name").ifBlank { id },
                    version = o.optStr("version").ifBlank { "—" },
                    category = o.optStr("category").ifBlank { "—" },
                    description = o.optStr("description"),
                    installed = o.optBoolean("installed"),
                    community = o.optBoolean("community"),
                    runnable = o.optBoolean("runnable"),
                    implementationStatus = o.optStr("implementation_status").ifBlank { "—" },
                    configuration = o.optJSONObject("configuration")?.toString() ?: "{}",
                    deviceName = o.optStr("device_name"),
                    fingerprint = o.optStr("fingerprint").ifBlank { null },
                    trusted = o.optBoolean("trusted"),
                    configured = o.optBoolean("configured"),
                    workerRunning = o.optBoolean("worker_running"),
                    runtimeDeclared = o.optBoolean("runtime_declared"),
                    runtimeError = o.optStr("runtime_error").ifBlank { null },
                    autoUpdate = o.optBoolean("auto_update"),
                    autoUpdateSupported = o.optBoolean("auto_update_supported"),
                    metadataRefreshSupported = o.optBoolean("metadata_refresh_supported"),
                    repositoryUrl = o.optStr("repository_url"),
                    zipUpload = o.optBoolean("zip_upload"),
                    permissions = JsonRender.extractList(o.opt("permissions")).map { it.toString() },
                    setupFields = parseSetupFields(o.optJSONArray("setup_fields")),
                )
            }
            skillItems = items
        }
        root.optJSONArray("repositories")?.let { arr ->
            skillRepos = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val count = o.optInt("skill_count")
                val name = o.optStr("name").ifBlank { "Repository" }
                name to "${o.optStr("url").ifBlank { "—" }} · $count skills"
            }
        }
    }

    /** Manifest setup fields become typed inputs instead of raw JSON. */
    private fun parseSetupFields(arr: JSONArray?): List<SetupField> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optStr("id")
            if (id.isBlank()) null else SetupField(
                id = id,
                label = o.optStr("label").ifBlank { id },
                help = o.optStr("help"),
                required = o.optBoolean("required"),
                secret = o.optStr("type") == "secret",
            )
        }
    }

    /**
     * POST /api/skills with one marketplace/runtime action
     * (install, uninstall, auto_update, configure, device_name, …).
     */
    fun skillAction(
        skillId: String,
        action: String,
        extra: JSONObject = JSONObject(),
        notice: String,
        force: Boolean = false,
        onResult: ((Boolean) -> Unit)? = null,
    ) {
        if (skillId.isBlank()) {
            onResult?.invoke(false)
            return
        }
        runSkillAction(
            action,
            JSONObject().put("skill_id", skillId),
            extra,
            notice,
            busyKey = skillId,
            force = force,
            onResult = onResult,
        )
    }

    /**
     * [skillAction] awaited: the pairing wizard runs install → configure →
     * trust → connect as one chain and each step must land before the next
     * one starts, so it needs the real outcome rather than a notice.
     */
    suspend fun skillActionAwait(
        skillId: String,
        action: String,
        extra: JSONObject = JSONObject(),
        notice: String,
        force: Boolean = true,
    ): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        skillAction(skillId, action, extra, notice, force) { ok ->
            if (cont.isActive) cont.resume(ok)
        }
    }

    /**
     * One direct read of GET /api/skills that both applies the payload to
     * state and hands the row back: the pairing chain decides what is
     * already done (installed? trusted? running?) from this instead of
     * assuming, which also makes a retry after a mid-flow failure safe.
     */
    suspend fun readSkill(id: String): SkillItem? {
        val r = try {
            withContext(Dispatchers.IO) { api.skills() }
        } catch (t: Throwable) {
            dataError["skills"] = "read threw: ${t.message ?: t.javaClass.simpleName}"
            return null
        }
        if (r.isHttpOk) {
            dataError.remove("skills")
            applySkills(r.json())
            // Community installs land under a namespaced id
            // (github:owner/repo:skill), so "echo-connect" must also
            // resolve the full catalog id actions are sent with.
            val match = skillItems.firstOrNull { it.id == id }
                ?: skillItems.firstOrNull { it.id.endsWith(":$id") }
            if (match == null) {
                dataError["skills"] =
                    "skill '$id' not in catalog: ids=${skillItems.map { it.id }} " +
                    "(code ${r.code}, body ${r.body.length} chars, json ${r.json() != null})"
            }
            return match
        } else {
            dataError["skills"] =
                "read failed: code ${r.code} transport=${r.transportError ?: "-"} " +
                "body=${r.body.take(120)}"
            return null
        }
    }

    private fun runSkillAction(
        action: String,
        payload: JSONObject,
        extra: JSONObject,
        notice: String,
        onResult: ((Boolean) -> Unit)? = null,
        busyKey: String = MARKET_BUSY_ID,
        /**
         * When true the action is sent even if another skill call is in
         * flight. Device controls (ring on/off/colour) must never be dropped
         * because an install happened to be running — a silently discarded
         * click reads as "the app is laggy".
         */
        force: Boolean = false,
    ) {
        if (skillBusyId != null && !force) {
            // Never drop a tap silently — tell the user why nothing moved.
            skillNotice = "Another skill is still busy — try that again in a second."
            onResult?.invoke(false)
            return
        }
        if (skillBusyId == null) skillBusyId = busyKey
        skillNotice = null
        dataError.remove("skill-action")
        payload.put("action", action)
        val keys = extra.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            payload.put(key, extra.get(key))
        }
        viewModelScope.launch {
            var ok = false
            // One distinct in-flight key per call: sharing a key lets the
            // loader dedupe — good for reads, but it is exactly how a rapid
            // second tap on a skill action used to vanish.
            val fetchKey = "skill-action-$busyKey-${System.nanoTime()}"
            fetch(fetchKey, { api.skillAction(payload) }) { r ->
                ok = r.isHttpOk
                if (ok) {
                    skillNotice = "$notice ✓"
                    applySkills(r.json())
                } else {
                    dataError["skill-action"] = r.hint()
                }
            }
            if (skillBusyId == busyKey) skillBusyId = null
            // The marketplace and runtime payloads differ per action; re-read
            // so the cards always reflect the server's persisted state.
            // The skills payload carries no device runtime state, so the
            // Devices card also has to be re-read or it keeps showing the
            // pre-action connection status.
            if (ok) {
                // Mirror the accepted action locally right now — the re-reads
                // below cost a round trip, and a card that keeps showing the
                // pre-action state reads as "the skill disconnected again".
                applySkillActionLocally(action, busyKey, extra)
                // A device-only tap needs the device read, not the whole
                // marketplace catalog — skip the extra round trip.
                if (action != "device_control" && action != "device_status") loadSkills()
                loadDevices()
                if (action == "connect" || action == "device_connect") {
                    confirmRuntime(busyKey, expectRunning = true)
                } else if (action == "disconnect") {
                    confirmRuntime(busyKey, expectRunning = false)
                }
            }
            onResult?.invoke(ok)
        }
    }

    /** Patch one skill's local state without waiting for a round trip. */
    private fun patchSkill(id: String, transform: (SkillItem) -> SkillItem) {
        val items = skillItems
        if (items.none { it.id == id }) return
        skillItems = items.map { if (it.id == id) transform(it) else it }
    }

    /**
     * Optimistic local result of an accepted skill action: the card flips
     * the instant the server says yes, and the confirming re-read keeps it
     * in step with whatever the host actually persisted.
     */
    private fun applySkillActionLocally(action: String, id: String, extra: JSONObject) {
        when (action) {
            "connect" -> patchSkill(id) { it.copy(workerRunning = true, runtimeError = null) }
            "disconnect" -> patchSkill(id) { it.copy(workerRunning = false) }
            "install" -> patchSkill(id) { it.copy(installed = true) }
            "uninstall" -> patchSkill(id) { it.copy(installed = false, workerRunning = false) }
            "trust" -> patchSkill(id) { it.copy(trusted = true) }
            "untrust" -> patchSkill(id) { it.copy(trusted = false, workerRunning = false) }
            "auto_update" -> patchSkill(id) {
                it.copy(autoUpdate = extra.optBoolean("enabled", !it.autoUpdate))
            }
            "device_name" -> {
                val renamed = extra.optString("device_name").trim()
                if (renamed.isNotBlank()) {
                    deviceItems = deviceItems.map {
                        if (it.id == id) it.copy(alias = renamed, label = renamed) else it
                    }
                }
            }
        }
        val wantConnected = when (action) {
            "connect", "device_connect" -> true
            "disconnect" -> false
            else -> null
        }
        if (wantConnected != null) {
            deviceItems = deviceItems.map {
                if (it.id == id) it.copy(connected = wantConnected) else it
            }
        }
    }

    /**
     * A freshly connected worker takes a moment to come up, and one immediate
     * re-read still reports the old state — which looks exactly like "the
     * skill keeps disconnecting". Poll the host until it agrees with the
     * action we just sent (or a few seconds pass) so the card settles on the
     * truth instead of the pre-action snapshot.
     */
    private fun confirmRuntime(id: String, expectRunning: Boolean) {
        viewModelScope.launch {
            repeat(6) { attempt ->
                kotlinx.coroutines.delay(if (attempt == 0) 800L else 1_300L)
                val r = withContext(Dispatchers.IO) { api.skills() }
                if (r.isHttpOk) {
                    val reported = (0 until (r.json()?.optJSONArray("skills")?.length() ?: 0)).any { i ->
                        val o = r.json()?.optJSONArray("skills")?.optJSONObject(i)
                        o?.optStr("id") == id && o.optBoolean("worker_running") == expectRunning
                    }
                    if (reported) {
                        applySkills(r.json())
                        loadDevices()
                        return@launch
                    }
                    // Server hasn't caught up yet: keep the optimistic state
                    // on screen for the remaining attempts.
                }
            }
            // Give up polling and show whatever the host reports now.
            val finalRead = withContext(Dispatchers.IO) { api.skills() }
            if (finalRead.isHttpOk) applySkills(finalRead.json())
            loadDevices()
        }
    }

    fun loadDevices() = viewModelScope.launch {
        fetch("device-list", api::devices) { r ->
            if (r.isHttpOk) {
                val arr = r.json()?.optJSONArray("devices")
                deviceItems = if (arr == null) {
                    emptyList()
                } else {
                    (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        val id = o.optStr("id")
                        if (id.isBlank()) null else parseDevice(o, id)
                    }
                }
                updateRingState()
            } else {
                deviceItems = emptyList()
                dataError["device-list"] = r.hint()
            }
        }
    }

    /** Turn one GET /api/devices record into display state plus its declared UI. */
    private fun parseDevice(o: JSONObject, id: String): DeviceItem {
        val reported = o.opt("device_status")
        val status = if (reported is JSONObject) reported else null
        val ui = o.optJSONObject("device_ui")
        val rawConnected = status?.opt("device_connected")
        return DeviceItem(
            id = id,
            label = o.optStr("device_label")
                .ifBlank { o.optStr("name").ifBlank { id } },
            name = o.optStr("name").ifBlank { id },
            icon = o.optStr("icon").ifBlank { "✦" },
            connected = o.optBoolean("worker_running"),
            declared = o.optBoolean("runtime_declared"),
            status = when (reported) {
                null, JSONObject.NULL -> "Not reported yet"
                else -> JsonRender.pretty(reported)
            },
            alias = o.optStr("device_alias"),
            configured = o.optBoolean("configured"),
            trusted = o.optBoolean("trusted"),
            fingerprint = o.optStr("fingerprint").ifBlank { null },
            runtimeError = o.optStr("runtime_error").ifBlank { null },
            deviceConnected = rawConnected as? Boolean,
            fields = parseDeviceFields(ui?.optJSONArray("fields"), status),
            controls = parseDeviceControls(ui?.optJSONArray("controls")),
        )
    }

    /** device_ui.fields rows resolved against the skill's reported status. */
    private fun parseDeviceFields(arr: JSONArray?, status: JSONObject?): List<DeviceField> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val f = arr.optJSONObject(i) ?: return@mapNotNull null
            val label = f.optStr("label").ifBlank { f.optStr("path") }
            if (label.isBlank()) return@mapNotNull null
            val raw = pathValue(status, f.optStr("path"))
            when (f.optStr("format")) {
                "color" -> {
                    val rgb = (raw as? JSONArray)?.let { a ->
                        if (a.length() == 3) {
                            (0 until a.length()).map { a.opt(it) }
                        } else null
                    }?.filterIsInstance<Number>()?.map { it.toInt() }
                    if (rgb != null && rgb.size == 3 && rgb.all { it in 0..255 }) {
                        DeviceField(label, rgb.joinToString(", "), rgbToHex(rgb))
                    } else {
                        DeviceField(label, "Unavailable")
                    }
                }
                "boolean" -> DeviceField(
                    label,
                    when (raw) {
                        true -> "Yes"
                        false -> "No"
                        else -> "Unavailable"
                    },
                )
                "percent" -> {
                    val n = (raw as? Number)?.toDouble()
                    DeviceField(
                        label,
                        if (n != null && n.isFinite()) {
                            "${Math.round(if (n <= 1.0) n * 100 else n)}%"
                        } else "Unavailable",
                    )
                }
                else -> DeviceField(
                    label,
                    when (raw) {
                        null, JSONObject.NULL -> "Unavailable"
                        else -> raw.toString().take(256)
                    },
                )
            }
        }
    }

    /** device_ui.controls — tools the skill lets us call with declared inputs. */
    private fun parseDeviceControls(arr: JSONArray?): List<DeviceControl> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            val tool = c.optStr("tool")
            if (tool.isBlank()) return@mapNotNull null
            val fieldsArr = c.optJSONArray("fields")
            val fields = if (fieldsArr == null) emptyList() else {
                (0 until fieldsArr.length()).mapNotNull { j ->
                    val f = fieldsArr.optJSONObject(j) ?: return@mapNotNull null
                    val name = f.optStr("name")
                    if (name.isBlank()) null else DeviceControlField(
                        name = name,
                        label = f.optStr("label").ifBlank { name },
                        widget = f.optStr("widget").ifBlank { "text" },
                        options = JsonRender.extractList(f.opt("options")).map { it.toString() },
                        minimum = (f.opt("minimum") as? Number)?.toDouble(),
                        maximum = (f.opt("maximum") as? Number)?.toDouble(),
                    )
                }
            }
            DeviceControl(
                tool = tool,
                label = c.optStr("label").ifBlank { tool },
                baseArguments = c.optJSONObject("arguments") ?: JSONObject(),
                fields = fields,
            )
        }
    }

    /** Dotted-path lookup against a reported status object (device_ui paths). */
    private fun pathValue(root: JSONObject?, path: String): Any? {
        if (root == null || path.isBlank()) return null
        var current: Any? = root
        for (segment in path.split('.')) {
            current = (current as? JSONObject)?.opt(segment)
            if (current == null || current == JSONObject.NULL) return null
        }
        return current
    }

    private fun rgbToHex(rgb: List<Int>): String =
        "#" + rgb.joinToString("") { "%02x".format(it) }

    // -------------------------------------------------------------- settings

    fun saveSettings() {
        config.baseUrl = serverUrl
        config.location = location
        config.cfClientId = cfClientId
        config.cfClientSecret = cfClientSecret
        config.bearerToken = bearerToken
        saved = true
        configured = config.baseUrl.isNotBlank()
        testLines = emptyList()
        // Reload everything that depends on the connection.
        healthChips = emptyList()
        homeCopy = null
        memoryText = null
        kbCards = emptyList()
        peopleCards = emptyList()
        scheduleUpcoming = emptyList()
        schedulePast = emptyList()
        scheduleActions = emptyList()
        modelRows = emptyList()
        deviceRows = emptyList()
        sessionRows = emptyList()
        stageRows = emptyList()
        traceRows = emptyList()
        assistantName = null
        activeModelId = null
        healthPayload = null
        modelChoices = emptyList()
        modelSelectable = false
        modelNotice = null
        skillItems = emptyList()
        skillRepos = emptyList()
        skillNotice = null
        updateNotice = null
        deviceItems = emptyList()
        if (config.baseUrl.isNotBlank()) screen = Screen.Home
    }

    fun testConnection() {
        if (testBusy) return
        testBusy = true
        testLines = listOf(TestLine("Contacting ${serverUrl.ifBlank { "(no URL set)" }} …", null))
        viewModelScope.launch {
            val lines = mutableListOf<TestLine>()

            if (serverUrl.isBlank()) {
                testLines = listOf(TestLine("Set the NIX console URL first.", false))
                testBusy = false
                return@launch
            }

            val profile = withContext(Dispatchers.IO) { api.profile() }
            lines.add(
                if (profile.isHttpOk) {
                    TestLine("Console reachable — HTTP 200 in ${profile.ms} ms", true)
                } else {
                    TestLine(profile.hint(), false)
                }
            )
            testLines = lines.toList()

            if (profile.isHttpOk) {
                val health = withContext(Dispatchers.IO) { api.health() }
                if (health.isHttpOk) {
                    val obj = health.json()
                    val knowledge = obj?.optJSONObject("knowledge")?.optBoolean("ok", false) == true
                    val actions = obj?.optJSONObject("actions")?.optBoolean("ok", false) == true
                    val model = obj?.optJSONObject("assistant")?.optStr("active")
                        ?: obj?.optJSONObject("casper")?.optStr("model") ?: "unreported"
                    lines.add(TestLine("Knowledge ${if (knowledge) "up" else "down"} · Actions ${if (actions) "up" else "down"} · Companion $model", true))
                } else {
                    lines.add(TestLine(health.hint(), false))
                }
                testLines = lines.toList()

                val mobile = withContext(Dispatchers.IO) { api.mobileIndex() }
                lines.add(
                    when {
                        mobile.isHttpOk -> TestLine("Mobile API v1 index present (console_extend routes live)", true)
                        mobile.code == 404 -> TestLine("Mobile v1 index absent — launch with python nix_core/console_extend.py for home copy", null)
                        else -> TestLine(mobile.hint(), false)
                    }
                )
            }

            lines.add(TestLine("Credentials attached: ${config.authSummary}", true))
            testLines = lines.toList()
            testBusy = false
        }
    }

    /** The paired unit whose detail page is open (BLE address), if any. */
    var nixDevAddress: String? = null

    /** Bumped to re-probe the Nix Dev unit (refresh, rename, unpair). */
    var nixDevTick by mutableStateOf(0)

    /** Opens this unit's own full-screen page. */
    fun openNixDev(address: String) {
        nixDevAddress = address
        screen = Screen.NixDev
    }

    /** Opens a screen and loads its data on first visit. */
    fun navigate(target: Screen) {
        screen = target
        when (target) {
            Screen.Home -> if (healthChips.isEmpty()) refreshHome()
            Screen.Knowledge -> loadKnowledge()
            Screen.Schedule -> if (scheduleUpcoming.isEmpty() && schedulePast.isEmpty()) loadSchedule()
            Screen.Skills -> if (skillItems.isEmpty()) loadSkills()
            Screen.Devices -> if (deviceItems.isEmpty()) loadDevices()
            Screen.System -> if (modelRows.isEmpty() && deviceRows.isEmpty()) loadSystem()
            Screen.Hardware -> if (healthPayload == null) loadHealth()
            else -> Unit
        }
    }

    companion object {
        /** Busy key for catalog-level actions that belong to no single card. */
        private const val MARKET_BUSY_ID = "__market__"


        /** Mirror of the server's home copy shape, used when the route is absent. */
        val DEFAULT_HOME_COPY: JSONObject = JSONObject(
            """
            {
              "copy_id": "home-bundled",
              "headline": "What would you like to work through?",
              "subtitle": "No rush. We can talk, make a small plan, or simply take a breath together.",
              "section_title": "Choose a gentle beginning",
              "suggestions": [
                {"icon": "♡", "title": "Check in with me", "description": "A little space to be heard",
                 "prompt": "Can you check in with me? I want to talk through how I'm feeling."},
                {"icon": "☼", "title": "Help me reset", "description": "Find a softer next step",
                 "prompt": "Help me reset and make the rest of today feel easier."},
                {"icon": "⌁", "title": "Save a thought", "description": "Keep what matters close",
                 "prompt": "Remember a thought I want to keep."}
              ]
            }
            """.trimIndent()
        )
    }
}
