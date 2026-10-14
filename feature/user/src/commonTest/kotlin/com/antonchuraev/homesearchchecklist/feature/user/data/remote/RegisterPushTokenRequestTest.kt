package com.antonchuraev.homesearchchecklist.feature.user.data.remote

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Wire contract of CF `register_push_token`, encoded with the same Json config the HTTP client uses.
 *
 * Server side (`plan_push_registration` in firebase-functions/main.py): `fcm_token` null = activity-only
 * heartbeat (only `lastActiveAt` is written); `push_holdout` / `fcm_opt_in` must be booleans when present,
 * a `null` there is a 400 even on the activity-only path. `mark_active` (default true server-side) decides
 * whether `lastActiveAt` moves — the client always sends it explicitly, so an older default can never
 * turn a background registration into "the user was active".
 */
class RegisterPushTokenRequestTest {

    private fun encode(request: RegisterPushTokenRequest): JsonObject =
        userApiJson.encodeToJsonElement(RegisterPushTokenRequest.serializer(), request).jsonObject

    @Test
    fun activityOnly_sendsNullToken_booleanFlags_andMarkActive() {
        val body = encode(buildRegisterPushTokenRequest(USER_ID, token = null, platform = "android", markActive = true))

        assertEquals(
            JsonObject(
                mapOf(
                    "user_id" to JsonPrimitive(USER_ID),
                    "fcm_token" to JsonNull,
                    "platform" to JsonPrimitive("android"),
                    "push_holdout" to JsonPrimitive(false),
                    "fcm_opt_in" to JsonPrimitive(false),
                    "mark_active" to JsonPrimitive(true),
                )
            ),
            body,
        )
    }

    @Test
    fun activityOnly_alwaysMarksActive_evenIfCallerPassesFalse() {
        val body = encode(buildRegisterPushTokenRequest(USER_ID, token = null, platform = "android", markActive = false))

        assertEquals(JsonPrimitive(true), body["mark_active"])
    }

    @Test
    fun activityOnly_dropsCallerFlags_soServerNeverSeesOptInWithoutToken() {
        val body = encode(
            buildRegisterPushTokenRequest(
                USER_ID,
                token = null,
                platform = "android",
                markActive = true,
                pushHoldout = true,
                fcmOptIn = true,
            )
        )

        assertEquals(JsonPrimitive(false), body["push_holdout"])
        assertEquals(JsonPrimitive(false), body["fcm_opt_in"])
    }

    @Test
    fun foregroundWithToken_sendsTokenCallerFlags_andMarkActiveTrue() {
        val body = encode(
            buildRegisterPushTokenRequest(
                USER_ID,
                token = "tok-123",
                platform = "android",
                markActive = true,
                pushHoldout = true,
                fcmOptIn = true,
            )
        )

        assertEquals(
            JsonObject(
                mapOf(
                    "user_id" to JsonPrimitive(USER_ID),
                    "fcm_token" to JsonPrimitive("tok-123"),
                    "platform" to JsonPrimitive("android"),
                    "push_holdout" to JsonPrimitive(true),
                    "fcm_opt_in" to JsonPrimitive(true),
                    "mark_active" to JsonPrimitive(true),
                )
            ),
            body,
        )
    }

    @Test
    fun backgroundWithToken_sendsMarkActiveFalse() {
        val body = encode(
            buildRegisterPushTokenRequest(
                USER_ID,
                token = "tok-123",
                platform = "android",
                markActive = false,
                pushHoldout = false,
                fcmOptIn = true,
            )
        )

        assertEquals(
            JsonObject(
                mapOf(
                    "user_id" to JsonPrimitive(USER_ID),
                    "fcm_token" to JsonPrimitive("tok-123"),
                    "platform" to JsonPrimitive("android"),
                    "push_holdout" to JsonPrimitive(false),
                    "fcm_opt_in" to JsonPrimitive(true),
                    "mark_active" to JsonPrimitive(false),
                )
            ),
            body,
        )
    }

    private companion object {
        const val USER_ID = "user-0123456789"
    }
}
