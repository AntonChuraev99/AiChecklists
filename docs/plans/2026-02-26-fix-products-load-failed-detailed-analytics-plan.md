---
title: "Fix products_load_failed error details and add comprehensive paywall analytics"
type: fix
date: 2026-02-26
deepened: 2026-02-26
---

# Fix products_load_failed Error Details and Add Comprehensive Paywall Analytics

## Enhancement Summary

**Deepened on:** 2026-02-26
**Sections enhanced:** 4 phases + new findings
**Research agents used:** architecture-strategist, code-simplicity-reviewer, performance-oracle, pattern-recognition-specialist, security-sentinel, best-practices-researcher, framework-docs-researcher, learnings-researcher

### Key Improvements

1. **`purchases-kmp-result` module discovered** — already declared in `gradle/libs.versions.toml` but unused. Provides `Purchases.awaitOfferings()` that eliminates all manual `suspendCancellableCoroutine` wrappers. Migration is optional but recommended.
2. **`cachedPackages` race condition found** — mutable `var Map<String, Package>` on a Koin singleton, written from RevenueCat callback thread, read from coroutine dispatcher. Must be fixed with `@Volatile` + `Mutex`.
3. **Security: raw error messages in analytics** — `error.message` and `underlyingErrorMessage` may contain internal paths or user-specific data. Must sanitize/truncate before sending to Firebase Analytics (100-char param limit) and Amplitude.
4. **Phase priority reordered** — Phase 1 (error preservation) and Phase 2 (analytics + source fix) are critical. Phase 3 (retry) deferred until analytics data confirms it's needed. Phase 4 (concurrency fix) added.

### New Considerations Discovered

- **Koin `parametersOf`** is the idiomatic way to pass `source` to OnboardingScreen's PaywallViewModel (not `SavedStateHandle` extras which don't work with `koinViewModel()`)
- **`isRetryable` should NOT be in domain enum** — retry policy is an infrastructure concern; keep it as a `when` expression in repository
- **`PurchaseResult` and `RestoreResult` are sealed interfaces (not classes)** — new fields with defaults are fully backward-compatible
- **Firebase Analytics param values are capped at 100 characters** — `underlyingError` must be truncated

---

## Overview

Users on version 1.9.4+ see `products_load_failed` analytics events with generic error
`"Error performing request."` and `source = "unknown"`. The RevenueCat SDK provides rich error
information (`PurchasesErrorCode`, `underlyingErrorMessage`) that is being discarded when wrapping
errors. Additionally, several product loading paths are completely silent in analytics.

## Problem Statement

### Root Cause

In `PaywallRepositoryImpl.getOfferings()` (line 66), `PurchasesError` is wrapped into a plain
`Exception(error.message)`:

```kotlin
onError = { error ->
    continuation.resume(Result.failure(Exception(error.message)))
}
```

`PurchasesError` has three fields:
- `code: PurchasesErrorCode` — enum (NetworkError=10, StoreProblemError=2, ConfigurationError=23, etc.)
- `underlyingErrorMessage: String?` — detailed explanation of the root cause
- `message: String` — generic `code.description` (e.g., `"Error performing request."` for NetworkError)

Only `message` is preserved. `code` and `underlyingErrorMessage` are **permanently lost**.

The same wrapping pattern exists in `purchase()`, `restorePurchases()`, `logIn()`, and `logOut()`.

### Source = "unknown" Problem

Two causes:
1. `AppNavigator.navigateToPaywall(source: String = "unknown")` — default value when callers
   don't pass source. All current navigation callers pass source correctly.
2. **Onboarding screen** creates `PaywallViewModel` via `koinViewModel()` without navigation args.
   `SavedStateHandle` has no `AppNavRoute.Paywall` keys, so `source` falls back to `"unknown"`.

### Analytics Blind Spots

