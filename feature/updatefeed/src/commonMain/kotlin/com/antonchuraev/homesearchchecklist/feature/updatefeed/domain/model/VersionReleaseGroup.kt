package com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model

/**
 * A group of [UpdatePost]s that belong to the same app version release.
 *
 * Used by the UI to render one "release card" per version in the Google Play
 * release-notes style (version header + optional store description + feature item rows).
 *
 * @param version           App version string in `X.Y` format, e.g. "1.11".
 * @param publishedAtMillis Max [UpdatePost.publishedAtMillis] across all posts in the group,
 *                          or [ReleaseNoteEntry.publishedAtMillis] when the group has no posts.
 *                          Used only for sorting groups — not displayed in UI.
 * @param storeDescription  Optional Google Play release-note text. Displayed above the feature
 *                          item rows inside the card. Supports emoji and `\n` line breaks.
 * @param posts             Posts that belong to this version, sorted descending by publishedAtMillis.
 *                          May be empty (bug-fix-only releases that have a [storeDescription]
 *                          but no individual feature posts).
 */
data class VersionReleaseGroup(
    val version: String,
    val publishedAtMillis: Long,
    val storeDescription: String? = null,
    val posts: List<UpdatePost>
)
