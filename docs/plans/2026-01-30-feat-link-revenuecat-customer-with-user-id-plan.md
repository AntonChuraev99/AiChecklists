---
title: "feat: Link RevenueCat customer ID with persistent user ID"
type: feat
date: 2026-01-30
---

# feat: Link RevenueCat customer ID with persistent user ID

## Overview

Связать RevenueCat customer ID с серверным userId, чтобы предотвратить создание новых customers при переустановке приложения. Это обеспечит корректный подсчёт пользователей в RevenueCat и автоматическое восстановление подписок.

## Problem Statement

**Текущая проблема:** Каждая переустановка приложения создаёт нового customer в RevenueCat, потому что:

1. RevenueCat инициализируется в `GistiApplication.onCreate()` **без** `appUserId`
2. Серверный `userId` получается **позже** в `SplashViewModel` через `ensureUserRegistered()`
3. Полученный `userId` **никогда не передаётся** в RevenueCat через `logIn()`

```
Current Flow:
┌─────────────────────────────────────────────────────────────┐
│ App Start → RC.init(NO userId) → Anonymous Customer Created │
│     ↓                                                       │
│ SplashViewModel → registerUser() → userId saved locally     │
│     ↓                                                       │
│ userId EXISTS but NOT linked to RevenueCat! ❌              │
└─────────────────────────────────────────────────────────────┘
```

**Результат:**
- Каждая переустановка = новый anonymous customer
- Покупки "осиротевают" — не привязаны к стабильному ID
- Restore Purchases не работает корректно
- Искажённая статистика пользователей в RevenueCat dashboard

## Proposed Solution

Использовать метод `Purchases.sharedInstance.logIn(appUserId)` из RevenueCat SDK для связывания anonymous customer с серверным `userId` сразу после успешной регистрации.

```
Proposed Flow:
┌─────────────────────────────────────────────────────────────┐
│ App Start → RC.init(NO userId) → Anonymous Customer Created │
│     ↓                                                       │
│ SplashViewModel → registerUser() → userId received          │
│     ↓                                                       │
│ PaywallRepository.logIn(userId) → Customer LINKED! ✅       │
│     ↓                                                       │
│ If returning user → Auto restorePurchases() → Premium! ✅   │
└─────────────────────────────────────────────────────────────┘
```

## Technical Approach

### Architecture Changes

```
feature/paywall/
  domain/
    repository/
      PaywallRepository.kt          # + logIn(), logOut() methods
  data/
    repository/
      PaywallRepositoryImpl.kt      # + implement logIn/logOut via RC SDK

feature/user/
  domain/
    repository/
      UserDataRepository.kt         # + linkWithPaywall() method
  data/
    repository/
      UserDataRepositoryImpl.kt     # + orchestrate logIn after registration
```

### Implementation Phases

#### Phase 1: PaywallRepository — Add logIn/logOut Methods

**File:** `feature/paywall/src/commonMain/kotlin/.../domain/repository/PaywallRepository.kt`

Add interface methods:

```kotlin
interface PaywallRepository {
    // ... existing methods ...

    /**
     * Links the current anonymous RevenueCat customer with the app's user ID.
     * Should be called after successful user registration.
     *
     * @param appUserId The server-generated user ID
     * @return Result containing CustomerInfo if successful, or error
     */
    suspend fun logIn(appUserId: String): Result<LoginResult>

    /**
     * Logs out the current user, generating a new anonymous customer.
     * Typically called when user explicitly signs out (if auth is added later).
     */
    suspend fun logOut(): Result<CustomerInfo>
}

/**
 * Result of logIn operation
 */
data class LoginResult(
    val customerInfo: CustomerInfo,
    val created: Boolean  // true if this is a new customer, false if merged
)
```

**File:** `feature/paywall/src/commonMain/kotlin/.../data/repository/PaywallRepositoryImpl.kt`

Implement using RevenueCat SDK:

