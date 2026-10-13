package com.nixpuca.home.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Connection settings for the NIX PUCA console.
 *
 * The Cloudflare Access service token (CF-Access-Client-Id / secret) and the
 * optional OpenAI-compatible bearer key are secrets: they are stored in an
 * [EncryptedSharedPreferences] file backed by the Android Keystore. Plain
 * preferences only ever hold the server URL, device label and conversation id.
 * A device where the Keystore store cannot be created falls back to plain
 * private preferences so the app stays usable; that path is logged by the
 * Settings screen.
 */
class NixConfig(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nix_home_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        secretsFallback = true
        context.getSharedPreferences("nix_home_credentials_plaintext", Context.MODE_PRIVATE)
    }

    private val plain: SharedPreferences =
        context.getSharedPreferences("nix_home_prefs", Context.MODE_PRIVATE)

    /** True when the encrypted credential store could not be created. */
    val insecureCredentialStore: Boolean
        get() = secretsFallback

    var baseUrl: String
        get() = plain.getString(KEY_URL, "").orEmpty()
        set(value) = plain.edit().putString(KEY_URL, value.trim()).apply()

    /** Free-text label sent as `location`, e.g. "pixel" or "pocket". */
    var location: String
        get() = plain.getString(KEY_LOCATION, "android-app").orEmpty()
        set(value) = plain.edit().putString(KEY_LOCATION, value.trim().ifEmpty { "android-app" }).apply()

    /** Kept stable across restarts so Core can replay the same conversation. */
    var conversationId: String
        get() = plain.getString(KEY_CONVERSATION, "").orEmpty().ifEmpty {
            java.util.UUID.randomUUID().toString().also { id ->
                plain.edit().putString(KEY_CONVERSATION, id).apply()
            }
        }
        set(value) = plain.edit().putString(KEY_CONVERSATION, value.trim()).apply()

    var cfClientId: String
        get() = prefs.getString(KEY_CF_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CF_ID, value.trim()).apply()

    var cfClientSecret: String
        get() = prefs.getString(KEY_CF_SECRET, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CF_SECRET, value.trim()).apply()

    /** Optional `Authorization: Bearer` value for the OpenAI-compatible routes. */
    var bearerToken: String
        get() = prefs.getString(KEY_BEARER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_BEARER, value.trim()).apply()

    val hasCloudflareToken: Boolean
        get() = cfClientId.isNotBlank() && cfClientSecret.isNotBlank()

    /** Human description of the credentials that will be attached. */
    val authSummary: String
        get() = when {
            hasCloudflareToken && bearerToken.isNotBlank() ->
                "Cloudflare service token + bearer key"
            hasCloudflareToken -> "Cloudflare service token"
            bearerToken.isNotBlank() -> "Bearer key"
            else -> "No credentials (trusted local network)"
        }

    fun resetConversation() {
        conversationId = java.util.UUID.randomUUID().toString()
    }

    // ------------------------------------------------------------- appearance

    /** Accent style id: "mono" (official black & white) or a colour name. */
    var accentId: String
        get() = plain.getString(KEY_ACCENT, "mono").orEmpty().ifEmpty { "mono" }
        set(value) = plain.edit().putString(KEY_ACCENT, value).apply()

    /** When true the app background mirrors the ring light's live effect. */
    var mirrorRingEffect: Boolean
        get() = plain.getBoolean(KEY_MIRROR_RING, false)
        set(value) = plain.edit().putBoolean(KEY_MIRROR_RING, value).apply()

    /** Dark theme follows the console's inverted palette. */
    var darkTheme: Boolean
        get() = plain.getBoolean(KEY_DARK_THEME, false)
        set(value) = plain.edit().putBoolean(KEY_DARK_THEME, value).apply()

    /** First-run setup flow finished (welcome + guided configuration). */
    var setupComplete: Boolean
        get() = plain.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = plain.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    /**
     * Addresses of Nix Dev units this app has finished the pairing wizard
     * with (Wi-Fi set, echo-connect installed/configured/trusted/running).
     * Kept across restarts so the device list keeps its badges and a unit
     * is not re-paired by accident.
     */
    var pairedDevices: Set<String>
        get() = plain.getString(KEY_PAIRED, "").orEmpty()
            .split('\u0001').filter { it.isNotBlank() }.toSet()
        set(value) = plain.edit()
            .putString(KEY_PAIRED, value.joinToString("\u0001"))
            .apply()

    /** Ordered profile names; the first entry is the default profile. */
    var profiles: List<String>
        get() = plain.getString(KEY_PROFILES, "").orEmpty()
            .split('\u0001').filter { it.isNotBlank() }
        set(value) = plain.edit()
            .putString(KEY_PROFILES, value.joinToString("\u0001"))
            .apply()

    /** Currently selected profile name (blank = first/default profile). */
    var activeProfile: String
        get() = plain.getString(KEY_ACTIVE_PROFILE, "").orEmpty()
        set(value) = plain.edit().putString(KEY_ACTIVE_PROFILE, value).apply()

    /** Google Calendar secret iCal address used by the Schedule import. */
    var googleCalendarUrl: String
        get() = plain.getString(KEY_GCAL_URL, "").orEmpty()
        set(value) = plain.edit().putString(KEY_GCAL_URL, value.trim()).apply()

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_LOCATION = "location"
        private const val KEY_CONVERSATION = "conversation_id"
        private const val KEY_CF_ID = "cf_access_client_id"
        private const val KEY_CF_SECRET = "cf_access_client_secret"
        private const val KEY_BEARER = "bearer_token"
        private const val KEY_ACCENT = "accent_id"
        private const val KEY_MIRROR_RING = "mirror_ring_effect"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_PAIRED = "paired_devices"
        private const val KEY_PROFILES = "profile_names"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val KEY_GCAL_URL = "google_calendar_ical_url"

        @Volatile
        private var secretsFallback: Boolean = false

        const val DEFAULT_LOCATION = "android-app"
    }
}
