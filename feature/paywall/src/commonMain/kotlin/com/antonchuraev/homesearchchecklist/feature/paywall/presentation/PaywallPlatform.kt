package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

/**
 * True only on the wasmJs target, where in-app purchases are not available
 * and the paywall is replaced by an "install the mobile app" CTA.
 */
internal expect val isWebPaywallTarget: Boolean