```kotlin
override suspend fun logIn(appUserId: String): Result<LoginResult> {
    return suspendCancellableCoroutine { continuation ->
        if (!Purchases.isConfigured) {
            continuation.resume(Result.failure(IllegalStateException("RevenueCat not configured")))
            return@suspendCancellableCoroutine
        }

        Purchases.sharedInstance.logIn(
            appUserId = appUserId,
            onError = { error ->
                AppLogger.e("PaywallRepository", "logIn failed: ${error.message}")
                continuation.resume(Result.failure(Exception(error.message)))
            },
            onSuccess = { customerInfo, created ->
                AppLogger.d("PaywallRepository", "logIn success: created=$created")
                _subscriptionStatus.value = customerInfo.toSubscriptionStatus()
                continuation.resume(Result.success(LoginResult(customerInfo, created)))
            }
        )
    }
}

override suspend fun logOut(): Result<CustomerInfo> {
    return suspendCancellableCoroutine { continuation ->
        if (!Purchases.isConfigured) {
            continuation.resume(Result.failure(IllegalStateException("RevenueCat not configured")))
            return@suspendCancellableCoroutine
        }

        Purchases.sharedInstance.logOut(
            onError = { error ->
                AppLogger.e("PaywallRepository", "logOut failed: ${error.message}")
                continuation.resume(Result.failure(Exception(error.message)))
            },
            onSuccess = { customerInfo ->
                AppLogger.d("PaywallRepository", "logOut success")
                _subscriptionStatus.value = customerInfo.toSubscriptionStatus()
                continuation.resume(Result.success(customerInfo))
            }
        )
    }
}
```

#### Phase 2: Domain Model — LoginResult

**File:** `feature/paywall/src/commonMain/kotlin/.../domain/model/LoginResult.kt`

```kotlin
package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

/**
 * Result of RevenueCat logIn operation.
 *
 * @property subscriptionStatus Current subscription status after login
 * @property isNewCustomer true if a new RevenueCat customer was created,
 *                         false if existing customer was found and merged
 */
data class LoginResult(
    val subscriptionStatus: SubscriptionStatus,
    val isNewCustomer: Boolean
)
```

#### Phase 3: UserDataRepository — Orchestrate logIn

**File:** `feature/user/src/commonMain/kotlin/.../domain/repository/UserDataRepository.kt`

Add method to interface:

```kotlin
interface UserDataRepository {
    // ... existing methods ...

    /**
     * Links user with RevenueCat after successful registration.
     * For returning users (isNewUser=false), also triggers restore.
     *
     * @param userId The server-generated user ID
     * @param isNewUser Whether this is a first-time registration
     * @return Result with subscription status
     */
    suspend fun linkWithPaywall(userId: String, isNewUser: Boolean): Result<SubscriptionStatus>
}
```

**File:** `feature/user/src/commonMain/kotlin/.../data/repository/UserDataRepositoryImpl.kt`

Implementation:

```kotlin
class UserDataRepositoryImpl(
    private val appDatastore: AppDatastore,
    private val deviceIdProvider: DeviceIdProvider,
    private val userApiService: UserApiService,
    private val paywallRepository: PaywallRepository  // NEW dependency
) : UserDataRepository {

    override suspend fun linkWithPaywall(userId: String, isNewUser: Boolean): Result<SubscriptionStatus> {
        // Step 1: Link with RevenueCat
        val loginResult = paywallRepository.logIn(userId)

        return loginResult.fold(
            onSuccess = { result ->
                AppLogger.d(TAG, "RevenueCat logIn success: isNewCustomer=${result.isNewCustomer}")

                // Step 2: For returning users, auto-restore purchases
                if (!isNewUser && !result.isNewCustomer) {
                    AppLogger.d(TAG, "Returning user detected, triggering restore")
                    val restoreResult = paywallRepository.restorePurchases()
                    restoreResult.fold(
                        onSuccess = { status ->
                            AppLogger.d(TAG, "Restore success: isPremium=${status.isActive}")
                            Result.success(status)
                        },
                        onFailure = { error ->
                            AppLogger.w(TAG, "Restore failed, using logIn status: ${error.message}")
                            // Still return success with logIn status
                            Result.success(result.subscriptionStatus)
                        }
                    )
                } else {
                    Result.success(result.subscriptionStatus)
                }
            },
            onFailure = { error ->
                AppLogger.e(TAG, "RevenueCat logIn failed: ${error.message}")
                Result.failure(error)
            }
        )
    }

    override suspend fun ensureUserRegistered(): Result<RegistrationResult> {
        // ... existing registration logic ...

        val result = // existing registration

        // NEW: Link with RevenueCat after successful registration
        result.onSuccess { registrationResult ->
            val userId = registrationResult.userId
            val isNewUser = registrationResult.isNewUser

            // Fire-and-forget linking (don't block app startup)
            coroutineScope.launch {
                linkWithPaywall(userId, isNewUser).onFailure { error ->
                    AppLogger.w(TAG, "Paywall linking failed, will retry later: ${error.message}")
                }
            }
        }

        return result
    }
}
```

