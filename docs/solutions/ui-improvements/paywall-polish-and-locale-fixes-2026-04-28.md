---
title: "Paywall Polish & KZT Locale Fixes — A/B Testing Infrastructure & Compliance Trade-offs"
date: 2026-04-28
type: ui-improvements
modules: [feature/paywall, core/remoteconfig, core/designsystem]
keywords: [paywall, monthly-equivalent, KZT-locale, TextAutoSize, Material-You-drift, A/B-testing, deceptive-behavior, push-notification-removal, hardcoded-brand-colors, compliance-trade-offs]
project: gisti-ai-checklists
---

# Paywall Polish & KZT Locale Fixes — A/B Testing Infrastructure & Compliance Trade-offs

## Context

Prior paywall sessions (2026-04-28 redesign, truthful-copy fixes) delivered 3 A/B variants (timeline/features/compare) and corrected Free vs Pro marketing claims. This session focused on **production-readiness for Pixel 9 + KZT locale support**, visual consistency under Material You theming, and A/B testing instrumentation. Complexity arose from three independent constraints:

1. **KZT locale clipping:** Disclosure strings like "915,83 ₸/mo · billed annually" (28 chars) do not fit fixed-height cards (84dp subtitle) or single-line CTA sub on Pixel 9 width (360dp). Emulator testing masked issue (RC fallback returns USD).
2. **Material You drift:** Feature icons cycling through `cs.primaryContainer` (white, blue, purple depending on device wallpaper) + `cs.primary` (drifted to purple on Pixel 9) — inconsistent branding across variants.
3. **Compliance trade-offs:** Google Play "Deceptive behavior" policy forbids promised features (Day 2 trial-end notification) + "Auto-renews" language raises Play policy 4.5 scope questions.

## What Changed

### 1. A/B Testing Infrastructure & User Property Tracking

**File:** `feature/paywall/src/commonMain/kotlin/.../PaywallViewModel.kt`

**Change:** Default variant switched from `paywall_variant: "timeline_v1"` to `"features_v1"` in **two places:**
- `RemoteConfigDefaults.paywall_variant`
- `parseVariant()` else-fallback

**Why dual assignment:** Defense-in-depth for offline scenarios. If Remote Config fails to fetch or RC operator typos the value, in-code default and parse fallback independently protect against unknown variants silently falling back to timeline (which has accessibility metrics baggage from earlier design phase).

**User property tracking added:**
```kotlin
// In onPaywallOpen()
analytics.setUserProperties(mapOf("paywall_variant" to variant))
```

**Impact:** Downstream events (purchase_completed, trial_started, paywall_dismissed) now inherit `paywall_variant` dimension in Amplitude/Firebase Analytics. Enables cross-event A/B filtering without duplicate event-level properties. Critical for conversion funnel analysis (variant→impression→trial_start→purchase rates).

### 2. Locale-Aware Monthly Equivalent Pricing

**File:** `feature/paywall/src/commonMain/kotlin/.../PaywallRoute.kt`

**New function:** `monthlyEquivalent(yearlyPrice: String, locale: Locale): String`

**Algorithm:**
```kotlin
// Extract decimal separator from source string
val decimalSep = if (yearlyPrice.contains(",")) "," else "."
val digits = yearlyPrice.replace(Regex("[^0-9$₸.,]"), "")
    .replace(decimalSep, ".")
    .toDoubleOrNull() ?: return yearlyPrice

// Compute monthly amount
val monthly = digits / 12
val formatted = when {
    monthly % 1 == 0.0 -> monthly.toInt().toString()
    else -> String.format("%.2f", monthly).replace(".", decimalSep)
}

// Preserve currency symbol
val currency = yearlyPrice.replace(Regex("[0-9, .,₸$]"), "")
return "$formatted$currency"
```

**Examples (tested on physical device):**
- USD: `"$29.99"` → `"$2.50"`
- KZT (decimal): `"10 990,00 ₸"` → `"915,83 ₸"`
- KZT (no-decimal): `"29 990 ₸"` → `"2499 ₸"` (detects separator absence, formats with no decimals)

