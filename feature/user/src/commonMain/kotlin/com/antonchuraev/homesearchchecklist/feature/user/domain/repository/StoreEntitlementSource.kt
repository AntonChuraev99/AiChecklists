package com.antonchuraev.homesearchchecklist.feature.user.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The store-side half of "is this user Premium": the live RevenueCat entitlement
 * (`AiChecklists Pro`). The project rule is `premium = revenueCat || firestore` — the Firestore half
 * is [com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData.isPremium].
 *
 * Declared here, implemented in `feature/paywall` (which owns RevenueCat) and bound in its Koin
 * module, so features that already depend on `feature/user` (AI chat) can read the store status
 * without a module edge to `feature/paywall`. Consumers must treat a missing binding as "not
 * entitled" and still OR it with the Firestore flag.
 */
interface StoreEntitlementSource {
    /** True while the store reports an active Premium entitlement. */
    val isEntitled: Flow<Boolean>
}