#### Phase 4: Update Koin DI

**File:** `feature/user/src/commonMain/kotlin/.../di/UserFeatureModule.kt`

Add PaywallRepository dependency:

```kotlin
val userFeatureModule = module {
    single<UserDataRepository> {
        UserDataRepositoryImpl(
            appDatastore = get(),
            deviceIdProvider = get(),
            userApiService = get(),
            paywallRepository = get()  // NEW
        )
    }
    // ... rest of module
}
```

#### Phase 5: Handle Offline Scenario

When offline, continue with anonymous RevenueCat. Link later when network is available.

**File:** `feature/user/src/commonMain/kotlin/.../data/repository/UserDataRepositoryImpl.kt`

```kotlin
private suspend fun tryLinkWithPaywallIfNeeded() {
    val userId = appDatastore.getString(USER_ID_KEY)
    val isLinkedKey = "is_paywall_linked"
    val isLinked = appDatastore.getBoolean(isLinkedKey) ?: false

    if (!userId.isNullOrEmpty() && !isLinked) {
        AppLogger.d(TAG, "Attempting deferred paywall linking")
        linkWithPaywall(userId, isNewUser = false).onSuccess {
            appDatastore.putBoolean(isLinkedKey, true)
            AppLogger.d(TAG, "Deferred paywall linking successful")
        }
    }
}
```

Call this in `SplashViewModel` or when network becomes available.

### Edge Cases Handling

| Scenario | Handling |
|----------|----------|
| Fresh install (online) | Register → get userId → logIn() → linked |
| Fresh install (offline) | Anonymous RC, link later on network |
| Reinstall (same device) | Register → same userId → logIn() → auto-restore |
| logIn() fails | Log error, continue anonymous, retry on next launch |
| Purchase before logIn | RC SDK auto-merges anonymous purchases on logIn |
| Multiple logIn calls | Safe — RC SDK handles idempotently |

### Testing Scenarios

```kotlin
// Test 1: Fresh install links correctly
@Test
fun `fresh install should link userId with RevenueCat`() {
    // Given: New user, no existing userId
    // When: ensureUserRegistered() completes
    // Then: paywallRepository.logIn(userId) is called
    // And: isNewCustomer = true
}

// Test 2: Reinstall restores purchases
@Test
fun `reinstall should auto-restore purchases for returning user`() {
    // Given: Returning user (server returns existing userId)
    // When: ensureUserRegistered() completes
    // Then: logIn(userId) called, isNewCustomer = false
    // And: restorePurchases() called automatically
    // And: subscription status reflects premium
}

// Test 3: Offline first launch
@Test
fun `offline first launch should defer linking`() {
    // Given: No network on first launch
    // When: ensureUserRegistered() fails
    // Then: App continues with anonymous RC
    // When: Network becomes available
    // Then: linkWithPaywall() is called
}

// Test 4: logIn failure doesn't break app
@Test
fun `logIn failure should not block app usage`() {
    // Given: RevenueCat server is down
    // When: logIn() fails
    // Then: App continues normally
    // And: Error is logged
    // And: Retry scheduled for next launch
}
```

