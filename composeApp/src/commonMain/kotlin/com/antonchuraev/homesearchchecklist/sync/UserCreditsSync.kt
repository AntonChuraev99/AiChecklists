package com.antonchuraev.homesearchchecklist.sync

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the locally cached AI credits / premium status LIVE and SHARED across web/Android by
 * (1) converging the device onto the canonical Google-linked credit doc and (2) subscribing to
 * that doc (`users/{userId}`) in real time.
 *
 * Why this exists: AI credits are deducted SERVER-SIDE by the Cloud Functions on every AI call
 * (Firestore transaction on `users/{userId}.ai_credits`). The client previously only refreshed
 * its cached balance on its OWN register/link/restore/API calls — so a deduction made on another
 * device (web vs Android) never reached this device, and the balance looked "not shared".
 *
 * Two coroutines:
 *  - [observeAndConverge] — self-healing identity convergence. Credits are keyed by the
 *    device-registration id (USER_ID_KEY), but Google identity is keyed by `google_uid`. A device
 *    that linked Google before USER_ID_KEY switching shipped is stranded on its own device-id doc
 *    (no `google_uid`), so credits never became shared AND the rules deny reading it. Whenever
 *    Firebase Auth is authenticated, resolve the canonical `google_uid` doc and switch USER_ID_KEY
 *    to it. Switching the key makes [observeCreditsDoc] re-attach automatically (it keys off the
 *    same flow), so no manual coordination is needed.
 *  - [observeCreditsDoc] — real-time listener on the (now canonical) credit doc; both signed-in
 *    devices converge on the same doc and observe its live balance.
 *
 * `userId` is the device-registration id (DataStore USER_ID_KEY) — the KEY of the credit doc —
 * NOT the Firebase Auth uid used for checklist sync (that uid == the doc's `google_uid` field).
 */
class UserCreditsSync(
    private val appScope: CoroutineScope,
    private val userDataRepository: UserDataRepository,
    private val firestoreSyncDataSource: FirestoreSyncDataSource,
    private val authRepository: GoogleAuthRepository,
    private val logger: AppLogger,
) {
    private companion object {
        const val TAG = "UserCreditsSync"
    }

    fun start() {
        appScope.launch { observeAndConverge() }
        appScope.launch { observeCreditsDoc() }
    }

    private suspend fun observeAndConverge() {
        authRepository.authState
            .filterIsInstance<GoogleAuthState.Authenticated>()
            .map { it.user.firebaseUid }
            .distinctUntilChanged()
            .collectLatest { googleUid ->
                if (googleUid.isBlank()) return@collectLatest
                when (val result = firestoreSyncDataSource.findUserIdByGoogleUid(googleUid)) {
                    is AppResult.Success ->
                        result.data?.let { canonical ->
                            userDataRepository.convergeUserIdToCanonical(canonical)
                        }
                    is AppResult.Error ->
                        logger.warning(TAG, "convergence lookup failed: ${result.exception.message}")
                    AppResult.Loading -> Unit
                }
            }
    }

    private suspend fun observeCreditsDoc() {
        userDataRepository.getUserDataFlow()
            .map { it.userId }
            .distinctUntilChanged()
            .collectLatest { userId ->
                if (userId.isBlank()) return@collectLatest
                logger.debug(TAG, "attaching credit listener for userId=${userId.take(8)}...")
                firestoreSyncDataSource.observeUserDoc(userId).collect { result ->
                    when (result) {
                        is AppResult.Success -> result.data?.let { snapshot ->
                            userDataRepository.updateCachedCredits(
                                aiCredits = snapshot.aiCredits,
                                isPremium = snapshot.isPremium,
                            )
                        }
                        is AppResult.Error ->
                            logger.warning(TAG, "credit listener error: ${result.exception.message}")
                        AppResult.Loading -> Unit
                    }
                }
            }
    }
}