| Scenario | Current behavior | Impact |
|----------|-----------------|--------|
| RevenueCat returns null offering | Silent — no analytics event | Can't detect dashboard misconfiguration |
| RevenueCat returns offering with 0 packages | Silent — no analytics event | Can't detect product setup issues |
| All errors | Only generic `error.message` | Can't distinguish NetworkError from ConfigurationError |

### Concurrency Bug (New Finding)

`cachedPackages` in `PaywallRepositoryImpl` (line 29) is a mutable `var Map<String, Package>` on
a Koin singleton (`single<PaywallRepository>`). It's written from RevenueCat's callback thread in
`getOfferings()` and read from coroutine dispatchers in `purchase()`. This is a data race.

**Impact:** Corrupted package map → `purchase()` returns "Package not found" error silently.

## Proposed Solution

### Phase 1: Preserve Error Details (Core Fix)

#### 1.1. Create domain-safe error model

Create `PaywallErrorCode` enum in domain layer (no RevenueCat SDK dependency):

```kotlin
// feature/paywall/src/commonMain/.../domain/model/PaywallError.kt

/**
 * Domain-safe error code for paywall operations.
 * Maps from RevenueCat PurchasesErrorCode at the repository boundary.
 */
enum class PaywallErrorCode {
    NETWORK_ERROR,
    OFFLINE,
    STORE_PROBLEM,
    UNKNOWN_BACKEND,
    CONFIGURATION_ERROR,
    INVALID_CREDENTIALS,
    PRODUCT_NOT_AVAILABLE,
    NOT_CONFIGURED,
    UNKNOWN;
}

/**
 * Rich error wrapper for paywall operations.
 * Preserves error code and underlying details from RevenueCat SDK.
 */
class PaywallException(
    val errorCode: PaywallErrorCode,
    val underlyingError: String? = null,
    message: String
) : Exception(message)
```

> **Research Insight (architecture-strategist):** `isRetryable` removed from enum.
> Retry policy is an infrastructure concern — it belongs in the repository's `when` expression,
> not in the domain model. This keeps the domain layer pure and avoids coupling error classification
> to retry strategy.

> **Research Insight (pattern-recognition-specialist):** Considered `PaywallError` data class vs
> `PaywallException`. Keeping `Exception` subclass because `Result.failure()` requires `Throwable`.
> A data class would need wrapping: `Result.failure(Exception(paywallError.message))`, which loses
> the type information we're trying to preserve. `PaywallException` flows naturally through
> `Result<T>` and can be checked with `is PaywallException` in ViewModel.

#### 1.2. Map PurchasesError → PaywallException in repository

```kotlin
// PaywallRepositoryImpl.kt — private helper

private fun PurchasesError.toPaywallException(): PaywallException {
    val errorCode = when (code) {
        PurchasesErrorCode.NetworkError -> PaywallErrorCode.NETWORK_ERROR
        PurchasesErrorCode.OfflineConnectionError -> PaywallErrorCode.OFFLINE
        PurchasesErrorCode.StoreProblemError -> PaywallErrorCode.STORE_PROBLEM
        PurchasesErrorCode.UnknownBackendError -> PaywallErrorCode.UNKNOWN_BACKEND
        PurchasesErrorCode.ConfigurationError -> PaywallErrorCode.CONFIGURATION_ERROR
        PurchasesErrorCode.InvalidCredentialsError -> PaywallErrorCode.INVALID_CREDENTIALS
        PurchasesErrorCode.ProductNotAvailableForPurchaseError -> PaywallErrorCode.PRODUCT_NOT_AVAILABLE
        else -> PaywallErrorCode.UNKNOWN
    }
    return PaywallException(
        errorCode = errorCode,
        underlyingError = underlyingErrorMessage,
        message = message
    )
}
```

> **Research Insight (best-practices-researcher):** RevenueCat official docs confirm only
> `NetworkError`, `StoreProblemError`, and `OfflineConnectionError` are transient/retryable.
> `UnknownBackendError` is also listed as potentially transient in their error handling guide.
> All other codes indicate permanent misconfiguration or invalid state.