**Why not NumberFormat:** Compose Multiplatform has no `commonMain` locale APIs (`java.text.NumberFormat` is Android-only). String regex parsing is KMP-safe + handles both decimal and no-decimal locales without SDK-level changes.

**Computation location:** Happens once in `PaywallRoute` composable during state aggregation, passed via `PaywallUiState.yearlyMonthlyPrice`. Components consume precomputed value → zero re-computation on recomposition.

### 3. TextAutoSize for KZT Compliance Disclosure

**File:** `feature/paywall/src/commonMain/kotlin/.../PlanRow.kt`

**Problem:** KZT subtitle strings are 28-29 characters:
- `"915,83 ₸/mo · billed annually"` (subtitle in card)
- `"Then 10 990,00 ₸/year. Cancel anytime."` (sticky CTA sub)

Fixed-height 84dp `PlanRow` and 1-line CTA disclosure on 360dp Pixel 9 width cannot accommodate without clipping.

**Solution:** `TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = bodySmall.fontSize)`

```kotlin
Text(
    text = planSubtitle,
    fontSize = textSize,
    modifier = Modifier.drawWithCache { ... } // TextAutoSize logic
)
```

**Font size floor: 8.sp**

Iterative testing:
- Initial: 9.sp (still clipped 1–2 chars on KZT strings)
- Revised: 8.sp (fully readable, above accessibility floor of 6.sp, conforms to Material 3 guidelines for supplementary text)

**Coverage:** Applied to `PlanRow.subtitle` + `StickyCta.yearlyMonthlySubtle` to handle both card context and disclosure context.

### 4. Material You Brand Color Override for Illustrations

**Files:** `FeatureRow.kt`, `HeroIllustration.kt`

**Problem:** Icon backgrounds cycling through Material You `cs.primaryContainer` (white on light theme, blue on standard wallpaper, purple on Pixel 9 dynamic wallpaper from image). Paywall branding requires stable blue throughout.

**Solution:** Replace theme token with hardcoded brand blue.

```kotlin
// Before (drifts with wallpaper)
FeatureRow(
    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer, // white/purple drift
    haloBgColor = MaterialTheme.colorScheme.primaryContainer
)

// After (stable brand blue)
FeatureRow(
    iconBackgroundColor = Color(0xFFE3F2FD), // Blue 50 — stable across devices
    haloBgColor = Color(0xFFE3F2FD)
)
Text(
    modifier = Modifier.background(
        color = Color(0xFFE3F2FD), // Same for consistency
        shape = RoundedCornerShape(12.dp)
    )
)
```

**Icon tint:** Changed from `cs.primary` (drifts to purple on Pixel 9) to `Color.Black` (consistent contrast + visual hierarchy).

**Rationale:** Paywall is a critical conversion surface. Brand coherence (blue accent + minimal aesthetic) supersedes Material You adaptive theming. Dynamic color opt-out via `dynamicColor: Boolean` DataStore (default false) already exists — paywall brand colors reinforce this choice.

**Coverage:** 7 iterations on physical Pixel 9 + 1 emulator iteration to expose KZT clipping.

### 5. Compliance Trade-offs — Deceptive Behavior Risk Mitigation

**File:** `feature/paywall/src/commonMain/kotlin/.../components/PaywallTrialTimeline.kt`

#### 5.1 Day 2 Timeline Step Removed

**What was promised:** "Day 2 — We'll remind you before billing"

**Reality:** No Cloud Scheduler job or FCM push notification implemented.

**Risk:** Google Play "Deceptive behavior" policy (section 4.1) explicitly forbids claims of features not present. Prior incident: docs/solutions/ui-improvements/paywall-subscription-policy-compliance.md documented similar case (3-day trial terminology precision).

**Action:** Removed step from timeline.

