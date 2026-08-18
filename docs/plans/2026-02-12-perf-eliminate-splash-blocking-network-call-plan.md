---
title: "perf: eliminate blocking network call in SplashViewModel"
type: perf
date: 2026-02-12
---

# perf: Eliminate Blocking Network Call in SplashViewModel

## Problem

`SplashViewModel` blocks navigation with a network call (`syncWithServer()` → HTTP POST `/register_user`) on **every** launch, even for returning users. All data needed for navigation (`isOnboardingPassed`) is already cached in DataStore.

**Impact:** 1-5s delay on good connection, up to 60s on timeout.

## Solution

One file change in `SplashViewModel.kt`: read cache → navigate immediately → sync in background.

```
Returning user: DataStore read (~1ms) → Navigate → Background: sync + paywall link
New user:       Show splash → Register (network) → Navigate → Paywall link
```

MainScreen auto-updates via StateFlow when background sync completes (~1-2s).

## File to Modify

**`feature/splash/src/commonMain/kotlin/.../SplashViewModel.kt`**

### Current code (lines 16-43):

```kotlin
init {
    viewModelScope.launch {
        val registrationResult = userDataRepository.ensureUserRegistered() // BLOCKS

        registrationResult.onSuccess { registrationData ->
            launch { linkWithPaywall(userId = registrationData.userData.userId, isNewUser = registrationData.isNewUser) }
        }

        val userData = registrationResult.getOrNull()?.userData
            ?: userDataRepository.getUserData()

        with(appNavigator) {
            when (userData.isOnboardingPassed) {
                true -> navigateToMainScreen(clearBackStack = true)
                false -> navigateToOnboarding()
            }
        }
    }
}
```

### Proposed code:

```kotlin
class SplashViewModel(
    private val userDataRepository: UserDataRepository,
    private val paywallRepository: PaywallRepository,
    private val appNavigator: AppNavigator,
    private val appScope: CoroutineScope          // app-level scope from Koin
) : ViewModel() {

    init {
        viewModelScope.launch {
            // Read from eagerly-cached StateFlow — instant, no I/O
            val cached = userDataRepository.getUserDataFlow().first()

            if (cached.userId.isNotBlank()) {
                // Launch background work BEFORE navigation (launch-then-navigate pattern)
                appScope.launch { runCatching { userDataRepository.syncWithServer() } }
                appScope.launch { runCatching { linkWithPaywall(cached.userId, isNewUser = false) } }

                // Returning user: navigate immediately from cache
                navigateTo(cached.isOnboardingPassed)
            } else {
                // New user (first launch only): must wait for registration
                val result = userDataRepository.ensureUserRegistered()
                val userData = result.getOrNull()?.userData ?: cached

                result.onSuccess { data ->
                    appScope.launch { linkWithPaywall(data.userData.userId, isNewUser = data.isNewUser) }
                }

                navigateTo(userData.isOnboardingPassed)
            }
        }
    }

    private fun navigateTo(isOnboardingPassed: Boolean) { ... }
    private suspend fun linkWithPaywall(userId: String, isNewUser: Boolean) { ... }
}
```

### Key design decisions:

1. **`getUserDataFlow().first()` instead of `getUserData()`** — uses the eagerly-cached StateFlow (already in memory via `SharingStarted.Eagerly`), avoids 4 separate DataStore reads
2. **`appScope` instead of `viewModelScope`** — background coroutines survive ViewModel destruction after `navigateTo()` clears the back stack
3. **Launch-then-navigate pattern** — `appScope.launch` calls always BEFORE `navigateTo()`, so coroutines are guaranteed to start even if `viewModelScope` is cancelled immediately after navigation
4. **Independent `launch` blocks** — `syncWithServer()` failure won't prevent `linkWithPaywall()` from running
5. **`runCatching`** — prevents unhandled exceptions from crashing background coroutines

## Acceptance Criteria

- [ ] Returning users see MainScreen within ~100ms (no visible splash)
- [ ] New users (first launch) still see splash while registering
- [ ] `aiCredits` and `isPremium` refresh in background on every start
- [ ] MainScreen auto-updates when background sync completes
- [ ] App works correctly offline (cached data, sync silently fails)
- [ ] Returning user with cleared app data falls back to registration flow correctly
- [ ] No regression in premium status (see: `docs/solutions/logic-errors/premium-status-not-syncing-on-launch.md`)

## Success Metrics

| Scenario | Before | After |
|----------|--------|-------|
| Returning user, good connection | 1-5s | ~100ms |
| Returning user, slow/no connection | 5-60s | ~100ms |
| New user, first launch | 1-5s | 1-5s (unchanged) |