#### 1.3. Update all error wrapping in PaywallRepositoryImpl

Replace `Exception(error.message)` with `error.toPaywallException()` in:

| Method | Line | Current | New |
|--------|------|---------|-----|
| `getOfferings()` | 66 | `Exception(error.message)` | `error.toPaywallException()` |
| `purchase()` | 149 | `PurchaseResult.Error(error.message)` | `PurchaseResult.Error(error.message, error.code.name, error.underlyingErrorMessage)` |
| `restorePurchases()` | 169 | `RestoreResult.Error(error.message)` | `RestoreResult.Error(error.message, error.code.name, error.underlyingErrorMessage)` |
| `logIn()` | 233 | `Exception(error.message)` | `error.toPaywallException()` |
| `logOut()` | 259 | `Exception(error.message)` | `error.toPaywallException()` |

#### 1.4. Update PurchaseResult.Error and RestoreResult.Error

Add optional error detail fields. Both are `sealed interface` with `data class Error` — adding
fields with defaults is fully backward-compatible:

```kotlin
// domain/model/PurchaseResult.kt — the whole file, Error is the only change
sealed interface PurchaseResult {
    data class Success(val subscriptionStatus: SubscriptionStatus) : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Error(
        val message: String,
        val errorCode: String? = null,
        val underlyingError: String? = null
    ) : PurchaseResult
}
```

```kotlin
// domain/model/RestoreResult.kt — same pattern
// Note: RestoreResult is in the same PurchaseResult.kt file (lines 9-13)
sealed interface RestoreResult {
    data class Success(val subscriptionStatus: SubscriptionStatus) : RestoreResult
    data object NoActiveSubscription : RestoreResult
    data class Error(
        val message: String,
        val errorCode: String? = null,
        val underlyingError: String? = null
    ) : RestoreResult
}
```

> **Research Insight (code-simplicity-reviewer):** Using `String` for `errorCode` in
> `PurchaseResult.Error` and `RestoreResult.Error` (instead of `PaywallErrorCode` enum) is
> intentional — these sealed interfaces are used across the codebase, and adding a dependency
> on `PaywallErrorCode` would increase coupling. The `error.code.name` string is sufficient
> for analytics logging.

### Phase 2: Analytics Enhancement & Source Fix

#### 2.1. Update products_load_failed with rich error info

```kotlin
// PaywallViewModel.kt — loadProducts() onFailure

.onFailure { error ->
    val params = mutableMapOf(
        "source" to source,
        "error" to (error.message ?: "unknown").take(100)
    )
    if (error is PaywallException) {
        params["error_code"] = error.errorCode.name
        params["underlying_error"] = (error.underlyingError ?: "none").take(100)
    }
    analyticsTracker.event("products_load_failed", params)
    handleEmptyProducts()
}
```

> **Research Insight (security-sentinel, HIGH):** Raw `error.message` and `underlyingErrorMessage`
> may contain internal paths, user IDs, or device-specific information. Two mitigations:
> 1. **Truncate to 100 chars** — Firebase Analytics caps event param values at 100 characters anyway;
>    explicit `.take(100)` prevents silent truncation that could cut meaningful data.
> 2. **Never display `underlyingError` to users** — it's for analytics/debugging only. The
>    user-facing message remains the static string: "Unable to load subscription options..."

#### 2.2. Add products_load_empty event

```kotlin
// PaywallViewModel.kt — loadProducts() onSuccess, before handleEmptyProducts()

// Case: offering is null or has 0 packages
if (products.isEmpty()) {
    analyticsTracker.event("products_load_empty", mapOf(
        "source" to source,
        "reason" to if (offering == null) "no_current_offering" else "empty_packages"
    ))
    handleEmptyProducts()
    return@launch
}
```