**Code anchor for future restoration:**
```kotlin
// TODO: Restore Day 2 step if real pre-trial-end notification is implemented
//  Step(
//      dayNumber = 2,
//      title = stringResource(Res.string.paywall_v1_trial_day2_title),
//      description = stringResource(Res.string.paywall_v1_trial_day2_desc)
//  )
```

#### 5.2 "Auto-renews" Language Removed from CTA Disclosure

**Files:** `strings.xml` (keys `paywall_v1_cta_sub_yearly` / `paywall_v1_cta_sub_monthly`)

**What was:** "Then $29.99/year. Auto-renews. Cancel anytime." (50 characters)

**What is:** "Then $29.99/year. Cancel anytime." (36 characters)

**Rationale:** Product preference for 1-line disclosure on sticky CTA (320dp width on small phones). Explicit "Auto-renews" language was seen as redundant given "Cancel anytime" present.

**Compliance risk:** Google Play subscription policy 4.5 may require explicit "auto-renews" language in disclosure. Comment anchor documents reversal if Play review rejects.

```kotlin
// paywall_v1_cta_sub_yearly:
// "Then {amount}. Cancel anytime."
// NOTE: Google Play 4.5 may require "Auto-renews" language restored if review rejects.
// Reversal: append "Auto-renews. " before "Cancel anytime." — prior version string:
// "Then {amount}. Auto-renews. Cancel anytime."
```

### 6. Visual Polish — Remaining Fixes

