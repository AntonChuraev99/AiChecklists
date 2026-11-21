package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.compose.runtime.Composable

/**
 * The notification ask of the Analyze screen (owner decision 3: ask once when the first analysis
 * starts; the analysis runs whatever the answer). The screen calls the returned lambda when
 * `AnalyzeScreenState.askNotificationPermission` turns true and forwards [onResult] as
 * `AnalyzeJobIntent.OnNotificationPermissionResult`.
 *
 * Never opens settings and never blocks: it only shows the system dialog when the OS can still show
 * it. Otherwise [onResult] fires synchronously:
 * - notifications already enabled → `true`;
 * - Android < 13 with notifications off, or a permanent denial recorded by the shared
 *   `NotificationPermissionLedger` → `false` (a dialog is impossible; nagging toward settings at the
 *   moment the user starts an analysis would be the wrong time);
 * - web / iOS → `true` (no OS permission gates delivery there; delivery is in-app).
 */
@Composable
expect fun rememberAnalyzeNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit
