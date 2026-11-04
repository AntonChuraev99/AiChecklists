package com.antonchuraev.aichecklists.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates `baseline-prof.txt` + `startup-prof.txt` for :androidApp.
 *
 * Run: `./gradlew :androidApp:generateBaselineProfile` (connected device/emulator, API 33+ or rooted).
 * Output is copied to `androidApp/src/release/generated/baselineProfiles/` — commit those files.
 * Use an emulator or a device whose data is Google-synced: the connected run UNINSTALLS the target
 * app at the end, and anonymous local data does not come back. With several devices connected,
 * prefix the command with `ANDROID_SERIAL=<serial>` to run on that one only.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /** Startup only — feeds the Startup Profile (DEX layout) as well as the Baseline Profile. */
    @Test
    fun startup() = rule.collect(
        packageName = targetAppId(),
        includeInStartupProfile = true,
    ) {
        startToInbox()
    }

    /** Post-startup interactions — Baseline Profile only, kept out of the Startup Profile. */
    @Test
    fun openActions() = rule.collect(
        packageName = targetAppId(),
        includeInStartupProfile = false,
    ) {
        startToInbox()
        openActionsJourney()
    }
}
