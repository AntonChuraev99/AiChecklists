package com.antonchuraev.aichecklists

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Custom test runner that uses [TestApplication] instead of [GistiApplication].
 * This prevents RevenueCat from initializing during E2E tests.
 */
class TestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context
    ): Application {
        return super.newApplication(cl, TestApplication::class.java.name, context)
    }
}