> **Research Insight (code-simplicity-reviewer):** `products_load_started` and
> `products_load_success` are deferred. Right now the immediate need is to **diagnose failures**.
> We already have `paywall_opened` (start proxy) and can infer success from absence of failure/empty
> events. If success rate metrics are needed later, add them in a follow-up.

#### 2.3. Enhance purchase_failed and restore_failed events

```kotlin
// PaywallViewModel.kt — purchase() Error case

is PurchaseResult.Error -> {
    analyticsTracker.event("purchase_failed", mapOf(
        "source" to source,
        "product_id" to selectedProduct.id,
        "error" to (result.message ?: "unknown").take(100),
        "error_code" to (result.errorCode ?: "unknown"),
        "underlying_error" to (result.underlyingError ?: "none").take(100)
    ))
    _screenState.update {
        it.copy(isPurchasing = false, error = result.message)
    }
}

// RestoreResult.Error — same pattern
is RestoreResult.Error -> {
    analyticsTracker.event("restore_failed", mapOf(
        "source" to source,
        "error" to (result.message ?: "unknown").take(100),
        "error_code" to (result.errorCode ?: "unknown"),
        "underlying_error" to (result.underlyingError ?: "none").take(100)
    ))
    _screenState.update {
        it.copy(isPurchasing = false, error = result.message)
    }
}
```

#### 2.4. Fix Onboarding embedded PaywallViewModel (source = "unknown")

The `OnboardingScreen` (line 82) creates `PaywallViewModel` via `koinViewModel()` without
navigation args. The ViewModel reads `source` from `SavedStateHandle` which is empty.

**Fix: Use Koin `parametersOf` with `CreationExtras`:**

```kotlin
// OnboardingScreen.kt — line 82
import androidx.lifecycle.viewmodel.CreationExtras
import org.koin.core.parameter.parametersOf

val paywallViewModel: PaywallViewModel = koinViewModel(
    key = "onboarding_paywall",
    extras = CreationExtras.Empty.apply {
        // Pass source through SavedStateHandle-compatible extras
        set(DEFAULT_ARGS_KEY, bundleOf("source" to "onboarding_trial"))
    }
)
```

> **Research Insight (architecture-strategist + learnings-researcher):** The `SavedStateHandle`
> approach requires `DEFAULT_ARGS_KEY` from `androidx.lifecycle.viewmodel.CreationExtras`.
> If this proves complex with KMP, a simpler alternative is to add a `source: String = "unknown"`
> parameter directly to `PaywallViewModel` constructor and use Koin `parametersOf("onboarding_trial")`.
> However, this changes the Koin module definition and all injection sites. Test both approaches.
>
> **Preferred approach for KMP:** Use Koin `parametersOf`:
>
> ```kotlin
> // PaywallFeatureModule.kt — change viewModelOf to viewModel with params
> viewModel { params ->
>     PaywallViewModel(
>         savedStateHandle = get(),
>         navigator = get(),
>         getOfferingsUseCase = get(),
>         purchaseProductUseCase = get(),
>         restorePurchasesUseCase = get(),
>         analyticsTracker = get(),
>         sourceOverride = params.getOrNull<String>()
>     )
> }
>
> // PaywallViewModel.kt — add sourceOverride parameter
> class PaywallViewModel(
>     savedStateHandle: SavedStateHandle,
>     // ... existing params,
>     private val sourceOverride: String? = null
> ) {
>     private val source: String = sourceOverride
>         ?: savedStateHandle[AppNavRoute.Paywall::source.name]
>         ?: "unknown"
> }
>
> // OnboardingScreen.kt
> val paywallViewModel: PaywallViewModel = koinViewModel(
>     key = "onboarding_paywall",
>     parameters = { parametersOf("onboarding_trial") }
> )
> ```
>
> This avoids `CreationExtras` platform differences and is explicit about the override.

#### 2.5. Verify all navigateToPaywall() callers pass source

Current audit (all pass source correctly):

