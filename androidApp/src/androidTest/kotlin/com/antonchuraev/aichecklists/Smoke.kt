package com.antonchuraev.aichecklists

/**
 * Annotation for smoke tests - fast, critical tests that catch major regressions.
 *
 * Smoke suite consists of 10 tests covering all key user flows:
 * - EndToEndFlowTest: 4 tests (complete user journeys)
 * - OnboardingFlowTest: 2 tests (first launch experience)
 * - MainScreenFlowTest: 1 test (empty state display)
 * - CreateChecklistFlowTest: 1 test (validation)
 * - AnalyzeFlowTest: 1 test (AI screen display)
 * - CreditsFlowTest: 1 test (paywall navigation)
 *
 * Expected runtime: ~2.8 minutes (with Test Orchestrator overhead)
 *
 * Run smoke tests only:
 * ```
 * ./gradlew connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.annotation=com.antonchuraev.aichecklists.Smoke
 * ```
 *
 * Run all tests (full suite):
 * ```
 * ./gradlew connectedDebugAndroidTest
 * ```
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Smoke
