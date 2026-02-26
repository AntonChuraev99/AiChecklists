# Fix: products_load_failed Error Details and Paywall Analytics

**Date:** 2026-02-26
**Type:** Bug fix + Analytics enhancement
**Status:** Done

## Problem

Users on v1.9.4+ saw `products_load_failed` analytics events with:
- `error = "Error performing request."` — generic RevenueCat `code.description`
- `source = "unknown"` — OnboardingScreen didn't pass source to PaywallViewModel

Root cause: `PaywallRepositoryImpl.getOfferings()` wrapped `PurchasesError` into `Exception(error.message)`, permanently losing `error.code` (e.g., `NetworkError`) and `error.underlyingErrorMessage`.

## Research Summary

- Traced error to `PurchasesErrorCode.NetworkError(10, "Error performing request.")` in RevenueCat KMP SDK v2.2.17
- Found same wrapping pattern in `purchase()`, `restorePurchases()`, `logIn()`, `logOut()`
- Found `cachedPackages` race condition (mutable var on Koin singleton)
- Found `source = "unknown"` from OnboardingScreen using `koinViewModel()` without params
- Discovered `purchases-kmp-result` module already in Gradle but unused

## Plan

Full plan: `docs/plans/2026-02-26-fix-products-load-failed-detailed-analytics-plan.md`

Deepened with 8 parallel research agents (architecture-strategist, code-simplicity-reviewer, performance-oracle, pattern-recognition-specialist, security-sentinel, best-practices-researcher, framework-docs-researcher, learnings-researcher).

Key decisions from deepening:
- Deferred retry logic (Phase 3) until analytics data proves it's needed
- Removed `isRetryable` from enum (infrastructure concern, not domain)
- Added `cachedPackages` concurrency fix (Phase 4)
- Added `.take(100)` truncation for Firebase Analytics param limit

## What Was Implemented

### New file
- `feature/paywall/.../domain/model/PaywallError.kt` — `PaywallErrorCode` enum (9 values) + `PaywallException` class

### Edited files
| File | Changes |
|------|---------|
| `PurchaseResult.kt` | Added `errorCode: String?` and `underlyingError: String?` to `PurchaseResult.Error` and `RestoreResult.Error` |
| `PaywallRepositoryImpl.kt` | Added `toPaywallException()` mapping, replaced all `Exception(error.message)` and `IllegalStateException`, `@Volatile cachedPackages` + `Mutex` |
| `PaywallViewModel.kt` | Added `sourceOverride` param, `products_load_empty` event, enriched `products_load_failed`/`purchase_failed`/`restore_failed` with error_code and underlying_error |
| `PaywallFeatureModule.kt` | Changed `viewModelOf` to `viewModel { params -> }` for source override support |
| `OnboardingScreen.kt` | Passes `"onboarding_trial"` as source via `parametersOf` |

### New tests
- `PaywallErrorTest.kt` — 4 tests: error code preservation, underlying error, Exception inheritance, enum completeness
- `PurchaseResultTest.kt` — 6 tests: backward compatibility (single-arg), full details, sealed interface

## Validation Results

- Build: BUILD SUCCESSFUL
- Existing tests: all passed
- New tests (10): all passed
- No rollbacks during implementation

## Analytics Events Changed

| Event | Before | After |
|-------|--------|-------|
| `products_load_failed` | `error` (generic message), `source` | + `error_code`, `underlying_error` (truncated 100ch) |
| `products_load_empty` | (did not exist) | `source`, `reason` (no_current_offering / empty_packages) |
| `purchase_failed` | `error` (generic) | + `error_code`, `underlying_error` |
| `restore_failed` | `error` (generic) | + `error_code`, `underlying_error` |

## Deferred Items

- **Retry logic** — deferred until analytics data shows transient errors are >30% of failures
- **`purchases-kmp-result` migration** — already in Gradle, would eliminate ~60 lines of callback code
- **`products_load_started` / `products_load_success`** — deferred as YAGNI for initial diagnosis
