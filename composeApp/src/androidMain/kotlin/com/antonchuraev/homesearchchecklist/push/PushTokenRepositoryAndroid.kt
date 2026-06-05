package com.antonchuraev.homesearchchecklist.push

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.PushTokenRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of [PushTokenRepository].
 *
 * Writes the FCM token and activity timestamp to the same `users/{uid}` document the
 * sync layer uses (see `AndroidFirestoreSyncDataSource`). Uses `SetOptions.merge()` so we
 * never clobber sync fields (`is_premium`, `ai_credits`, …) that live on the same document.
 *
 * uid is taken from [GoogleAuthRepository] (the core:auth abstraction) rather than FirebaseAuth
 * directly, so composeApp needs no direct firebase-auth dependency. This project has NO anonymous
 * auth: if `currentUser == null` the user has not signed in (or auth state is still restoring),
 * so there is no document to attach a token to — we log a warning and no-op. The token gets
 * re-registered on the next app start (and via `onNewToken`) once the user signs in.
 */
internal class PushTokenRepositoryAndroid(
    private val logger: AppLogger,
    private val googleAuthRepository: GoogleAuthRepository,
) : PushTokenRepository {

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val currentUid: String?
        get() = googleAuthRepository.currentUser?.firebaseUid

    override suspend fun registerToken(token: String) {
        if (token.isBlank()) {
            logger.warning(TAG, "registerToken called with blank token — skipping")
            return
        }
        val uid = currentUid
        if (uid == null) {
            logger.warning(TAG, "registerToken skipped: no authenticated user (uid == null)")
            return
        }
        runCatching {
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .set(
                    mapOf(
                        FIELD_FCM_TOKEN to token,
                        FIELD_PLATFORM to PLATFORM_ANDROID,
                        FIELD_LAST_ACTIVE_AT to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                )
                .await()
        }.onFailure { e ->
            logger.error(TAG, "Failed to register FCM token for uid=$uid: ${e.message}", e)
        }
    }

    override suspend fun touchLastActive() {
        val uid = currentUid
        if (uid == null) {
            logger.warning(TAG, "touchLastActive skipped: no authenticated user (uid == null)")
            return
        }
        runCatching {
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .set(
                    mapOf(
                        FIELD_PLATFORM to PLATFORM_ANDROID,
                        FIELD_LAST_ACTIVE_AT to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                )
                .await()
        }.onFailure { e ->
            logger.error(TAG, "Failed to touch lastActiveAt for uid=$uid: ${e.message}", e)
        }
    }

    private companion object {
        const val TAG = "PushToken"
        const val USERS_COLLECTION = "users"
        const val FIELD_FCM_TOKEN = "fcmToken"
        const val FIELD_PLATFORM = "platform"
        const val FIELD_LAST_ACTIVE_AT = "lastActiveAt"
        const val PLATFORM_ANDROID = "android"
    }
}
