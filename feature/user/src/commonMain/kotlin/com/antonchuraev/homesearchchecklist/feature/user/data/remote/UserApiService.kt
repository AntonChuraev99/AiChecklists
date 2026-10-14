package com.antonchuraev.homesearchchecklist.feature.user.data.remote

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Result of user registration.
 */
data class RegisterUserResult(
    val userId: String,
    val isNewUser: Boolean,
    val isPremium: Boolean,
    val aiCredits: Int,
    val createdAt: String
)

/**
 * Result of credits restoration after purchase.
 */
data class RestoreCreditsResult(
    val aiCredits: Int,
    val isPremium: Boolean,
    val message: String
)

/**
 * Service for user-related API calls.
 */
interface UserApiService {
    /**
     * Register a new user or retrieve existing user by device ID.
     *
     * @param deviceId Unique device identifier
     * @param appVersion Optional app version for analytics
     * @param platform Optional platform identifier (android/ios)
     * @return Result with user data or error
     */
    suspend fun registerUser(
        deviceId: String,
        appVersion: String? = null,
        platform: String? = null
    ): Result<RegisterUserResult>

    /**
     * Restore credits after premium purchase.
     *
     * This should be called immediately after successful subscription purchase
     * or restore to give user their premium credits instantly.
     *
     * @param userId User's unique identifier
     * @return Result with restored credits info or error
     */
    suspend fun restoreCreditsAfterPurchase(userId: String): Result<RestoreCreditsResult>

    suspend fun linkGoogleAccount(
        userId: String,
        idToken: String,
        platform: String,
    ): Result<LinkGoogleAccountApiResult>

    /**
     * Register / refresh the FCM push token on the user's server-side credit doc
     * (`users/{userId}`) via the Admin-SDK Cloud Function.
     *
     * The client CANNOT write this doc directly: Firestore rules make `users/{userId}`
     * server-write-only, and anonymous users have no Firebase Auth at all. Routing token
     * registration through the CF works uniformly for BOTH anonymous and signed-in users
     * and co-locates the token with `is_premium` / `pushHoldout` so the promo sender can
     * suppress premium/holdout on the same document.
     *
     * @param userId canonical device-registration user_id (the credit-doc id)
     * @param token FCM registration token (never blank — a blank token would clobber the stored one)
     * @param platform "android"
     * @param pushHoldout deterministic retention control-group flag (client-owned)
     * @param fcmOptIn explicit promo opt-in
     * @param markActive `mark_active`: true moves `lastActiveAt`. Only a visible foreground passes
     *   true; background registrations (`onNewToken`, token retry, process start) pass false so a
     *   process woken by the promo itself does not count as the user reacting to it.
     * @return Result.success(Unit) on a successful server write.
     *
     * Default no-op success keeps commonTest fakes compiling (only the real impl hits the network).
     */
    suspend fun registerPushToken(
        userId: String,
        token: String,
        platform: String,
        pushHoldout: Boolean,
        fcmOptIn: Boolean,
        markActive: Boolean,
    ): Result<Unit> = Result.success(Unit)

    /**
     * Activity-only heartbeat: refresh `lastActiveAt` on `users/{userId}` WITHOUT a push token,
     * through the same CF `register_push_token` (body sent with `fcm_token: null`). The server
     * merges ONLY `lastActiveAt` and leaves `fcmToken` / `pushHoldout` / `fcmOptIn` / `platform`
     * untouched, so this can never clobber a token another install of the same user registered.
     *
     * For a device whose FCM token fetch fails (`FIS_AUTH_ERROR`): without it the promo sender
     * would treat an active user as dormant. Always `mark_active = true`, so call it only from a
     * visible foreground.
     *
     * Default no-op success keeps commonTest fakes compiling (only the real impl hits the network).
     */
    suspend fun markUserActive(
        userId: String,
        platform: String,
    ): Result<Unit> = Result.success(Unit)
}

data class LinkGoogleAccountApiResult(
    val userId: String,
    val googleEmail: String,
    val isExistingAccount: Boolean,
    val aiCredits: Int,
    val isPremium: Boolean,
    val bonusCreditsGranted: Int,
)

/**
 * Implementation of UserApiService using Ktor HTTP client.
 */
