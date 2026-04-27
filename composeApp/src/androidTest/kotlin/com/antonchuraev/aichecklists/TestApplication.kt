package com.antonchuraev.aichecklists

import com.antonchuraev.homesearchchecklist.GistiApplication
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/**
 * Test Application that skips RevenueCat initialization.
 *
 * Without this, Test Orchestrator clears app data before each test,
 * causing RevenueCat to create a new anonymous user every time.
 * 50 tests = 50 fake users in the RevenueCat dashboard.
 *
 * Also swaps [PaywallRepository] for [FakePaywallRepository] so screenshot
 * tests see a fully-loaded paywall instead of an "Unable to load" error dialog.
 */
class TestApplication : GistiApplication() {

    override fun onCreate() {
        super.onCreate()
        // Replace the real PaywallRepositoryImpl (which calls Purchases.getOfferings)
        // with a fake that returns two pre-built subscription tiles.
        // loadKoinModules runs after startKoin in super.onCreate(); override = true
        // wins over the existing single<PaywallRepository> binding in paywallFeatureModule.
        loadKoinModules(module {
            single<PaywallRepository> { FakePaywallRepository() }
        })
    }

    override fun initRevenueCat() {
        // Skip RevenueCat initialization in E2E tests
    }
}
