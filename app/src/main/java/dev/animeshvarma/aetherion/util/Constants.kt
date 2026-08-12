package dev.animeshvarma.aetherion.util

object Constants {
    // API endpoints
    const val BASE_URL = "https://i.instagram.com/api/v1/"

    // Public identifiers of the official Instagram app — identical on every install
    const val FB_ANALYTICS_APP_ID = "567067343352427"
    const val FB_ANALYTICS_CLIENT_TOKEN = "f249176f09e26ce54212b472dbab8fa8"
    const val ACCESS_TOKEN = "$FB_ANALYTICS_APP_ID|$FB_ANALYTICS_CLIENT_TOKEN"
    const val APP_VERSION = "350.0.0.45.107"

    // Public signing key of the app binary + bloks version
    const val IG_SIGNATURE_KEY_HEX =
        "040000001400000003000000474e550054f7abed2248dbcbd8b61a60f41cb908"
    const val IG_SIGNATURE_KEY_VERSION = "4"
    const val SIGNATURE_VERSION_LABEL = "SIGNATURE"
    const val BLOKS_VERSION = "b669eebdb78604327540200c145e9f16d7dc0b2a696478f43517724a88602775"

    // Public capabilities string
    const val X_IG_CAPABILITIES = "3brTvw=="

    // Crypto
    const val KEY_ALIAS = "ig_android_keystore_key"
    const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    // Timing
    const val REQUEST_TIMEOUT_SECONDS = 60L
}