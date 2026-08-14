package com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.Entitlements
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus

/**
 * THE definition of "this user is premium", for the whole app.
 *
 * Premium has two independent sources and either one alone is authoritative:
 *  - RevenueCat's entitlement set (client truth, arrives first after a purchase);
 *  - the Firestore `isPremium` flag mirrored into [UserData] (server truth, survives a broken
 *    RevenueCat session).
 *
 * Neither may be read alone. `products_load_failed` fires on roughly two thirds of catalog loads in
 * production, so RevenueCat-only would show the upsell to people who already pay; Firestore-only
 * lags a fresh purchase by however long the write takes.
 *
 * ## Why a function and not a copy-pasted `||`
 * The same OR used to live inline inside `GetUserLimitsUseCase`, and a second copy was about to be
 * written for the toolbar chip. A gate duplicated across two call sites drifts, and fixing one leaves
 * the other wrong — this project has already shipped that exact defect with a free-tier ceiling
 * (`ToolCallDispatcherImpl`, 2026-08-10). One function, every caller.
 *
 * ⚠️ Read the ENTITLEMENT set, not [SubscriptionStatus.isActive]: `PaywallRepositoryImpl` computes
 * `isActive = isPremium || activeEntitlements.isNotEmpty()`, so `isActive` is also true for a
 * non-premium entitlement.
 *
 * @param subscriptionStatus RevenueCat side.
 * @param firestorePremium   `UserData.isPremium` — the server side.
 */
fun isPremiumUser(subscriptionStatus: SubscriptionStatus, firestorePremium: Boolean): Boolean =
    subscriptionStatus.activeEntitlements.contains(Entitlements.PREMIUM) || firestorePremium