| Caller | Source value |
|--------|-------------|
| `MainScreenViewModel` — limit dialog | `"main_limit_dialog"` |
| `MainScreenViewModel` — add checklist at limit | `"main_add_checklist_limit"` |
| `MainScreenViewModel` — credits chip | `"main_credits_chip"` |
| `ChecklistDetailViewModel` — fill limit | `"detail_fill_limit"` |
| `ChecklistDetailViewModel` — reminder limit | `"detail_reminder_limit"` |
| `OnboardingScreen` — embedded (**FIX NEEDED**) | `"unknown"` → `"onboarding_trial"` |

### Phase 3: Retry Logic for Retryable Errors (Deferred)

> **Research Insight (code-simplicity-reviewer + performance-oracle):** Retry logic is deferred
> until Phase 1+2 analytics data confirms that transient errors (NetworkError, OfflineError) are
> a significant portion of `products_load_failed` events. If most failures are
> `ConfigurationError` or `InvalidCredentialsError`, retry adds complexity without value.
>
> **Alternative (performance-oracle):** Instead of automatic retry, add a "Try Again" button
> to the error state UI. This gives the user control and avoids hidden delays on the loading screen.
> The button already exists implicitly — `PaywallIntent.LoadProducts` reloads.

**When to implement:** After 1-2 weeks of analytics data from Phase 1+2. If >30% of
`products_load_failed` events have `error_code` in `{NETWORK_ERROR, OFFLINE, STORE_PROBLEM}`,
implement retry.

#### 3.1. Retry implementation (for when needed)

Follow the `UserDataRepositoryImpl` credits restore pattern but with shorter backoff:

```kotlin
// PaywallRepositoryImpl.kt

private companion object {
    const val MAX_OFFERINGS_RETRIES = 2
    val RETRY_DELAYS_MS = longArrayOf(1_000L, 2_000L)
}

private val retryableErrors = setOf(
    PaywallErrorCode.NETWORK_ERROR,
    PaywallErrorCode.OFFLINE,
    PaywallErrorCode.STORE_PROBLEM,
    PaywallErrorCode.UNKNOWN_BACKEND
)

override suspend fun getOfferings(): Result<PaywallOffering?> {
    if (!isConfigured()) {
        return Result.failure(
            PaywallException(
                errorCode = PaywallErrorCode.NOT_CONFIGURED,
                message = "RevenueCat not configured"
            )
        )
    }

    setupCustomerInfoListener()

    var lastError: PaywallException? = null

    for (attempt in 0..MAX_OFFERINGS_RETRIES) {
        val result = fetchOfferingsOnce()

        if (result.isSuccess) return result

        lastError = result.exceptionOrNull() as? PaywallException
        val isRetryable = lastError?.errorCode in retryableErrors
        val hasMoreAttempts = attempt < MAX_OFFERINGS_RETRIES

        if (!isRetryable || !hasMoreAttempts) break

        kotlinx.coroutines.delay(RETRY_DELAYS_MS[attempt])
    }

    return Result.failure(lastError ?: PaywallException(
        errorCode = PaywallErrorCode.UNKNOWN,
        message = "Unknown error after retries"
    ))
}

private suspend fun fetchOfferingsOnce(): Result<PaywallOffering?> {
    return suspendCancellableCoroutine { continuation ->
        Purchases.sharedInstance.getOfferings(
            onError = { error ->
                continuation.resume(Result.failure(error.toPaywallException()))
            },
            onSuccess = { offerings ->
                // ... existing mapping logic (unchanged)
            }
        )
    }
}
```

> **Research Insight (architecture-strategist):** Retry policy is correctly isolated in the
> repository — ViewModel sees only the final result. The `retryableErrors` set replaces the
> removed `isRetryable` enum property, keeping retry decisions in infrastructure layer.

