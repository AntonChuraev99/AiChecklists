package com.antonchuraev.homesearchchecklist.feature.paywall.di

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.paywall.data.repository.PaywallRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import org.koin.core.context.GlobalContext

actual fun createPaywallRepository(): PaywallRepository {
    // Resolve AppLogger from Koin if available — safe null fallback if called before DI init.
    val logger = runCatching { GlobalContext.get().get<AppLogger>() }.getOrNull()
    return PaywallRepositoryImpl(logger = logger)
}
