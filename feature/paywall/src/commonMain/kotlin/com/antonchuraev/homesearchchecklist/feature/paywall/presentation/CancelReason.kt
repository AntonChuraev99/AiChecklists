package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

/**
 * Why the user dismissed the native purchase sheet. Collected once per app session via the
 * post-cancel reason picker ([PostCancelReasonSheet]).
 *
 * [key] is the analytics WIRE value sent as [com.antonchuraev.homesearchchecklist.core.common.api
 * .AnalyticsParams.REASON]. Renaming a key starts a brand-new series in GA4/Amplitude — change the
 * Kotlin identifier freely, the string only with intent (same contract as AnalyticsEvents).
 *
 * Adding / removing a reason? Update ALL of (String-Resolver-Enum recurring bug):
 *  1. this enum,
 *  2. `CancelReason.labelRes` in [PostCancelReasonSheet],
 *  3. `strings.xml` (EN `values/` + `values-ru/`).
 * The enum→string mapping deliberately lives in the UI layer, not here, so this stays
 * Compose-Resources-free.
 */
enum class CancelReason(val key: String) {
    EXPENSIVE("too_expensive"),
    JUST_LOOKING("just_looking"),
    NEED_INFO("need_info"),
    SUBSCRIPTIONS("not_sure_subscriptions"),
    MISSING_FEATURE("missing_feature"),
    PAYMENT_ISSUE("payment_issue"),
    OTHER("other"),
}