**Retry policy:**
- **Max 2 retries** (3 total attempts) — shorter than credits restore because user is watching
- **Backoff:** 1s, 2s — shorter than credits restore (2s, 4s) for same reason
- **Only for retryable errors:** NetworkError, OfflineConnectionError, StoreProblemError, UnknownBackendError
- **Immediate fail for non-retryable:** ConfigurationError, InvalidCredentialsError, ProductNotAvailableError
- **Transparent to ViewModel** — repository handles retry internally, ViewModel sees final result

### Phase 4: Fix cachedPackages Concurrency (New)

> **Research Insight (performance-oracle, HIGH):** This is a real bug in the current code that can
> cause silent purchase failures.

#### 4.1. Make cachedPackages thread-safe

```kotlin
// PaywallRepositoryImpl.kt

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PaywallRepositoryImpl : PaywallRepository {

    private val _subscriptionStatus = MutableStateFlow(SubscriptionStatus.FREE)
    override val subscriptionStatus: Flow<SubscriptionStatus> = _subscriptionStatus.asStateFlow()

    @Volatile
    private var cachedPackages: Map<String, Package> = emptyMap()
    private val packagesMutex = Mutex()
    private var listenerRegistered = false

    // In getOfferings() onSuccess callback:
    // Replace: cachedPackages = currentOffering.availablePackages.associateBy { it.identifier }
    // With:
    override suspend fun getOfferings(): Result<PaywallOffering?> {
        // ... existing code ...
        // Inside onSuccess:
        val packages = currentOffering.availablePackages.associateBy { it.identifier }
        packagesMutex.withLock { cachedPackages = packages }
        // ...
    }

    // In purchase():
    override suspend fun purchase(packageId: String): PurchaseResult {
        // ... existing code ...
        val packageToPurchase = packagesMutex.withLock { cachedPackages[packageId] }
            ?: return PurchaseResult.Error("Package not found: $packageId")
        // ...
    }
}
```

> **Note:** `@Volatile` ensures visibility across threads for reads that don't go through Mutex.
> `Mutex` provides atomicity for the read-modify-write pattern. For this specific case where
> `cachedPackages` is always replaced entirely (not modified), `@Volatile` alone would suffice.
> Adding `Mutex` is defensive for future changes.

## Analytics Events Summary (After Fix)

### Product Loading (Enhanced)

| Event | When | Key Params |
|-------|------|------------|
| `products_load_empty` | Offerings returned but no products | `source`, `reason` (`no_current_offering` / `empty_packages`) |
| `products_load_failed` | Error loading products | `source`, `error` (truncated 100ch), `error_code`, `underlying_error` (truncated 100ch) |

### Purchase & Restore (Enhanced)

| Event | When | Key Params |
|-------|------|------------|
| `purchase_failed` | Purchase error | `source`, `product_id`, `error`, `error_code`, `underlying_error` |
| `restore_failed` | Restore error | `source`, `error`, `error_code`, `underlying_error` |

### Existing (Unchanged)

`paywall_opened`, `paywall_closed`, `purchase_button_clicked`, `purchase_completed`,
`purchase_cancelled`, `restore_button_clicked`, `restore_completed`, `restore_no_subscription`,
`paywall_page_swiped`, `paywall_terms_clicked`, `paywall_privacy_clicked`, `paywall_support_clicked`

### Future (After Analytics Data Review)

| Event | When | Key Params |
|-------|------|------------|
| `products_load_started` | `loadProducts()` begins | `source` |
| `products_load_success` | Products loaded successfully | `source`, `product_count`, `offering_id` |

## Acceptance Criteria

### Functional

- [ ] `products_load_failed` event includes `error_code` (e.g., `NETWORK_ERROR`), `underlying_error`
- [ ] `products_load_empty` event fires when offerings return null or empty products, with `reason` field
- [ ] `purchase_failed` and `restore_failed` events include `error_code` and `underlying_error`
- [ ] Onboarding paywall reports `source = "onboarding_trial"` instead of `"unknown"`
- [ ] User-facing error message unchanged: "Unable to load subscription options..."
- [ ] `cachedPackages` is thread-safe with `@Volatile`
- [ ] Analytics param values truncated to 100 characters

