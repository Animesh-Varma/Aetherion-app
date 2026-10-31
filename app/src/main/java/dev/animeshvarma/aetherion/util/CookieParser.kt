package dev.animeshvarma.aetherion.util

import okhttp3.Headers

object CookieParser {

    data class IgSession(
        val sessionId: String,
        val dsUserId: String,
        val rur: String
    )

    fun extractSession(headers: Headers): IgSession? {
        val cookieValues = headers.values("Set-Cookie")
        var sessionId: String? = null
        var dsUserId = ""
        var rur = ""

        for (raw in cookieValues) {
            val parts = raw.split(";")
            val nameValue = parts.firstOrNull()?.trim() ?: continue
            val eqIdx = nameValue.indexOf('=')
            if (eqIdx <= 0) continue
            val name = nameValue.substring(0, eqIdx).trim()
            val value = nameValue.substring(eqIdx + 1).trim().trim('"')
            when (name) {
                "sessionid" -> if (value.isNotBlank()) sessionId = value
                "ds_user_id" -> if (value.isNotBlank()) dsUserId = value
                "rur" -> if (value.isNotBlank()) rur = value
            }
        }

        val resolvedSession = sessionId ?: return null
        return IgSession(
            sessionId = resolvedSession,
            dsUserId = dsUserId,
            rur = rur
        )
    }
}