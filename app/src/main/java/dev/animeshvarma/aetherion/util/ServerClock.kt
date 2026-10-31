package dev.animeshvarma.aetherion.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong

object ServerClock {
    private val skewMillis = AtomicLong(0L)
    private val httpDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    fun sync(dateHeader: String?) {
        if (dateHeader.isNullOrBlank()) return
        try {
            val serverTime = synchronized(httpDateFormat) {
                httpDateFormat.parse(dateHeader)?.time
            } ?: return
            skewMillis.set(serverTime - System.currentTimeMillis())
        } catch (_: Exception) {
        }
    }

    fun nowMillis(): Long = System.currentTimeMillis() + skewMillis.get()

    fun epochSeconds(): Long = nowMillis() / 1000L
}