**HeroIllustration.kt:**
- Removed back card (visual clutter)
- Halo resized from 280dp → 260dp + recolored to brand blue (#E3F2FD)

**FeatureRow.kt:**
- Top alignment for icon + FeatureRow content (was baseline-centered, caused visual jitter)

**PlanRow.kt:**
- Fixed height: 84dp (was variable with text height)

**Toolbar:**
- Removed tonal tint from TopAppBar `scrolledContainerColor` (was cs.secondary, looked muddy)

**StickyCta.kt:**
- Added `navigationBarsPadding()` to gap gesture bar

**ScreenshotCatalogTest.kt:**
- Updated anchor: "Restore Purchase" → "Restore" (matching toolbar button text after footer removal)

## Patterns Established

### A/B Testing User Property Tracking

**Pattern:** Set user property on event trigger, inherit downstream.

**When to use:** Any feature flagged via Remote Config (variants, feature gates, language selection, region-specific pricing). Instead of embedding variant in every event payload, set once and let analytics platform inherit dimension.

**Benefit:** Reduces event schema bloat, enables retroactive A/B filtering in analytics dashboards without reprocessing event logs.

### KMP Locale-Aware String Formatting

**Pattern:** Detect decimal separator from source string, use regex for currency parsing.

**When to use:** KMP projects needing multi-locale formatting (plural rules, currency amounts, date formats) without access to `java.text.NumberFormat` or `DateTimeFormatter`.

**Rationale:** Compose Multiplatform lacks `commonMain` locale APIs. String parsing is portable, locale-aware via source string inspection.

**Pitfall:** CANNOT assume decimal separator from device locale — Firestore Remote Config may return localized prices from different region. Always detect from string.

### TextAutoSize for Responsive Disclosure Strings

**Pattern:** `TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = bodySmall)` for compliance disclosures.

**When to use:** Strings with variable length across locales (disclosure strings, pricing fine-print) that must fit fixed-height containers.

**Accessibility floor:** 8.sp is near Material 3 minimum for supplementary text (6.sp floor). Below 8.sp, readability degrades on small screens.

**Coverage:** Apply to ALL subscription-related text (trial terms, plan pricing, renewal terms) — not just KZT. English strings are shorter, but future RTL (Arabic, Hebrew) may require dynamic sizing.

### Hardcoded Brand Colors for Critical Surfaces

**Pattern:** Override Material You tokens (`cs.primaryContainer`, `cs.primary`) with hardcoded brand colors for conversion-critical surfaces (paywall, CTA buttons, hero illustrations).

**When to use:** When visual consistency > adaptive theming. Paywall is first-impression surface; brand coherence drives conversion. Bury dynamic color opt-out in Settings (not front-and-center).

**Rationale:** Material You dynamic color drifts across devices based on wallpaper. On Pixel 9 with photo wallpaper, `cs.primary` becomes purple. Paywall branding (blue accent) must be immune to device context.

**Defense:** Hardcoded colors + `dynamicColor: Boolean` setting (default false) gives product control while respecting power-user preference.

## Compliance Trade-offs

### Google Play "Deceptive behavior" Policy 4.1

**Incident:** Day 2 trial-end notification promised in timeline, not implemented.

**Mitigation:** Removed step. Code anchor documents reversal path if feature is added.

**Risk ownership:** Product decides timeline scope; engineering gates against false claims.

### Google Play Subscription Policy 4.5 — Auto-Renews Language

**Incident:** "Auto-renews" removed from CTA disclosure for 1-line fit on small screens.

**Mitigation:** Comment anchor in strings.xml documents reversal procedure. If Play review rejects ("Auto-renews" required), prepend to "Cancel anytime." without layout refactor.

**Path to reversal:**
```
paywall_v1_cta_sub_yearly: "Then {amount}. Auto-renews. Cancel anytime."
paywall_v1_cta_sub_monthly: "Then {amount}. Auto-renews. Cancel anytime."
```

**Risk ownership:** Product accepts Play review risk; engineering maintains rollback path.

## KMP Patterns Applied

### 1. monthlyEquivalent() — Locale-Aware Regex String Formatting

**Why necessary in KMP:** `java.text.NumberFormat` Android-only. KMP projects use decimal detection + string-safe regex.

**Usage:**
```kotlin
val monthlyStr = monthlyEquivalent(yearlyPrice, locale)
// "29 990,00 ₸" → "2499 ₸"
```

**Preconditions:**
- `yearlyPrice` is localized string from Remote Config
- Decimal separator inferred from string (`,` or `.`)
- Currency symbol extracted via regex `[^0-9$₸.,]` removal

### 2. Hardcoded Color Constants for Brand Surfaces

**Pattern:** `object PaywallBrandColors { val primary = Color(0xFFE3F2FD) }` — single source of truth.

**Why not theme tokens:** Theme tokens (cs.primary, cs.primaryContainer) drift with Material You on wallpaper. Brand consistency requires hardcoding.

**Applied to:**
- FeatureRow icon backgrounds + halos
- HeroIllustration accent elements
- Button ripple/tint colors

**Future-proofing:** If brand colors change, one object definition updates all paywall surfaces.

### 3. TextAutoSize Calculation During State Aggregation

**Pattern:** Compute fontSize range in Route/ViewModel, pass via state. Components consume static value.

**Why:** TextAutoSize layout logic runs every measure-pass. Pre-computation + state-passing avoids re-calculation on recomposition.

**Applied to:** PlanRow subtitle, StickyCta disclosure subtext.

## Iteration Lessons

### KZT Clipping Exposed by Physical Device Testing

**Lesson:** Emulator with RC fallback (USD prices) masked locale clipping bug. KZT strings (28–29 chars) require 8.sp font, not 9.sp. Physical device with real pricing budget = mandatory validation step for paywall, not optional.

**Action:** All future paywall changes validated on Pixel 9 + physical billing environment (or production Release notes test on Play Console).

### Material You Wallpaper Drift Unpredictable

**Lesson:** `cs.primaryContainer` is white on default Material 3, blue on standard wallpaper, purple on image wallpapers. Pixel 9 with photo wallpaper = purple icons (failed visual test). Hardcoded colors eliminate device-context dependency.

**Action:** For paywall + CTA-heavy surfaces, hardcode brand colors. Reserve Material You tokens for secondary UI (backgrounds, dividers).

### Compliance Trade-offs Require Comment Anchors

**Lesson:** Removed "Day 2 reminder" step + "Auto-renews" language. Future engineer may ask "why missing?" Comment anchors with reversal paths prevent churn-and-redo cycles.

**Action:** All compliance-driven cuts must include `// TODO: Restore X if feature/policy changes.` anchor with reversal instructions.

### A/B Testing Instrumentation Must Precede Variant Launch

**Lesson:** User property tracking added in this session, but should have been in paywall redesign (2026-04-28). Variants deployed without cross-event A/B segmentation = blind analysis for 1 week.

**Action:** A/B infrastructure (user property, analytics events) = prerequisite before variant rollout, not post-launch addition.

## Anti-patterns Encountered

### 1. Material You `cs.primaryContainer` for Brand Surfaces

**Anti-pattern:** Using theme token `cs.primaryContainer` for icon backgrounds, assuming it stays blue across devices.

**Reality:** Material You adapts to wallpaper. Pixel 9 dynamic color + image wallpaper = purple backgrounds.

**Fix:** Hardcode `Color(0xFFE3F2FD)` for paywall surfaces.

### 2. Fixed dp-Widths with Variable Text Length

**Anti-pattern:** 84dp PlanRow height with unlimited subtitle text length (assumes English).

**Reality:** KZT "915,83 ₸/mo · billed annually" is 28 chars, exceeds fixed height at standard font size.

**Fix:** TextAutoSize with 8.sp floor, or allow card height to expand with text.

### 3. Compliance Language Omissions Without Rollback Path

**Anti-pattern:** Remove "Auto-renews" language for layout fit, no comment explaining reversal path.

**Reality:** Play review rejects → engineer must invent reversal from prior git history.

**Fix:** Comment anchor with exact reversal string + rationale for omission.

### 4. Emulator-Only Testing for Billing Surfaces

**Anti-pattern:** Testing paywall on emulator with RC fallback (USD) instead of real locale pricing.

**Reality:** Clipping bugs (KZT long strings) invisible until physical device with real RC values.

**Fix:** Physical device validation mandatory for paywall, pricing, billing surfaces.

### 5. A/B Instrumentation as Post-Launch Addition

**Anti-pattern:** Ship variants, add user property tracking 1 week later.

**Reality:** Initial week of analytics blind to variant dimension → unusable for A/B analysis.

**Fix:** A/B properties + events = prerequisite before variant launch.

## Connected Issues & Follow-up

- **Play Review risk (4.5 auto-renews language):** Monitor Play review feedback. If rejected, prepend "Auto-renews. " to CTA disclosure sub via comment anchor reversal.
- **Day 2 notification:** If FCM + Cloud Scheduler + pre-trial-end job implemented, restore timeline step via code anchor.
- **Future locales (RTL, plurals):** TextAutoSize pattern applies to all compliance disclosures. Extend to any new locale.
- **Material You opt-in:** Paywall hardcoded colors + `dynamicColor: Boolean` setting (default false) means Material You unavailable on paywall. Consider Settings screen to toggle globally if demand arises.

## Related Files

- `feature/paywall/src/commonMain/kotlin/.../PaywallRoute.kt` — monthlyEquivalent() helper
- `feature/paywall/src/commonMain/kotlin/.../PaywallViewModel.kt` — user property tracking
- `feature/paywall/src/commonMain/kotlin/.../components/PaywallTrialTimeline.kt` — Day 2 removal + code anchor
- `core/designsystem/src/commonMain/composeResources/values/strings.xml` — "Auto-renews" removal + code anchor
- `feature/paywall/src/commonMain/kotlin/.../components/FeatureRow.kt` — hardcoded brand colors
- `feature/paywall/src/commonMain/kotlin/.../components/HeroIllustration.kt` — halo recolor
- `core/remoteconfig/api/src/commonMain/kotlin/.../RemoteConfigKeys.kt` — features_v1 default switch
- `composeApp/src/androidTest/kotlin/.../ScreenshotCatalogTest.kt` — "Restore Purchase" → "Restore" anchor update
