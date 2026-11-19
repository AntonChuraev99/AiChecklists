package com.antonchuraev.homesearchchecklist.feature.user.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** Status/code table of CF `telegram_account` → typed results (server contract 2026-11-19). */
class TelegramAccountResponseTest {

    private fun status(code: Int, body: String) = parseTelegramAccountResponse(code, body, ::decodeTelegramStatus)

    @Test
    fun status_linked_withAllFields() {
        val result = status(
            200,
            """{"success":true,"linked":true,"telegram_username":"anton","telegram_first_name":"Anton","linked_at_ms":1763550000000}""",
        )
        assertEquals(
            TelegramAccountResult.Success(TelegramLinkStatus.Linked("anton", "Anton", 1763550000000L)),
            result,
        )
    }

    @Test
    fun status_linked_nullUsername_keepsFirstName() {
        val result = status(200, """{"success":true,"linked":true,"telegram_username":null,"telegram_first_name":"Anton","linked_at_ms":5}""")
        assertEquals(TelegramAccountResult.Success(TelegramLinkStatus.Linked(null, "Anton", 5L)), result)
    }

    @Test
    fun status_notLinked() {
        assertEquals(TelegramAccountResult.Success(TelegramLinkStatus.NotLinked), status(200, """{"success":true,"linked":false}"""))
    }

    @Test
    fun success200_withSuccessFalse_isServerError_notSilentSuccess() {
        val result = status(200, """{"success":false,"linked":false,"error":"internal"}""")
        assertEquals(TelegramAccountResult.Failure(TelegramAccountError.Server(200, "internal")), result)
    }

    @Test
    fun success200_withUndecodableBody_isServerError() {
        val result = status(200, "<html>oops</html>")
        assertEquals(TelegramAccountResult.Failure(TelegramAccountError.Server(200, "malformed_body")), result)
    }

    @Test
    fun errorTable() {
        assertEquals(
            TelegramAccountResult.Failure(TelegramAccountError.NoAccount),
            status(409, """{"success":false,"error":"no_account"}"""),
        )
        assertEquals(
            TelegramAccountResult.Failure(TelegramAccountError.TokenExpired),
            status(410, """{"success":false,"error":"token_used"}"""),
        )
        assertEquals(
            TelegramAccountResult.Failure(TelegramAccountError.TokenExpired),
            status(410, """{"success":false,"error":"token_expired"}"""),
        )
        assertEquals(
            TelegramAccountResult.Failure(TelegramAccountError.TokenUnknown),
            status(404, """{"success":false,"error":"unknown_token"}"""),
        )
        assertEquals(
            TelegramAccountResult.Failure(TelegramAccountError.Unauthorized(401)),
            status(401, """{"success":false,"error":"invalid_token"}"""),
        )
        assertEquals(
            TelegramAccountResult.Failure(TelegramAccountError.Unauthorized(403)),
            status(403, """{"success":false,"error":"user_disabled"}"""),
        )
        assertEquals(
            TelegramAccountResult.Failure(TelegramAccountError.Server(500, "internal")),
            status(500, """{"success":false,"error":"internal"}"""),
        )
        // A proxy error page without JSON still maps by status.
        assertEquals(TelegramAccountResult.Failure(TelegramAccountError.Server(502, null)), status(502, "Bad Gateway"))
    }

    @Test
    fun createToken_decodes_and_rejectsMissingDeepLink() {
        val ok = parseTelegramAccountResponse(
            200,
            """{"success":true,"token":"abc","deep_link":"https://t.me/gisti_app_bot?start=abc","expires_in_sec":600}""",
            ::decodeTelegramToken,
        )
        assertEquals(
            TelegramAccountResult.Success(TelegramLinkToken("abc", "https://t.me/gisti_app_bot?start=abc", 600)),
            ok,
        )
        val broken = parseTelegramAccountResponse(200, """{"success":true,"token":"abc","expires_in_sec":600}""", ::decodeTelegramToken)
        assertIs<TelegramAccountResult.Failure>(broken)
    }

    @Test
    fun claim_decodesLinkedWithoutLinkedAt() {
        val result = parseTelegramAccountResponse(
            200,
            """{"success":true,"linked":true,"telegram_username":"anton","telegram_first_name":null}""",
            ::decodeTelegramClaim,
        )
        assertEquals(TelegramAccountResult.Success(TelegramLinkStatus.Linked("anton", null, null)), result)
    }

    @Test
    fun requestBody_carriesAction_andOmitsNullOrBlankFields() {
        val body = Json.parseToJsonElement(
            buildTelegramRequestBody("claim", mapOf("token" to "t1", "user_id" to null, "x" to " ")),
        ).jsonObject
        assertEquals("claim", body["action"]?.jsonPrimitive?.content)
        assertEquals("t1", body["token"]?.jsonPrimitive?.content)
        assertFalse("user_id" in body)
        assertFalse("x" in body)
    }
}