class UserApiServiceImpl(
    private val logger: AppLogger,
    private val baseUrl: String = DEFAULT_BASE_URL
) : UserApiService {

    companion object {
        private const val TAG = "UserApiService"
        private const val DEFAULT_BASE_URL = "https://us-central1-aichecklists-40230.cloudfunctions.net"
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(userApiJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000 // 1 minute for user operations
            connectTimeoutMillis = 30_000 // 30 seconds to connect
            socketTimeoutMillis = 60_000  // 1 minute socket timeout
        }
    }

    override suspend fun registerUser(
        deviceId: String,
        appVersion: String?,
        platform: String?
    ): Result<RegisterUserResult> = runCatching {
        logger.debug(TAG, "registerUser: deviceId=${deviceId.take(8)}..., platform=$platform")

        val request = RegisterUserRequest(
            deviceId = deviceId,
            appVersion = appVersion,
            platform = platform
        )

        logger.debug(TAG, "registerUser: calling $baseUrl/register_user")
        val response: HttpResponse = httpClient.post("$baseUrl/register_user") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        logger.debug(TAG, "registerUser: response status=${response.status}")

        // Guard non-2xx BEFORE deserializing: an error response often carries a
        // non-JSON body (HTML/plain-text error page), and response.body<Dto>() then
        // throws a cryptic NoTransformationFoundException (crashes 8695b359) instead of
        // a meaningful failure. Surface the HTTP status as a clean, self-healing error.
        if (!response.status.isSuccess()) {
            val raw = runCatching { response.bodyAsText() }.getOrNull()?.take(200)
            logger.warning(TAG, "registerUser: HTTP ${response.status.value}, body=$raw")
            throw Exception("register_user failed: HTTP ${response.status.value}")
        }
        val responseBody = response.body<RegisterUserResponseDto>()
        logger.debug(TAG, "registerUser: response body - success=${responseBody.success}, userId=${responseBody.userId?.take(8)}..., aiCredits=${responseBody.aiCredits}, isPremium=${responseBody.isPremium}, error=${responseBody.error}")

        if (responseBody.success && responseBody.userId != null) {
            val result = RegisterUserResult(
                userId = responseBody.userId,
                isNewUser = responseBody.isNewUser ?: true,
                isPremium = responseBody.isPremium ?: false,
                aiCredits = responseBody.aiCredits ?: 0,
                createdAt = responseBody.createdAt ?: ""
            )
            logger.info(TAG, "registerUser: SUCCESS - aiCredits=${result.aiCredits}")
            result
        } else {
            logger.error(TAG, "registerUser: FAILED - ${responseBody.error}")
            throw Exception(responseBody.error ?: "Failed to register user")
        }
    }

    override suspend fun linkGoogleAccount(
        userId: String,
        idToken: String,
        platform: String,
    ): Result<LinkGoogleAccountApiResult> = runCatching {
        logger.debug(TAG, "linkGoogleAccount: userId=${userId.take(8)}..., platform=$platform")

        val response: HttpResponse = httpClient.post("$baseUrl/link_google_account") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $idToken")
            setBody(LinkGoogleAccountRequest(userId = userId, platform = platform))
        }

        val body = response.body<LinkGoogleAccountResponseDto>()
        if (body.success) {
            LinkGoogleAccountApiResult(
                userId = body.userId ?: userId,
                googleEmail = body.googleEmail ?: "",
                isExistingAccount = body.isExistingAccount ?: false,
                aiCredits = body.aiCredits ?: 0,
                isPremium = body.isPremium ?: false,
                bonusCreditsGranted = body.bonusCreditsGranted ?: 0,
            )
        } else {
            throw Exception(body.error ?: "Failed to link Google account")
        }
    }

    override suspend fun restoreCreditsAfterPurchase(userId: String): Result<RestoreCreditsResult> = runCatching {
        logger.debug(TAG, "restoreCreditsAfterPurchase: userId=${userId.take(8)}...")

        val request = RestoreCreditsRequest(userId = userId)

        logger.debug(TAG, "restoreCreditsAfterPurchase: calling $baseUrl/restore_credits_after_purchase")
        val response: HttpResponse = httpClient.post("$baseUrl/restore_credits_after_purchase") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        logger.debug(TAG, "restoreCreditsAfterPurchase: response status=${response.status}")

        val responseBody = response.body<RestoreCreditsResponseDto>()
        logger.debug(TAG, "restoreCreditsAfterPurchase: response body - success=${responseBody.success}, aiCredits=${responseBody.aiCredits}, error=${responseBody.error}")

        if (responseBody.success) {
            val result = RestoreCreditsResult(
                aiCredits = responseBody.aiCredits ?: 0,
                isPremium = responseBody.isPremium ?: true,
                message = responseBody.message ?: "Credits restored"
            )
            logger.info(TAG, "restoreCreditsAfterPurchase: SUCCESS - aiCredits=${result.aiCredits}")
            result
        } else {
            logger.error(TAG, "restoreCreditsAfterPurchase: FAILED - ${responseBody.error}")
            throw Exception(responseBody.error ?: "Failed to restore credits")
        }
    }

    override suspend fun registerPushToken(
        userId: String,
        token: String,
        platform: String,
        pushHoldout: Boolean,
        fcmOptIn: Boolean,
        markActive: Boolean,
    ): Result<Unit> = runCatching {
        logger.debug(
            TAG,
            "registerPushToken: userId=${userId.take(8)}..., platform=$platform, holdout=$pushHoldout, markActive=$markActive",
        )
        postRegisterPushToken(
            op = "registerPushToken",
            request = buildRegisterPushTokenRequest(userId, token, platform, markActive, pushHoldout, fcmOptIn),
        )
    }

    override suspend fun markUserActive(
        userId: String,
        platform: String,
    ): Result<Unit> = runCatching {
        logger.debug(TAG, "markUserActive: userId=${userId.take(8)}..., platform=$platform")
        postRegisterPushToken(
            op = "markUserActive",
            request = buildRegisterPushTokenRequest(userId, token = null, platform = platform, markActive = true),
        )
    }

    private suspend fun postRegisterPushToken(op: String, request: RegisterPushTokenRequest) {
        val response: HttpResponse = httpClient.post("$baseUrl/register_push_token") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        // Same non-2xx guard as registerUser: avoid NoTransformationFoundException
        // (crashes ddc8be1f) when the error body isn't JSON.
        if (!response.status.isSuccess()) {
            val raw = runCatching { response.bodyAsText() }.getOrNull()?.take(200)
            logger.warning(TAG, "$op: HTTP ${response.status.value}, body=$raw")
            throw Exception("register_push_token failed: HTTP ${response.status.value}")
        }
        val responseBody = response.body<RegisterPushTokenResponseDto>()
        if (responseBody.success) {
            logger.debug(TAG, "$op: SUCCESS, token_registered=${responseBody.tokenRegistered}")
        } else {
            logger.error(TAG, "$op: FAILED - ${responseBody.error}")
            throw Exception(responseBody.error ?: "Failed to register push token")
        }
    }
}

