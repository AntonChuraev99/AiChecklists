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
}

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

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
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
        logger.debug(TAG, "registerUser: deviceId=$deviceId, platform=$platform")

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

        val responseBody = response.body<RegisterUserResponseDto>()
        logger.debug(TAG, "registerUser: response body - success=${responseBody.success}, userId=${responseBody.userId}, aiCredits=${responseBody.aiCredits}, isPremium=${responseBody.isPremium}, error=${responseBody.error}")

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
}

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
