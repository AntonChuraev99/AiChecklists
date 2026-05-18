package com.antonchuraev.homesearchchecklist.core.datastore.api

/**
 * User-selectable UI language.
 *
 * Stored in DataStore as the enum [name]. The [tag] is the BCP-47 language tag
 * passed to `LocalAppLocale provides …` in the Compose layer:
 *   System  → null  → restore device default
 *   English → "en"  → override
 *   Russian → "ru"  → override
 *
 * Lives in core:datastore:api because the root App composable (locale plumbing)
 * and feature:settings both consume this type.
 */
enum class AppLanguage(val tag: String?) {
    System(null),
    English("en"),
    Russian("ru"),
}
