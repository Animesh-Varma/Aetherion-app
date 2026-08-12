package dev.animeshvarma.aetherion.network

import dev.animeshvarma.aetherion.network.models.AttestationResponse
import dev.animeshvarma.aetherion.network.models.BloksResponse
import dev.animeshvarma.aetherion.network.models.LoginResponse
import dev.animeshvarma.aetherion.network.models.MsisdnResponse
import dev.animeshvarma.aetherion.network.models.QeSyncResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Path

interface InstagramApiService {

    // ---- Attestation (used by AttestationHelper) ----
    @FormUrlEncoded
    @POST("attestation/create_android_keystore/")
    suspend fun createAndroidKeystore(
        @Field("app_scoped_device_id") appScopedDeviceId: String,
        @Field("key_hash") keyHash: String
    ): Response<AttestationResponse>

    // ---- Bootstrap: seeds mid/csrftoken/ig_did cookies ----
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

    // ---- Sign-in (legacy; body auto-signed by interceptor) ----
    @FormUrlEncoded
    @POST("accounts/login/")
    suspend fun login(@FieldMap data: Map<String, String>): Response<LoginResponse>

    // ---- 2FA ----
    @FormUrlEncoded
    @POST("accounts/two_factor_login/")
    suspend fun twoFactorLogin(@FieldMap data: Map<String, String>): Response<LoginResponse>

    // ---- Bloks (2FA fallback / app rendering) ----
    @FormUrlEncoded
    @POST("bloks/async_action/{action_name}/")
    suspend fun postBloksAsyncAction(
        @Path("action_name") actionName: String,
        @Field("params") params: String,
        @Field("bk_client_context") bkClientContext: String,
        @Field("bloks_versioning_id") bloksVersioningId: String
    ): Response<BloksResponse>

    @FormUrlEncoded
    @POST("bloks/apps/{app_id}/")
    suspend fun getBloksApp(
        @Path("app_id") appId: String,
        @Field("params") params: String,
        @Field("bk_client_context") bkClientContext: String,
        @Field("bloks_versioning_id") bloksVersioningId: String
    ): Response<BloksResponse>
}