package dev.animeshvarma.aetherion.network.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---- Login ----
@Serializable
data class MsisdnResponse(val status: String? = null)

@Serializable
data class QeSyncResponse(val status: String? = null, val configs: JsonElement? = null)

@Serializable
data class LoginResponse(
    val status: String? = null,
    val logged_in_user: UserInfo? = null,
    val two_factor_required: Boolean? = null,
    val two_factor_info: TwoFactorInfo? = null,
    val two_step_verification_context: String? = null,
    val message: String? = null,
    val error_type: String? = null,
    val invalid_credentials: Boolean? = null,
    val error_title: String? = null,
    val challenge: ChallengeInfo? = null
)

@Serializable
data class UserInfo(
    val pk: Long? = null,
    val pk_id: String? = null,
    val username: String? = null,
    val full_name: String? = null
)

@Serializable
data class TwoFactorInfo(
    val two_factor_identifier: String? = null,
    val username: String? = null
)

@Serializable
data class ChallengeInfo(val url: String? = null, val api_path: String? = null)

// ---- Attestation (restored — used by AttestationHelper) ----
@Serializable
data class AttestationResponse(
    val challenge_nonce: String,
    val status: String
)

// ---- Bloks (restored — used by the service / 2FA fallback) ----
@Serializable
data class BloksResponse(
    val layout: BloksLayout?,
    val status: String
)

@Serializable
data class BloksLayout(
    val bloks_payload: BloksPayload?
)

@Serializable
data class BloksPayload(
    val action: String?
)