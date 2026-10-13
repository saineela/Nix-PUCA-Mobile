package com.nixpuca.home.data

import org.json.JSONArray
import org.json.JSONObject

/** One row the UI renders as a card: a title plus readable key/value fields. */
data class JsonCard(
    val title: String,
    val fields: List<Pair<String, String>>,
    val raw: String,
) {
    companion object {
        fun empty(): JsonCard = JsonCard("—", emptyList(), "")
    }
}

/**
 * Turning the console's heterogeneous JSON into compact, readable cards.
 *
 * Endpoints return either a bare list (`/api/kb`) or an object wrapping lists
 * under a preferred key (`/api/schedule` -> upcoming/past). These helpers pick
 * the most useful array, give each row a title, and flatten scalar fields so a
 * phone screen can show them without horizontal scrolling.
 */
object JsonRender {

    private val PREFERRED_KEYS = listOf(
        "records", "people", "sessions", "devices", "skills", "events",
        "upcoming", "actions", "stages", "trace", "turns", "models",
        "items", "results", "data", "variants",
    )

    private val TITLE_KEYS = listOf(
        "title", "name", "device_label", "label", "subject", "headline",
        "session_tag", "type", "knowledge_type", "action_type", "id",
    )

    /** Titles that should win over the record's own content when present. */
    private val PRIMARY_TITLE_KEYS = listOf(
        "title", "name", "device_label", "label", "subject", "headline", "session_tag",
    )

    private val ISO_LIKE = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")

    private val SKIP_KEYS = setOf("token", "secret", "password", "api_key")

    /** Pretty-print any JSON value. */
    fun pretty(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is JSONObject -> try {
            value.toString(2)
        } catch (t: Throwable) {
            value.toString()
        }
        is JSONArray -> try {
            value.toString(2)
        } catch (t: Throwable) {
            value.toString()
        }
        else -> value.toString()
    }

    /** First useful array inside [root], or the root itself when it is a list. */
    fun extractList(root: Any?): List<Any?> {
        when (root) {
            is JSONArray -> return (0 until root.length()).map { root.opt(it) }
            !is JSONObject -> return emptyList()
        }
        val obj = root as JSONObject
        for (key in PREFERRED_KEYS) {
            val candidate = obj.opt(key)
            if (candidate is JSONArray) {
                return (0 until candidate.length()).map { candidate.opt(it) }
            }
        }
        // No known key: take the first array we can find.
        val names = obj.keys()
        while (names.hasNext()) {
            val candidate = obj.opt(names.next())
            if (candidate is JSONArray) {
                return (0 until candidate.length()).map { candidate.opt(it) }
            }
        }
        return listOf(obj)
    }

    fun cards(root: Any?): List<JsonCard> =
        extractList(root).mapIndexed { index, item -> card(item, index) }

    private fun card(item: Any?, index: Int): JsonCard {
        if (item !is JSONObject) {
            return JsonCard(
                title = "#${index + 1}",
                fields = listOf("value" to (item?.toString() ?: "—")),
                raw = pretty(item),
            )
        }
        val dataObj = item.opt("data") as? JSONObject
        val content = (dataObj?.opt("value") as? String)?.takeIf { it.isNotBlank() }
        val primaryTitle = PRIMARY_TITLE_KEYS.firstNotNullOfOrNull { key ->
            (item.opt(key) as? String)?.takeIf { it.isNotBlank() }
        }
        val contentIsTitle = primaryTitle == null && content != null && content.length <= 84
        val title = when {
            primaryTitle != null -> primaryTitle
            content != null && content.length > 84 -> content.take(82) + "…"
            content != null -> content
            else -> titleFor(item, index)
        }
        val fields = mutableListOf<Pair<String, String>>()
        val used = mutableSetOf<String>()
        val names = item.keys()
        while (names.hasNext()) {
            val key = names.next()
            if (SKIP_KEYS.contains(key.lowercase())) continue
            val value = item.opt(key) ?: continue
            when {
                // The `data` envelope is the record's payload: flatten it in
                // place so it reads as fields, never as a JSON blob.
                key == "data" && dataObj != null ->
                    flatten("", dataObj, fields, used, if (contentIsTitle) content else null)
                value is JSONObject || value is JSONArray ->
                    flatten(key, value, fields, used, null)
                value === JSONObject.NULL -> Unit
                else -> addField(key, value.toString(), fields, used, null)
            }
        }
        return JsonCard(title, fields, pretty(item))
    }

