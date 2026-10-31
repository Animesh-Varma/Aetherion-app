package dev.animeshvarma.aetherion.network

import dev.animeshvarma.aetherion.util.DeviceFingerprint
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class InstagramCookieJar(private val fp: DeviceFingerprint) : CookieJar {

    private val cookies = mutableMapOf<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
        cookieList.filter { it.name in KEYS }.forEach { c ->
            if (c.value.isNotBlank()) {
                cookies[c.name] = c
                when (c.name) {
                    "mid" -> fp.mid = c.value
                    "csrftoken" -> fp.csrfToken = c.value
                    "ig_did" -> fp.igDid = c.value
                    "sessionid" -> fp.sessionId = c.value
                    "ds_user_id" -> fp.dsUserId = c.value
                    "rur" -> fp.rur = c.value.trim('"')
                }
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        // Drop cookies whose source-of-truth was cleared (logout / fresh login).
        val live = cookies.values.filter { c ->
            when (c.name) {
                "sessionid" -> fp.sessionId.isNotBlank() && fp.sessionId == c.value
                "ds_user_id" -> fp.dsUserId.isNotBlank()
                "ig_did" -> fp.igDid.isNotBlank()
                else -> true
            }
        }.toMutableList()

        if (live.none { it.name == "csrftoken" } && fp.csrfToken.isNotBlank()) {
            runCatching {
                live += Cookie.Builder().domain("instagram.com").path("/")
                    .name("csrftoken").value(fp.csrfToken).build()
            }
        }
        if (live.none { it.name == "mid" } && fp.mid.isNotBlank()) {
            runCatching {
                live += Cookie.Builder().domain("instagram.com").path("/")
                    .name("mid").value(fp.mid).build()
            }
        }
        return live
    }

    companion object {
        private val KEYS = setOf("mid", "csrftoken", "ig_did", "sessionid", "ds_user_id", "rur")
    }
}