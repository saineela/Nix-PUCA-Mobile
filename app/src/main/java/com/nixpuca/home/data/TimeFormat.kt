package com.nixpuca.home.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * How the phone itself formats a clock time.
 *
 * Read once from the Android 24-hour setting
 * ([android.text.format.DateFormat.is24HourFormat]) and then used wherever the
 * app renders a time it received from the console. This is presentation only:
 * the server keeps sending ISO-8601 / 24-hour strings, and nothing on the wire
 * changes — a phone set to a 12-hour clock just reads "5:04 PM" instead of
 * "17:04".
 */
object TimeFormat {

    /** True when the device's own clock setting is 24-hour. */
    @Volatile
    var use24Hour: Boolean = true
        private set

    private val h12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private val h24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Seed from the device setting; called once when the app starts. */
    fun configure(is24Hour: Boolean) {
        use24Hour = is24Hour
    }

    /** The pattern matching the phone's clock style. */
    fun pattern(): String = if (use24Hour) "HH:mm" else "h:mm a"

    /** Format a [LocalTime] the way this phone's clock shows time. */
    fun format(time: LocalTime): String =
        time.format(if (use24Hour) h24 else h12)

    /**
     * Format an `HH:mm` / `HH:mm:ss` string the way this phone's clock shows
     * time. Anything unparseable is returned untouched.
     */
    fun format(hhMm: String?): String {
        val text = hhMm?.trim().orEmpty()
        if (text.isEmpty()) return text
        return try {
            format(LocalTime.parse(if (text.length == 5) text else text.substring(0, 5)))
        } catch (_: Throwable) {
            text
        }
    }
}