### Non-Functional

- [ ] Domain layer (`domain/model/`) has no dependency on RevenueCat SDK
- [ ] `PaywallErrorCode` enum has no `isRetryable` property (retry policy in repository)
- [ ] Existing unit tests pass without modification
- [ ] New unit tests cover: error mapping, analytics event params, PurchaseResult.Error backward compatibility

## Files Affected

| File | Action | Description |
|------|--------|-------------|
| `feature/paywall/src/commonMain/.../domain/model/PaywallError.kt` | **New** | `PaywallErrorCode` enum + `PaywallException` class |
| `feature/paywall/src/commonMain/.../domain/model/PurchaseResult.kt` | Edit | Add `errorCode`, `underlyingError` to both `PurchaseResult.Error` and `RestoreResult.Error` |
| `feature/paywall/src/commonMain/.../data/repository/PaywallRepositoryImpl.kt` | Edit | Error mapping helper, `@Volatile cachedPackages`, `Mutex` |
| `feature/paywall/src/commonMain/.../presentation/PaywallViewModel.kt` | Edit | Rich analytics events with truncation, `PaywallException` handling |
| `feature/paywall/src/commonMain/.../di/PaywallFeatureModule.kt` | Edit | Change `viewModelOf` to `viewModel { }` with `params` for source override |
| `feature/onboarding/src/commonMain/.../OnboardingScreen.kt` | Edit | Pass `source = "onboarding_trial"` via Koin `parametersOf` |

## Dependencies & Risks

| Risk | Mitigation |
|------|------------|
| `cachedPackages` race condition (existing bug) | `@Volatile` + `Mutex` in Phase 4 |
| Breaking `PurchaseResult.Error` / `RestoreResult.Error` constructors | New fields have defaults (`null`) — backward compatible |
| Analytics param values exceeding 100-char Firebase limit | Explicit `.take(100)` truncation |
| `underlyingErrorMessage` leaking PII to analytics | Truncation + never displaying to user |
| Koin `parametersOf` approach may conflict with `SavedStateHandle` injection | Test with `key = "onboarding_paywall"` to isolate instance |
| Dual ViewModel instances calling getOfferings() concurrently | `Mutex` on `cachedPackages` prevents race; both get valid data |

## Future Opportunities

### `purchases-kmp-result` Module Migration

> **Research Insight (framework-docs-researcher, CRITICAL):** The `purchases-kmp-result` module
> is already declared in `gradle/libs.versions.toml` (line 75) but is NOT used anywhere in the
> codebase. It provides coroutine-native APIs:
>
> ```kotlin
> // Instead of suspendCancellableCoroutine + callbacks:
> val offerings = Purchases.sharedInstance.awaitOfferings()
> val customerInfo = Purchases.sharedInstance.awaitPurchase(package)
> val customerInfo = Purchases.sharedInstance.awaitRestore()
> ```
>
> This would eliminate ~60 lines of callback wrapping code in `PaywallRepositoryImpl.kt`.
> Consider as a follow-up refactor after the core fix lands.

### Retry Logic (Phase 3)

Implement after 1-2 weeks of analytics data confirms transient errors are significant.

## References

- RevenueCat KMP SDK `PurchasesError`: `com.revenuecat.purchases.kmp.models.PurchasesError` (v2.2.17)
- RevenueCat error codes: [Error Handling docs](https://www.revenuecat.com/docs/test-and-launch/errors)
- RevenueCat `purchases-kmp-result` module: already in `gradle/libs.versions.toml` line 75
- Credits restore retry pattern: `feature/user/src/commonMain/.../data/repository/UserDataRepositoryImpl.kt`
- Documented solution: `docs/solutions/runtime-errors/paywall-mock-product-silent-failure.md`
- Documented solution: `docs/solutions/logic-errors/premium-status-not-syncing-on-launch.md`
- Firebase Analytics limits: event params max 100 characters per value