## Acceptance Criteria

### Functional Requirements

- [x] `PaywallRepository.logIn(appUserId)` method implemented
- [x] `PaywallRepository.logOut()` method implemented (for future use)
- [x] `SplashViewModel` calls `logIn()` after successful registration
- [x] Returning users (same deviceId after reinstall) get automatic restore
- [x] New users proceed without restore attempt
- [x] Offline first launch defers linking (checked via isPaywallLinked flag)
- [x] logIn failure doesn't crash or block the app

### Non-Functional Requirements

- [x] No additional API calls to RevenueCat beyond `logIn()` and `restorePurchases()`
- [x] Fire-and-forget linking doesn't block app startup
- [ ] Logging added for debugging linking flow (minimal, in SplashViewModel)

### Quality Gates

- [ ] Unit tests for PaywallRepository.logIn/logOut
- [ ] Integration test for reinstall restore flow
- [ ] Manual test: Install → Purchase → Uninstall → Reinstall → Auto-restore

## Success Metrics

1. **RevenueCat customer count accuracy**: Same user = same customer ID after reinstall
2. **Restore success rate**: Returning users automatically get their premium status
3. **No orphaned purchases**: All purchases linked to stable userId

## Dependencies & Prerequisites

- RevenueCat SDK already integrated (purchases-kmp 2.2.17)
- Server `register_user` endpoint returns `userId` and `isNewUser` flag
- `PaywallRepository` already injected via Koin

## Risk Analysis & Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| logIn() API changes in future RC SDK | Medium | Pin SDK version, test on updates |
| Anonymous purchases before logIn | Low | RC SDK auto-merges on logIn |
| Multiple simultaneous logIn calls | Low | RC SDK handles idempotently |
| Server returns different userId for same device | High | Server uses deviceId as key — shouldn't happen |

## Future Considerations

1. **Full authentication**: If email/social login added later, the `logIn()` call already exists
2. **Multi-device support**: Current design supports adding device migration later
3. **Logout feature**: `logOut()` method ready for use if needed

## References & Research

### Internal References

- RevenueCat initialization: `feature/paywall/src/commonMain/.../data/RevenueCatInitializer.kt:11`
- User registration: `feature/user/src/commonMain/.../data/repository/UserDataRepositoryImpl.kt:41-57`
- App initialization: `composeApp/src/androidMain/.../GistiApplication.kt:30-34`
- DataStore crash prevention: `docs/solutions/runtime-errors/datastore-multiple-instances-crash.md`

### External References

- [RevenueCat: Identifying Customers](https://www.revenuecat.com/docs/customers/identifying-customers)
- [RevenueCat: User IDs Best Practices](https://www.revenuecat.com/docs/customers/user-ids)
- [RevenueCat: Restoring Purchases](https://www.revenuecat.com/docs/getting-started/restoring-purchases)
- [purchases-kmp GitHub](https://github.com/revenuecat/purchases-kmp)

## ERD: No Database Changes Required

Existing models remain unchanged. The linking happens entirely through RevenueCat SDK API calls using the existing server-generated `userId`.

## Files to Modify

| File | Change |
|------|--------|
| `feature/paywall/.../domain/repository/PaywallRepository.kt` | Add `logIn()`, `logOut()` interface methods |
| `feature/paywall/.../domain/model/LoginResult.kt` | NEW: Domain model for login result |
| `feature/paywall/.../data/repository/PaywallRepositoryImpl.kt` | Implement `logIn()`, `logOut()` |
| `feature/user/.../domain/repository/UserDataRepository.kt` | Add `linkWithPaywall()` interface method |
| `feature/user/.../data/repository/UserDataRepositoryImpl.kt` | Implement linking, add PaywallRepository dependency |
| `feature/user/.../di/UserFeatureModule.kt` | Add PaywallRepository to UserDataRepositoryImpl |
