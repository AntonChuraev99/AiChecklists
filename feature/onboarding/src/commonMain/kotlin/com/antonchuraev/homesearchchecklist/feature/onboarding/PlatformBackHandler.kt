package com.antonchuraev.homesearchchecklist.feature.onboarding

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
