package com.antonchuraev.aichecklists

import com.antonchuraev.homesearchchecklist.GistiApplication

/**
 * Test Application that skips RevenueCat initialization.
 *
 * Without this, Test Orchestrator clears app data before each test,
 * causing RevenueCat to create a new anonymous user every time.
 * 50 tests = 50 fake users in the RevenueCat dashboard.
 */
class TestApplication : GistiApplication() {

    override fun initRevenueCat() {
        // Skip RevenueCat initialization in E2E tests
    }
}
