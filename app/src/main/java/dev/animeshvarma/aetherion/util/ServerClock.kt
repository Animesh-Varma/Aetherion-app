package dev.animeshvarma.aetherion.util

import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * The #PWD_INSTAGRAM timestamp is used as AES-GCM AAD by the server.
 * A device clock that drifts from server time corrupts decryption → bad_password.
 * Compute a one-time offset from any response's `Date` header.
 */
object ServerClock {
    @Volatile
    private var offsetSeconds: Long = 0L

    fun sync(dateHeader: String?) {
        if (dateHeader.isNullOrBlank()) return
        try {
            val serverMs = DateTimeFormatter.RFC_1123_DATE_TIME
                .parse(dateHeader, Instant::from).toEpochMilli()
            offsetSeconds = (serverMs - System.currentTimeMillis()) / 1000
        } catch (_: Exception) { /* keep offset 0 */ }
    }

    fun epochSeconds(): Long = System.currentTimeMillis() / 1000 + offsetSeconds
}