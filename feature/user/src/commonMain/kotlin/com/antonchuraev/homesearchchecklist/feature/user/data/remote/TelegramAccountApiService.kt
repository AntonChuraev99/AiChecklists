package com.antonchuraev.homesearchchecklist.feature.user.data.remote

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Link state of the signed-in Google account with the Telegram bot (CF `telegram_account`). */
sealed interface TelegramLinkStatus {
    data object NotLinked : TelegramLinkStatus

    /**
     * @property username Telegram @username without the `@`; null — Telegram users may have none.
     * @property firstName Telegram first name, the fallback label when [username] is null.
     * @property linkedAtMs link time (epoch ms); null on a `claim` reply, which does not carry it.
     * @property reminderChannel `reminder_channel` of a phase-4 server. Null = the field is absent:
     *   the server does not support reminder delivery yet (the screen hides the section). An
     *   unknown value is also null — this build cannot render it.
     * @property timezone `timezone` (IANA) the server holds for the user, null when absent.
     */
    data class Linked(
        val username: String?,
        val firstName: String?,
        val linkedAtMs: Long?,
        val reminderChannel: ReminderChannel? = null,
        val timezone: String? = null,
    ) : TelegramLinkStatus
}

/** One-time app→bot link token. [deepLink] = `https://t.me/<bot>?start=<token>`. */
data class TelegramLinkToken(
    val token: String,
    val deepLink: String,
    val expiresInSec: Int,
)

/** Typed failure of a `telegram_account` call — the UI picks its message by type, never by text. */
sealed interface TelegramAccountError {
    /** The client has no Firebase ID token (not signed in with Google). No request was sent. */
    data object NotSignedIn : TelegramAccountError

    /** 401 / 403 — the server rejected the ID token or the user is disabled. */
    data class Unauthorized(val httpStatus: Int) : TelegramAccountError

    /** 409 `no_account` — the Google account has no credit doc on the server. */
    data object NoAccount : TelegramAccountError

    /** 410 `token_used` / `token_expired` — a claim of a link that is spent. */
    data object TokenExpired : TelegramAccountError

    /** 404 `unknown_token` — a claim of a token the server never issued (or of the wrong direction). */
    data object TokenUnknown : TelegramAccountError

    /** No HTTP response at all: offline, DNS, timeout, TLS. */
    data class Network(val cause: Throwable) : TelegramAccountError

    /** 5xx, an unexpected status, or a 2xx body that does not parse. */
    data class Server(val httpStatus: Int, val code: String?) : TelegramAccountError
}

sealed interface TelegramAccountResult<out T> {
    data class Success<T>(val value: T) : TelegramAccountResult<T>
    data class Failure(val error: TelegramAccountError) : TelegramAccountResult<Nothing>
}

/**
 * HTTP client of the Python CF `telegram_account` (POST, `Authorization: Bearer <Firebase ID token>`,
 * body `{action, ...}`; envelope `{success: true, ...}` / `{success: false, error: <code>}`).
 *
 * Lives in `feature/user` next to [UserApiService] (same CF base URL, same Bearer-token pattern) —
 * composeApp has no ktor dependency of its own. Token and credit-doc id are passed in by the caller,
 * so this layer stays free of auth / DataStore.
 */
interface TelegramAccountApiService {
    suspend fun status(idToken: String): TelegramAccountResult<TelegramLinkStatus>

    suspend fun createToken(idToken: String, userId: String?): TelegramAccountResult<TelegramLinkToken>

    suspend fun claim(idToken: String, token: String, userId: String?): TelegramAccountResult<TelegramLinkStatus.Linked>

    suspend fun unlink(idToken: String): TelegramAccountResult<Unit>

    /**
     * `set_reminder_channel {channel, timezone?}`. [timezone] = the device IANA zone, written in the
     * same request (null = omitted). An invalid zone → 400 [TelegramAccountError.Server] with code
     * [ERROR_INVALID_TIMEZONE]. Success = the channel the server stored.
     */
    suspend fun setReminderChannel(
        idToken: String,
        channel: ReminderChannel,
        timezone: String?,
    ): TelegramAccountResult<ReminderChannel> =
        TelegramAccountResult.Failure(TelegramAccountError.Server(0, "not_implemented"))

    companion object {
        const val ERROR_INVALID_TIMEZONE = "invalid_timezone"
    }
}

