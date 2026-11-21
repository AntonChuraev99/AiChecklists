package com.antonchuraev.homesearchchecklist.feature.analyze.di

import org.koin.core.module.Module

/**
 * Platform half of the background-analysis graph. Binds exactly the five platform ports from
 * `domain/job/AnalysisJobPorts.kt` + `ImageDownscaler.kt`:
 * [com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLauncher],
 * [com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisPayloadStore],
 * [com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ImageDownscaler],
 * [com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadyNotifier],
 * [com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AppForegroundMonitor].
 *
 * The common pieces it relies on (`AnalysisJobStore`, `AnalysisJobProcessor`, `AppLogger`) are bound
 * elsewhere; the launchers resolve the processor lazily, so module load order does not matter.
 *
 * - Android: WorkManager worker, `filesDir` payloads, BitmapFactory downscale, system notification,
 *   `ProcessLifecycleOwner`.
 * - wasmJs / iOS: app-scope coroutine launcher, in-memory payloads, no downscale, no notification,
 *   always foreground (the wasmJs side is upgraded by `@wasmjs-expert`).
 */
expect val analyzePlatformModule: Module
