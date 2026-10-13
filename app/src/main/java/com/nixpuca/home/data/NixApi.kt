package com.nixpuca.home.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * optString, minus org.json's nastiest surprise: when the console sends an
 * explicit `null` (e.g. `"runtime_error": null`) the platform's optString
 * returns the literal four-character string "null", which then renders on
 * screen as a mystery "null" row. Treat JSON null as an empty string so
 * `.ifBlank { null }` callers see it as absent.
 */
fun JSONObject.optStr(name: String): String =
    if (isNull(name)) "" else optString(name)

/** Raw outcome of one HTTP call: status code, body, transport error, latency. */
data class ApiResult(
    val code: Int,
    val body: String,
    val transportError: String? = null,
    val ms: Long = 0,
) {
    val isHttpOk: Boolean get() = transportError == null && code in 200..299

    fun json(): JSONObject? = try {
        JSONObject(body)
    } catch (t: Throwable) {
        null
    }

    fun isHtml(): Boolean = body.trimStart().startsWith("<")

    /**
     * One-line diagnosis the UI can show next to a failed request. The NIX
     * console returns {"ok":false,"error":"…"} on 4xx, so prefer that wording
     * over a bare status code — it is the only place the real reason shows up.
     */
    fun hint(): String {
        transportError?.let { return it }
        val detail = serverError()
        return when {
            code == 401 ->
                "401 — the server rejected the credentials (check the bearer key / NIX token)."
            code == 403 ->
                "403 — Cloudflare Access refused the request. Check the service token Client ID and Secret."
            code == 404 ->
                "404 — endpoint not found. This console build may not expose it (try python nix_core/console_extend.py)."
            detail != null -> "HTTP $code — $detail"
            code >= 500 -> "HTTP $code — the NIX service reported a server error."
            isHtml() ->
                "The URL answered with a web page, not the NIX API. Check the host and port (console default 49117)."
            else -> "HTTP $code"
        }
    }

    /** The console's own `error` string, when the body is a failure payload. */
    private fun serverError(): String? = try {
        val obj = JSONObject(body)
        if (obj.optBoolean("ok", true) && !obj.has("error")) null else obj.optStr("error").ifBlank { null }
    } catch (t: Throwable) {
        null
    }
}

/**
 * Thin HTTP client for the NIX PUCA console.
 *
 * Every request carries the Cloudflare Access service-token headers when they
 * are configured, plus an optional `Authorization: Bearer` for the
 * OpenAI-compatible routes. No credential is ever logged or returned.
 */
class NixApi(private val config: NixConfig) {

    private fun applyAuth(conn: HttpURLConnection) {
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "NixHome/1.0 (Android)")
        conn.setRequestProperty("X-Nix-Client", "nix-home-android")
        if (config.cfClientId.isNotBlank()) {
            conn.setRequestProperty("CF-Access-Client-Id", config.cfClientId)
        }
        if (config.cfClientSecret.isNotBlank()) {
            conn.setRequestProperty("CF-Access-Client-Secret", config.cfClientSecret)
        }
        if (config.bearerToken.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer ${config.bearerToken}")
        }
    }

    private fun request(method: String, path: String, body: String?, timeoutMs: Int): ApiResult {
        val base = config.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) {
            return ApiResult(-1, "", "No server URL set — open Settings first.")
        }
        val started = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(base + path).openConnection() as HttpURLConnection)
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            applyAuth(conn)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { out -> out.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            ApiResult(code, text, null, System.currentTimeMillis() - started)
        } catch (e: SocketTimeoutException) {
            ApiResult(-1, "", "Timed out after ${timeoutMs / 1000}s — the model may still be loading.", System.currentTimeMillis() - started)
        } catch (e: UnknownHostException) {
            ApiResult(-1, "", "Host unreachable — check the URL and the phone's network.", System.currentTimeMillis() - started)
        } catch (e: IOException) {
            ApiResult(-1, "", "Network error: ${e.message ?: "connection failed"}.", System.currentTimeMillis() - started)
        } catch (e: Exception) {
            ApiResult(-1, "", "Request failed: ${e.message ?: e.javaClass.simpleName}.", System.currentTimeMillis() - started)
        } finally {
            conn?.disconnect()
        }
    }

    fun get(path: String, timeoutMs: Int = 25_000): ApiResult =
        request("GET", path, null, timeoutMs)

    /**
     * Cheap reachability probe for the cold-start session gate. True when the
     * hub answers at all (any HTTP status — even 4xx/5xx means it is up), false
     * only on a transport failure (DNS miss, refused, timeout). Lets the app
     * sign a user out when their configured hub is unreachable while keeping
     * the cached connection for an easy re-login.
     */
    fun reachable(): Boolean = get("/api/health", timeoutMs = 8_000).transportError == null

    fun post(path: String, payload: JSONObject, timeoutMs: Int = 300_000): ApiResult =
        request("POST", path, payload.toString(), timeoutMs)

    // ---------------------------------------------------------------- endpoints

    fun profile() = get("/api/profile")
    fun saveProfile(name: String): ApiResult =
        post("/api/profile", JSONObject().put("user_name", name))
    fun health() = get("/api/health", timeoutMs = 60_000)
    fun mobileIndex() = get("/api/mobile/v1", timeoutMs = 12_000)
    fun homeCopy() = get("/api/mobile/v1/home-copy", timeoutMs = 12_000)
    fun models() = get("/api/models")
    fun pipeline() = get("/api/pipeline")
    fun memory() = get("/api/memory")
    fun kb() = get("/api/kb")
    fun people() = get("/api/people")
    fun schedule() = get("/api/schedule")
    fun devices() = get("/api/devices")
    fun sessions() = get("/api/sessions")
    fun skills() = get("/api/skills")
    fun skillsRefresh() = get("/api/skills?refresh=1", timeoutMs = 45_000)

    /** POST /api/skills — install/uninstall and skill tuning actions. */
    fun skillAction(payload: JSONObject, timeoutMs: Int = 120_000): ApiResult =
        post("/api/skills", payload, timeoutMs)

    /** POST /api/model — switch the active official assistant model (Luna/Casper). */
    fun selectModel(modelId: String): ApiResult =
        post("/api/model", JSONObject().put("model", modelId), timeoutMs = 300_000)
    fun feed() = get("/api/feed")

    fun send(text: String, conversationId: String, model: String? = null): ApiResult {
        val payload = JSONObject()
            .put("text", text)
            .put("location", config.location)
            .put("conversation_id", conversationId)
        if (!model.isNullOrBlank()) payload.put("model", model)
        return post("/api/send", payload, timeoutMs = 300_000)
    }
}

/** Small helpers for reading the console's loosely typed JSON payloads. */
object Json {
    fun arr(root: Any?): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        when (root) {
            is JSONArray -> for (i in 0 until root.length()) {
                val item = root.opt(i)
                if (item is JSONObject) list.add(item)
            }
            is JSONObject -> list.add(root)
        }
        return list
    }

    fun str(obj: JSONObject?, key: String): String =
        obj?.opt(key)?.toString()?.replace("\"", "") ?: ""
}