    /**
     * Depth-first walk that turns nested JSON into flat `path to value` rows.
     * Scalar arrays collapse into one `a, b, c` value; objects recurse with a
     * space-joined path so [humanKey]-style labels stay readable.
     */
    private fun flatten(
        prefix: String,
        value: Any?,
        out: MutableList<Pair<String, String>>,
        used: MutableSet<String>,
        skipValue: String?,
    ) {
        when (value) {
            null, JSONObject.NULL -> Unit
            is JSONObject -> {
                val names = value.keys()
                while (names.hasNext()) {
                    val key = names.next()
                    if (SKIP_KEYS.contains(key.lowercase())) continue
                    val child = value.opt(key) ?: continue
                    val path = if (prefix.isEmpty()) key else "$prefix $key"
                    flatten(path, child, out, used, skipValue)
                }
            }
            is JSONArray -> {
                if (value.length() == 0) return
                val scalars = (0 until value.length()).map { value.opt(it) }
                if (scalars.all { it != null && it !== JSONObject.NULL && it !is JSONObject && it !is JSONArray }) {
                    addField(prefix, scalars.joinToString(", ") { it.toString() }, out, used, skipValue)
                } else {
                    for (i in 0 until value.length()) {
                        flatten("$prefix ${i + 1}", value.opt(i), out, used, skipValue)
                    }
                }
            }
            else -> addField(prefix, value.toString(), out, used, skipValue)
        }
    }

    private fun addField(
        key: String,
        raw: String,
        out: MutableList<Pair<String, String>>,
        used: MutableSet<String>,
        skipValue: String?,
    ) {
        if (key.isBlank()) return
        if (skipValue != null && raw == skipValue) return
        if (!used.add(key)) return
        out.add(key to humanValue(raw))
    }

    /**
     * "2026-10-06T23:16:27.379645+00:00" → "6 Oct 2026, 11:16 PM" (local),
     * or "23:16" when this phone's clock is set to 24-hour.
     */
    private fun humanValue(raw: String): String {
        if (!ISO_LIKE.containsMatchIn(raw)) return raw
        val pattern = java.time.format.DateTimeFormatter.ofPattern(
            "d MMM yyyy, " + com.nixpuca.home.data.TimeFormat.pattern(),
        )
        return try {
            java.time.OffsetDateTime.parse(raw, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .format(pattern)
        } catch (_: Throwable) {
            try {
                java.time.LocalDateTime.parse(raw, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .format(pattern)
            } catch (_: Throwable) {
                raw
            }
        }
    }

    private fun titleFor(obj: JSONObject, index: Int): String {
        for (key in TITLE_KEYS) {
            val value = obj.opt(key)
            if (value is String && value.isNotBlank()) return value
            if (value != null && value !is JSONObject && value !is JSONArray && value != JSONObject.NULL) {
                return value.toString()
            }
        }
        return "#${index + 1}"
    }

    /** Flatten an object of scalars into ordered key/value rows. */
    fun scalars(obj: JSONObject): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        val names = obj.keys()
        while (names.hasNext()) {
            val key = names.next()
            if (SKIP_KEYS.contains(key.lowercase())) continue
            val value = obj.opt(key) ?: continue
            when (value) {
                is JSONObject, is JSONArray -> Unit
                JSONObject.NULL -> Unit
                else -> rows.add(key to value.toString())
            }
        }
        return rows
    }

    /** Deep search for a boolean/number/string field across nested objects. */
    fun find(root: Any?, vararg keys: String): String? {
        when (root) {
            is JSONObject -> for (key in keys) {
                val value = root.opt(key)
                if (value != null && value != JSONObject.NULL && value !is JSONObject && value !is JSONArray) {
                    return value.toString()
                }
            }
            is JSONArray -> if (root.length() > 0) return find(root.opt(0), *keys)
        }
        return null
    }
}
