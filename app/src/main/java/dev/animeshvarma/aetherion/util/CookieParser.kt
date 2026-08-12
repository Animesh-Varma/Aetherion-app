package dev.animeshvarma.aetherion.util

import okhttp3.Headers

/** Extracts the IG session cookies directly from raw Set-Cookie headers. */
object CookieParser {

    data class IgSession(
        val sessionId: String,
        val dsUserId: String,
        val rur: String
    )

    /**
     * Scans every Set-Cookie header for the named cookies.
     * Ignores domain/path/secure attrs — just grabs name=value.
     */
    fun extract(name: String, headers: Headers): String? {
        for (value in headers.values("Set-Cookie")) {
            val eq = value.indexOf('=')
            if (eq < 0) continue
            val cookieName = value.substring(0, eq).trim()
            if (cookieName == name) {
                // value ends at the first ';' (attribute separator)
                val end = value.indexOf(';', eq + 1)
                val raw = if (end < 0) value.substring(eq + 1) else value.substring(eq + 1, end)
                return raw.trim()
            }
        }
        return null
    }

    fun extractSession(headers: Headers): IgSession? {
        val sessionId = extract("sessionid", headers) ?: return null
        val dsUserId = extract("ds_user_id", headers) ?: ""
        val rur = extract("rur", headers) ?: ""
        return IgSession(sessionId, dsUserId, rur)
    }
}