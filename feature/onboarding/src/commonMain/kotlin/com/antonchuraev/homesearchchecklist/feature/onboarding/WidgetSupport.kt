package com.antonchuraev.homesearchchecklist.feature.onboarding

/**
 * Returns true if the platform supports home screen widgets.
 * Used to conditionally show the "Add Widget" card in onboarding.
 */
expect fun isWidgetSupported(): Boolean
