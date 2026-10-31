package dev.animeshvarma.aetherion.network

import dev.animeshvarma.aetherion.network.models.AccountFamilyResponse
import dev.animeshvarma.aetherion.network.models.AttestationResponse
import dev.animeshvarma.aetherion.network.models.HighlightsTrayResponse
import dev.animeshvarma.aetherion.network.models.MsisdnResponse
import dev.animeshvarma.aetherion.network.models.QeSyncResponse
import dev.animeshvarma.aetherion.network.models.ReelsTrayResponse
import dev.animeshvarma.aetherion.network.models.TextFeedResponse
import dev.animeshvarma.aetherion.network.models.UserFeedResponse
import dev.animeshvarma.aetherion.network.models.UserProfileResponse
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface InstagramApiService {

    // ---- Keystore attestation ----
    @FormUrlEncoded
    @POST("attestation/create_android_keystore/")
    suspend fun createAndroidKeystore(
        @HeaderMap extraHeaders: Map<String, String>,
        @Field("app_scoped_device_id") appScopedDeviceId: String,
        @Field("key_hash") keyHash: String
    ): Response<AttestationResponse>

    // ---- Bootstrap ----
    @FormUrlEncoded
    @POST("accounts/read_msisdn_header/")
    suspend fun readMsisdnHeader(
        @Field("device_id") deviceId: String,
        @Field("mobile_subno_usage") usage: String = "ig_select_app"
    ): Response<MsisdnResponse>

    @FormUrlEncoded
    @POST("qe/sync/")
    suspend fun qeSync(
        @Field("id") id: String,
        @Field("server_config_retrieval") config: String = "1"
    ): Response<QeSyncResponse>

    @FormUrlEncoded
    @POST("launcher/sync/")
    suspend fun launcherSync(
        @Field("id") id: String,
        @Field("server_config_retrieval") config: String = "1"
    ): Response<QeSyncResponse>

    // ---- legacy private-API login (signed_body) ----
    @FormUrlEncoded
    @POST("accounts/login/")
    suspend fun loginSigned(
        @HeaderMap extraHeaders: Map<String, String>,
        @FieldMap fields: Map<String, String>
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("accounts/two_factor_login/")
    suspend fun twoFactorSigned(
        @FieldMap fields: Map<String, String>
    ): Response<ResponseBody>

    @GET("accounts/current_user/")
    suspend fun currentUserRaw(
        @Query("edit") edit: String = "true"
    ): Response<ResponseBody>

    // ---- Bloks async actions ----
    @FormUrlEncoded
    @POST("bloks/async_action/{action_name}/")
    suspend fun postBloksAsyncAction(
        @Path(value = "action_name", encoded = true) actionName: String,
        @HeaderMap extraHeaders: Map<String, String>,
        @Field("params") params: String,
        @Field("bk_client_context") bkClientContext: String,
        @Field("bloks_versioning_id") bloksVersioningId: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST
    suspend fun postBloksAsyncActionByUrl(
        @Url fullUrl: String,
        @HeaderMap extraHeaders: Map<String, String>,
        @Field("params") params: String,
        @Field("bk_client_context") bkClientContext: String,
        @Field("bloks_versioning_id") bloksVersioningId: String
    ): Response<ResponseBody>

    // ---- Bloks apps ----
    @FormUrlEncoded
    @POST("bloks/apps/{app_id}/")
    suspend fun getBloksApp(
        @Path(value = "app_id", encoded = true) appId: String,
        @HeaderMap extraHeaders: Map<String, String>,
        @Field("params") params: String,
        @Field("bk_client_context") bkClientContext: String,
        @Field("bloks_versioning_id") bloksVersioningId: String
    ): Response<ResponseBody>

    // ---- GraphQL ----
    @FormUrlEncoded
    @POST
    suspend fun postGraphQlQuery(
        @Url url: String,
        @HeaderMap extraHeaders: Map<String, String>,
        @FieldMap fields: Map<String, String>
    ): Response<JsonElement>

    // ---- Feed / reels ----
    @FormUrlEncoded
    @POST("feed/timeline/")
    suspend fun postFeedTimeline(
        @HeaderMap extraHeaders: Map<String, String>,
        @FieldMap fields: Map<String, String>
    ): Response<JsonElement>

    @FormUrlEncoded
    @POST("feed/reels_tray/")
    suspend fun postReelsTray(
        @Field("_uuid") uuid: String
    ): Response<ReelsTrayResponse>

    @FormUrlEncoded
    @POST("feed/reels_media_stream/")
    suspend fun postReelsMediaStream(
        @Field("reel_ids") reelIdsJson: String
    ): Response<JsonElement>

    // ---- Account / profile ----
    @GET("multiple_accounts/get_account_family/")
    suspend fun getAccountFamily(): Response<AccountFamilyResponse>

    @GET("users/{user_id}/info/")
    suspend fun getUserInfo(
        @Path("user_id") userId: String
    ): Response<UserProfileResponse>

    @FormUrlEncoded
    @POST("users/{user_id}/info_stream/")
    suspend fun postUserInfoStream(
        @Path("user_id") userId: String,
        @Field("entry_point") entryPoint: String = "self_profile",
        @Field("from_module") fromModule: String = "self_profile",
        @Field("_uuid") uuid: String
    ): Response<ResponseBody>

    @GET("users/{user_id_b64}/")
    suspend fun getUserByBase64(
        @Path(value = "user_id_b64", encoded = true) userIdB64: String
    ): Response<ResponseBody>

    @GET("highlights/{user_id}/highlights_tray/")
    suspend fun getHighlightsTray(
        @Path("user_id") userId: String
    ): Response<HighlightsTrayResponse>

    @GET("text_feed/{user_id_b64}/")
    suspend fun getTextFeed(
        @Path(value = "user_id_b64", encoded = true) userIdB64: String
    ): Response<TextFeedResponse>

    @GET("feed/user/{user_id}/")
    suspend fun getUserFeed(
        @Path("user_id") userId: String
    ): Response<UserFeedResponse>
}