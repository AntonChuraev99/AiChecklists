package com.antonchuraev.aichecklists

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.antonchuraev.homesearchchecklist.MainActivity
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * Base class for UI tests providing common setup and utilities.
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Wait for the app to be idle before assertions.
     */
    protected fun waitForIdle() {
        composeTestRule.waitForIdle()
    }

    /**
     * Wait for a specific condition with timeout.
     */
    protected fun waitUntil(timeoutMillis: Long = 5000, condition: () -> Boolean) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis, condition = condition)
    }

    /**
     * Press the device back button.
     */
    protected fun pressBack() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressBack()
    }
}