class TelegramAccountApiServiceImpl(
    private val logger: AppLogger,
    private val endpointUrl: String = DEFAULT_ENDPOINT_URL,
) : TelegramAccountApiService {

    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
    }

    override suspend fun status(idToken: String): TelegramAccountResult<TelegramLinkStatus> =
        call(ACTION_STATUS, idToken, emptyMap(), ::decodeTelegramStatus)

    override suspend fun createToken(idToken: String, userId: String?): TelegramAccountResult<TelegramLinkToken> =
        call(ACTION_CREATE_TOKEN, idToken, mapOf("user_id" to userId), ::decodeTelegramToken)

    override suspend fun claim(
        idToken: String,
        token: String,
        userId: String?,
    ): TelegramAccountResult<TelegramLinkStatus.Linked> =
        call(ACTION_CLAIM, idToken, mapOf("token" to token, "user_id" to userId), ::decodeTelegramClaim)

    override suspend fun unlink(idToken: String): TelegramAccountResult<Unit> =
        call(ACTION_UNLINK, idToken, emptyMap()) { Unit }

    override suspend fun setReminderChannel(
        idToken: String,
        channel: ReminderChannel,
        timezone: String?,
    ): TelegramAccountResult<ReminderChannel> =
        call(
            ACTION_SET_REMINDER_CHANNEL,
            idToken,
            mapOf("channel" to channel.wire, "timezone" to timezone),
        ) { decodeReminderChannelReply(it, requested = channel) }

    private suspend fun <T> call(
        action: String,
        idToken: String,
        fields: Map<String, String?>,
        decode: (JsonObject) -> T?,
    ): TelegramAccountResult<T> {
        val response: HttpResponse
        val text: String
        try {
            response = httpClient.post(endpointUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $idToken")
                setBody(buildTelegramRequestBody(action, fields))
            }
            text = response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Not logged here: the repository logs every failure once with its context.
            return TelegramAccountResult.Failure(TelegramAccountError.Network(e))
        }
        val result = parseTelegramAccountResponse(response.status.value, text, decode)
        if (result is TelegramAccountResult.Failure) {
            logger.debug(TAG, "$action: HTTP ${response.status.value}, body=${text.take(200)}")
        }
        return result
    }

    companion object {
        private const val TAG = "TelegramAccountApi"
        const val DEFAULT_ENDPOINT_URL = "https://us-central1-aichecklists-40230.cloudfunctions.net/telegram_account"
        internal const val ACTION_STATUS = "status"
        internal const val ACTION_CREATE_TOKEN = "create_token"
        internal const val ACTION_CLAIM = "claim"
        internal const val ACTION_UNLINK = "unlink"
        internal const val ACTION_SET_REMINDER_CHANNEL = "set_reminder_channel"
    }
}

private val telegramJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Request body as sent: `{action, ...}`; null fields are omitted (the server treats them as absent). */
internal fun buildTelegramRequestBody(action: String, fields: Map<String, String?>): String =
    buildJsonObject {
        put("action", action)
        fields.forEach { (key, value) -> if (!value.isNullOrBlank()) put(key, value) }
    }.toString()

/**
 * Maps an HTTP status + body of `telegram_account` to a typed result. Pure, so the whole status/code
 * table is unit-tested without a network stack.
 *
 * The HTTP status is authoritative; the `error` code only splits 410 / 404 shapes and is kept on
 * [TelegramAccountError.Server] for diagnostics. A 2xx with `success: false` or an undecodable body
 * is a server defect, never a silent success.
 */
internal fun <T> parseTelegramAccountResponse(
    httpStatus: Int,
    body: String,
    decode: (JsonObject) -> T?,
): TelegramAccountResult<T> {
    val json = runCatching { telegramJson.parseToJsonElement(body).jsonObject }.getOrNull()
    val code = (json?.get("error") as? JsonPrimitive)?.contentOrNull
    val error: TelegramAccountError = when (httpStatus) {
        in 200..299 -> {
            val success = (json?.get("success") as? JsonPrimitive)?.booleanOrNull
            val decoded = if (json != null && success != false) runCatching { decode(json) }.getOrNull() else null
            return if (decoded != null) {
                TelegramAccountResult.Success(decoded)
            } else {
                TelegramAccountResult.Failure(TelegramAccountError.Server(httpStatus, code ?: "malformed_body"))
            }
        }
        401, 403 -> TelegramAccountError.Unauthorized(httpStatus)
        404 -> TelegramAccountError.TokenUnknown
        409 -> if (code == null || code == "no_account") TelegramAccountError.NoAccount
        else TelegramAccountError.Server(httpStatus, code)
        410 -> TelegramAccountError.TokenExpired
        else -> TelegramAccountError.Server(httpStatus, code)
    }
    return TelegramAccountResult.Failure(error)
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.long(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull

internal fun decodeTelegramStatus(json: JsonObject): TelegramLinkStatus? {
    val linked = (json["linked"] as? JsonPrimitive)?.booleanOrNull ?: return null
    return if (linked) {
        TelegramLinkStatus.Linked(
            username = json.string("telegram_username"),
            firstName = json.string("telegram_first_name"),
            linkedAtMs = json.long("linked_at_ms"),
            reminderChannel = ReminderChannel.fromWire(json.string("reminder_channel")),
            timezone = json.string("timezone"),
        )
    } else {
        TelegramLinkStatus.NotLinked
    }
}

/**
 * `set_reminder_channel` reply. The server echoes the stored `reminder_channel`; when it does not,
 * a `success: true` envelope means [requested] was stored. An echoed value this build does not know
 * is a malformed reply (null → Server failure), never a silent success.
 */
internal fun decodeReminderChannelReply(json: JsonObject, requested: ReminderChannel): ReminderChannel? {
    val echoed = json.string("reminder_channel") ?: return requested
    return ReminderChannel.fromWire(echoed)
}

internal fun decodeTelegramToken(json: JsonObject): TelegramLinkToken? {
    val token = json.string("token") ?: return null
    val deepLink = json.string("deep_link") ?: return null
    val expires = json.long("expires_in_sec")?.toInt()?.takeIf { it > 0 } ?: return null
    return TelegramLinkToken(token = token, deepLink = deepLink, expiresInSec = expires)
}

internal fun decodeTelegramClaim(json: JsonObject): TelegramLinkStatus.Linked? =
    decodeTelegramStatus(json) as? TelegramLinkStatus.Linked
