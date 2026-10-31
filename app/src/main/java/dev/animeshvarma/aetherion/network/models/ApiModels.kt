package dev.animeshvarma.aetherion.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---- Attestation (Step A1 / B1) ----
@Serializable
data class AttestationResponse(
    val challenge_nonce: String? = null,
    val status: String? = null
)

// ---- Bootstrap ----
@Serializable
data class MsisdnResponse(val status: String? = null)

@Serializable
data class QeSyncResponse(val status: String? = null, val configs: JsonElement? = null)

// ---- Login & 2FA ----
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
    val full_name: String? = null,
    val profile_pic_url: String? = null,
    val is_private: Boolean? = null,
    val is_verified: Boolean? = null
)

@Serializable
data class TwoFactorInfo(
    val two_factor_identifier: String? = null,
    val username: String? = null,
    val totp_two_factor_on: Boolean? = null,
    val sms_two_factor_on: Boolean? = null,
    val obfuscated_phone_number: String? = null
)

@Serializable
data class ChallengeInfo(val url: String? = null, val api_path: String? = null)

// ---- Bloks Flexible Response (Steps A2–A8, B2) ----
@Serializable
data class BloksResponse(
    val layout: JsonElement? = null,
    val status: String? = null,
    val error_type: String? = null,
    val message: String? = null,
    val two_step_verification_context: String? = null,
    val challenge_context: String? = null
)

// ---- Account Family (Step A13 / B3) ----
@Serializable
data class AccountFamilyResponse(
    val status: String? = null,
    val current_account: AccountFamilyEntry? = null,
    val main_accounts: List<AccountFamilyEntry>? = null,
    val child_accounts: List<AccountFamilyEntry>? = null
)

@Serializable
data class AccountFamilyEntry(
    val user_id: String? = null,
    val pk: String? = null,
    val username: String? = null,
    val full_name: String? = null,
    val profile_pic_url: String? = null,
    val is_2fa_enabled: Boolean? = null
)

// ---- Profile Info & Info Stream (Steps A14–A16, B4–B6) ----
@Serializable
data class UserProfileResponse(
    val user: ProfileUserDetail? = null,
    val status: String? = null
)

@Serializable
data class ProfileUserDetail(
    val pk: Long? = null,
    val pk_id: String? = null,
    val id: String? = null,
    val username: String? = null,
    val full_name: String? = null,
    val biography: String? = null,
    val external_url: String? = null,
    val profile_pic_url: String? = null,
    val hd_profile_pic_url_info: HdPicInfo? = null,
    val media_count: Int? = null,
    val follower_count: Int? = null,
    val following_count: Int? = null,
    val is_private: Boolean? = null,
    val is_verified: Boolean? = null,
    val is_business: Boolean? = null,
    val category: String? = null,
    val pronouns: List<String>? = null,
    val bio_links: List<BioLinkItem>? = null,
    val total_igtv_videos: Int? = null,
    val total_clips_count: Int? = null
) {
    val resolvedId: String
        get() = pk_id ?: pk?.toString() ?: id ?: ""

    val bestAvatarUrl: String
        get() = hd_profile_pic_url_info?.url?.takeIf { it.isNotBlank() }
            ?: profile_pic_url.orEmpty()
}

@Serializable
data class HdPicInfo(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class BioLinkItem(
    val link_id: String? = null,
    val url: String? = null,
    val lynx_url: String? = null,
    val link_type: String? = null,
    val title: String? = null
)

// ---- Story Highlights Tray (Step A17 / B7) ----
@Serializable
data class HighlightsTrayResponse(
    val tray: List<HighlightReelItem>? = null,
    val status: String? = null
)

@Serializable
data class HighlightReelItem(
    val id: String? = null,
    val title: String? = null,
    val media_count: Int? = null,
    val cover_media: HighlightCoverMedia? = null
)

@Serializable
data class HighlightCoverMedia(
    val cropped_image_version: ImageCandidate? = null,
    val full_image_version: ImageCandidate? = null
)

// ---- Text Feed (Step A18 / B8) ----
@Serializable
data class TextFeedResponse(
    val count: Int? = null,
    val status: String? = null
)

// ---- User Media Posts Grid ----
@Serializable
data class UserFeedResponse(
    val items: List<MediaFeedItem>? = null,
    val num_results: Int? = null,
    val more_available: Boolean? = null,
    val next_max_id: String? = null,
    val status: String? = null
)

@Serializable
data class MediaFeedItem(
    val pk: Long? = null,
    val id: String? = null,
    val code: String? = null,
    val media_type: Int? = null, // 1 = Photo, 2 = Video/Reel, 8 = Carousel
    val taken_at: Long? = null,
    val like_count: Int? = null,
    val comment_count: Int? = null,
    val view_count: Int? = null,
    val play_count: Int? = null,
    val caption: MediaCaption? = null,
    val image_versions2: ImageVersions2? = null,
    val carousel_media: List<CarouselMediaItem>? = null
) {
    val thumbnailUrl: String
        get() = image_versions2?.candidates?.firstOrNull()?.url
            ?: carousel_media?.firstOrNull()?.image_versions2?.candidates?.firstOrNull()?.url
            ?: ""
}

@Serializable
data class CarouselMediaItem(
    val id: String? = null,
    val media_type: Int? = null,
    val image_versions2: ImageVersions2? = null
)

@Serializable
data class MediaCaption(
    val text: String? = null,
    val created_at: Long? = null
)

@Serializable
data class ImageVersions2(
    val candidates: List<ImageCandidate>? = null
)

@Serializable
data class ImageCandidate(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

// ---- Reels Tray & Timeline (Steps A10–A12) ----
@Serializable
data class ReelsTrayResponse(
    val tray: List<ReelTrayEntry>? = null,
    val status: String? = null
)

@Serializable
data class ReelTrayEntry(
    val id: JsonElement? = null
)

// ---- Aggregated Domain Model for Profile UI ----
data class OwnProfileData(
    val userId: String,
    val userIdB64: String,
    val username: String,
    val fullName: String,
    val biography: String,
    val avatarUrl: String,
    val mediaCount: Int,
    val followerCount: Int,
    val followingCount: Int,
    val isPrivate: Boolean,
    val isVerified: Boolean,
    val is2faEnabled: Boolean,
    val category: String?,
    val pronouns: List<String>,
    val externalUrl: String?,
    val bioLinks: List<BioLinkItem>,
    val highlights: List<HighlightReelItem>,
    val posts: List<MediaFeedItem>,
    val textFeedCount: Int,
    val executedFlowName: String,
    val executedSteps: List<String>
)