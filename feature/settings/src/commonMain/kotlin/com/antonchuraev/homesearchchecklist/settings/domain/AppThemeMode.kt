package com.antonchuraev.homesearchchecklist.settings.domain

/**
 * Re-export of [com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode].
 *
 * The canonical definition lives in core:datastore:api so that both the root
 * App composable (theme plumbing) and feature:settings share the same type
 * without a circular dependency.
 *
 * This typealias keeps the original domain package path working — code that
 * was already referencing [com.antonchuraev.homesearchchecklist.settings.domain.AppThemeMode]
 * (e.g. SettingsScreenContent, previews) continues to compile unchanged.
 */
typealias AppThemeMode = com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode
