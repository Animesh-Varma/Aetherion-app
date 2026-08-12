package dev.animeshvarma.aetherion.network.interceptors

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.util.UUID

class LoggingInterceptor : Interceptor {

    companion object {
        private val logEntries = mutableListOf<LogEntry>()
        private val maxEntries = 500

        fun getLogs(): List<LogEntry> = logEntries.toList()
        fun clearLogs() { logEntries.clear() }

        fun logNetwork(tag: String, message: String) {
            addEntry(LogEntry(System.currentTimeMillis(), tag, message, LogType.NETWORK))
            android.util.Log.d(tag, message)
        }

        fun logRequest(request: Request, requestId: String) {
            val body = request.body?.let { bufferToString(it) } ?: ""
            val headers = request.headers.toMultimap()
            val msg = """
                ┌─ REQUEST [$requestId] ${request.method} ${request.url}
                ├─ Headers: $headers
                ├─ Body: $body
                └─ END
            """.trimIndent()
            addEntry(LogEntry(System.currentTimeMillis(), "REQUEST", msg, LogType.REQUEST, requestId))
        }

        fun logResponse(response: Response, requestId: String, tookMs: Long) {
            val body = response.body?.let {
                val source = it.source()
                source.request(Long.MAX_VALUE)
                source.buffer.clone().readUtf8()
            } ?: ""

            val msg = """
                ┌─ RESPONSE [$requestId] ${response.code} ${response.message} (${tookMs}ms)
                ├─ Headers: ${response.headers}
                ├─ Body: $body
                └─ END
            """.trimIndent()
            addEntry(LogEntry(System.currentTimeMillis(), "RESPONSE", msg, LogType.RESPONSE, requestId))
        }

        private fun addEntry(entry: LogEntry) {
            synchronized(logEntries) {
                logEntries.add(0, entry)
                if (logEntries.size > maxEntries) logEntries.removeLast()
            }
        }

        private fun bufferToString(body: okhttp3.RequestBody): String {
            val buffer = Buffer()
            body.writeTo(buffer)
            return buffer.readUtf8()
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestId = UUID.randomUUID().toString().substring(0, 8)
        val startTime = System.currentTimeMillis()

        logRequest(request, requestId)

        try {
            val response = chain.proceed(request)
            logResponse(response, requestId, System.currentTimeMillis() - startTime)
            return response
        } catch (e: Exception) {
            val msg = "Request $requestId failed: ${e.message}"
            addEntry(LogEntry(System.currentTimeMillis(), "ERROR", msg, LogType.ERROR, requestId))
            throw e
        }
    }
}

data class LogEntry(
    val timestamp: Long,
    val tag: String,
    val message: String,
    val type: LogType,
    val requestId: String = ""
)

enum class LogType { NETWORK, REQUEST, RESPONSE, ERROR, INFO }