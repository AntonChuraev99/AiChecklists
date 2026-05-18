package com.antonchuraev.homesearchchecklist.settings.domain

/**
 * Re-export of [com.antonchuraev.homesearchchecklist.core.datastore.api.AppLanguage].
 *
 * The canonical definition lives in core:datastore:api so that both the root
 * App composable (locale plumbing) and feature:settings share the same type
 * without a circular dependency.
 *
 * This typealias mirrors the [AppThemeMode] pattern in this same package —
 * code that references [com.antonchuraev.homesearchchecklist.settings.domain.AppLanguage]
 * (e.g. SettingsScreenContent, previews) compiles unchanged.
 */
typealias AppLanguage = com.antonchuraev.homesearchchecklist.core.datastore.api.AppLanguage
