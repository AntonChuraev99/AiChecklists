---
paths:
  - "**/*Purchase*.kt"
  - "**/*Credits*.kt"
  - "**/*Restore*.kt"
  - "**/UserDataRepository*.kt"
  - "**/*Paywall*.kt"
---

# Credit Restore Architecture

After purchase/restore, credits must be **explicitly** restored via Cloud Function `restore_credits_after_purchase` (NOT automatic).

```
PurchaseProductUseCase / RestorePurchasesUseCase
  -> UserDataRepository.restoreCreditsAfterPurchase()
    -> UserDataRepositoryImpl (retry up to 3 times, backoff 2s/4s)
      -> UserApiService -> POST /restore_credits_after_purchase
        -> Cloud Function verifies premium via RevenueCat REST API
        -> Writes to Firestore: is_premium=true, ai_credits=300
      -> On success: saves to DataStore (local cache)
```

## Critical rules

- **Always use the UseCase** to restore — `RestorePurchasesUseCase`, not `paywallRepository.restorePurchases()` directly.
- **`SplashViewModel.linkWithPaywall()`** must use `RestorePurchasesUseCase` for returning users.
- **Retry lives in the repository** (`UserDataRepositoryImpl`) — all callers get retry automatically.
- **`PurchaseProductUseCase`** intentionally ignores `Result<Int>` from `restoreCreditsAfterPurchase()` — the purchase itself already succeeded.

## Firestore collections

| Collection | Purpose |
|-----------|---------|
| `users/{userId}` | `is_premium`, `ai_credits`, `credits_restored_at` |
| `credits_restore_log` | Log of successful restore operations |
| `credits_refill_log` | Log of daily premium credit refills |

Paywall: premium via RevenueCat. Free limits `UserLimits(maxChecklists, maxFillsPerChecklist, currentChecklistCount, isPremium)`. `PurchasesDelegate` handles pending transactions. Trial timeline: `docs/solutions/ui-improvements/paywall-trial-timeline.md`.
