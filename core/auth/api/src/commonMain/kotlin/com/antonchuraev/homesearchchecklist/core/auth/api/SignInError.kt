package com.antonchuraev.homesearchchecklist.core.auth.api

/**
 * Typed Google sign-in failure. Lives in commonMain so every platform maps its native errors to the
 * same set, and presentation can react without string-matching a raw [Throwable].
 *
 * [code] is a STABLE string literal — deliberately NOT an enum `.name` or a class `simpleName`, both
 * of which R8 can obfuscate on release builds. It is used verbatim as the analytics `error_code`
 * (see MainScreenViewModel.handleSignInClick) so `login_failed` stays segmentable by cause in
 * Amplitude/Firebase on obfuscated Play builds.
 */
sealed class SignInError(val code: String) {
    /** User dismissed / cancelled the picker. Never triggers the legacy fallback. */
    data object Cancelled : SignInError("USER_CANCELED")

    /** No Google account usable on the device (surfaced only after the two-phase request). */
    data object NoCredentials : SignInError("NO_CREDENTIAL")

    /** Network error or exhausted retries — a transient/connectivity failure. */
    data object NetworkOrTimeout : SignInError("NETWORK")

    /** Any other unexpected failure (unmapped Credential Manager / Firebase error). */
    data object Generic : SignInError("GENERIC")
}

/**
 * Throwable carrier so a typed [SignInError] can travel through `Result.failure(...)` — the
 * [com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository.signInWithGoogle]
 * contract returns `Result<GoogleUser>`, so the typed error rides along as this exception.
 *
 * [message] intentionally keeps the Credential Manager `TYPE_` prefix
 * (e.g. `"TYPE_NO_CREDENTIAL: ..."`) that MainScreenViewModel.signInErrorSnackbarKey matches on to
 * pick the user-facing snackbar — do NOT drop the prefix when constructing this.
 */
class SignInException(
    val error: SignInError,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