/** Wire config of every UserApiService request; internal so tests encode bodies exactly as sent. */
internal val userApiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/**
 * Body of CF `register_push_token`. A null [token] is the activity-only heartbeat: the server
 * writes only `lastActiveAt`. The holdout / opt-in flags are then forced to `false` rather than
 * null — the server ignores them on that path, but its validator still rejects a non-boolean
 * value, and the shared Json config (`encodeDefaults = true`) would send a null as `null`.
 *
 * [markActive] is `mark_active` (server default true): only a visible foreground passes true. A
 * null [token] always sends true — the activity-only write has nothing else to do, and false there
 * would be a request that writes nothing.
 */
internal fun buildRegisterPushTokenRequest(
    userId: String,
    token: String?,
    platform: String,
    markActive: Boolean,
    pushHoldout: Boolean = false,
    fcmOptIn: Boolean = false,
): RegisterPushTokenRequest = RegisterPushTokenRequest(
    userId = userId,
    fcmToken = token,
    platform = platform,
    pushHoldout = token != null && pushHoldout,
    fcmOptIn = token != null && fcmOptIn,
    markActive = token == null || markActive,
)

@Serializable
private data class RegisterUserRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("app_version") val appVersion: String? = null,
    val platform: String? = null
)

@Serializable
private data class RegisterUserResponseDto(
    val success: Boolean,
    val error: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("is_new_user") val isNewUser: Boolean? = null,
    @SerialName("is_premium") val isPremium: Boolean? = null,
    @SerialName("ai_credits") val aiCredits: Int? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class RestoreCreditsRequest(
    @SerialName("user_id") val userId: String
)

@Serializable
private data class RestoreCreditsResponseDto(
    val success: Boolean,
    val error: String? = null,
    @SerialName("ai_credits") val aiCredits: Int? = null,
    @SerialName("is_premium") val isPremium: Boolean? = null,
    val message: String? = null
)

@Serializable
private data class LinkGoogleAccountRequest(
    @SerialName("user_id") val userId: String,
    val platform: String,
)

@Serializable
private data class LinkGoogleAccountResponseDto(
    val success: Boolean,
    val error: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("google_email") val googleEmail: String? = null,
    @SerialName("is_existing_account") val isExistingAccount: Boolean? = null,
    @SerialName("ai_credits") val aiCredits: Int? = null,
    @SerialName("is_premium") val isPremium: Boolean? = null,
    @SerialName("bonus_credits_granted") val bonusCreditsGranted: Int? = null,
)

@Serializable
internal data class RegisterPushTokenRequest(
    @SerialName("user_id") val userId: String,
    /** null = activity-only heartbeat (server writes only `lastActiveAt`). */
    @SerialName("fcm_token") val fcmToken: String?,
    val platform: String,
    @SerialName("push_holdout") val pushHoldout: Boolean,
    @SerialName("fcm_opt_in") val fcmOptIn: Boolean,
    /** false = register the token without moving `lastActiveAt` (background callers). */
    @SerialName("mark_active") val markActive: Boolean,
)

@Serializable
private data class RegisterPushTokenResponseDto(
    val success: Boolean,
    val error: String? = null,
    /** false on the activity-only path; absent from servers older than the token-less contract. */
    @SerialName("token_registered") val tokenRegistered: Boolean? = null,
)
