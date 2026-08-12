package dev.animeshvarma.aetherion.network

import dev.animeshvarma.aetherion.util.DeviceFingerprint
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class InstagramCookieJar(private val fp: DeviceFingerprint) : CookieJar {
    private val cookies = mutableMapOf<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
        cookieList.filter { it.name in KEYS }.forEach { c ->
            cookies[c.name] = c
            when (c.name) {
                "mid" -> fp.mid = c.value
                "csrftoken" -> fp.csrfToken = c.value
                "ig_did" -> fp.igDid = c.value
                "sessionid" -> fp.sessionId = c.value
                "ds_user_id" -> fp.dsUserId = c.value
                "rur" -> fp.rur = c.value
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.values.toList()

    companion object {
        private val KEYS = setOf("mid", "csrftoken", "ig_did", "sessionid", "ds_user_id", "rur")
    }
}