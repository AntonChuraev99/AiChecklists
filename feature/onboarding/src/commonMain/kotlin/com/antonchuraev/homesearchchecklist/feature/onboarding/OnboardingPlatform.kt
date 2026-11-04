package com.antonchuraev.homesearchchecklist.feature.onboarding

/**
 * True on the web (wasmJs) build. A source-set fact, not a string compared against
 * `getPlatformName()`: a renamed platform string cannot silently turn the web's "sign in with Google
 * to use AI" box (FEATURE_SPEC §5.3) into the generic one.
 */
internal expect val isWebTarget: Boolean
