package com.antonchuraev.aichecklists.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing of the post-startup journey (project chip, list fling, task sheet, chat dock,
 * tab switches): no AOT vs the shipped Baseline Profile. The app is started — and an empty Inbox
 * seeded with one task — in setupBlock, so neither startup nor the one-off seed is part of the
 * measured frames, and every iteration of both modes measures the same journey.
 *
 * Run on an emulator or on a device whose data is Google-synced: the connected run UNINSTALLS the
 * target app at the end, and anonymous local data does not come back. With several devices
 * connected, pick one: `ANDROID_SERIAL=<serial> ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.antonchuraev.aichecklists.baselineprofile.OpenActionsBenchmark`
 */
@RunWith(AndroidJUnit4::class)
class OpenActionsBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun openActionsCompilationNone() = openActions(CompilationMode.None())

    @Test
    fun openActionsCompilationBaselineProfile() =
        openActions(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun openActions(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = targetAppId(),
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        setupBlock = {
            startToInbox()
            seedInboxIfEmpty()
        },
    ) {
        openActionsJourney(seedInbox = false)
    }

    private companion object {
        const val ITERATIONS = 5
    }
}
