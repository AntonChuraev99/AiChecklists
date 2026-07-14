package com.antonchuraev.homesearchchecklist.feature.checklist.data.sync

import kotlinx.serialization.Serializable

/**
 * A public gallery template read from the top-level Firestore collection
 * `gallery_templates/{slug}` (public read, no client write). Backs the SEO-gallery
 * deep-link `https://app.gisti-ai.com/?g=create&template={slug}`: the app fetches the
 * doc and creates a checklist **as-is** (deterministic, no AI credit).
 *
 * Doc shape (seeded via landing-src/checklists/seed-firestore-templates.mjs):
 *   { slug, category, title, ordered:bool, items:[ { text, note? } ] }
 *
 * - [slug]: Firestore doc id == gallery URL slug == deep-link `template` param.
 * - [category]: gallery category key (travel/moving/…); informational only.
 * - [title]: becomes the created checklist name.
 * - [ordered]: documents intent (an ordered how-to vs an unordered set). The created
 *   checklist always preserves [items] list order; this flag carries no extra behaviour
 *   in the app today (kept so the contract matches the seeded docs and the gallery pages).
 * - [items]: ordered list; each carries [GalleryTemplateItemData.note] which MUST survive
 *   into the created checklist's default fill (create-as-is preserves notes).
 */
@Serializable
data class GalleryTemplateSyncData(
    val slug: String,
    val category: String = "",
    val title: String,
    val ordered: Boolean = false,
    val items: List<GalleryTemplateItemData> = emptyList(),
)

@Serializable
data class GalleryTemplateItemData(
    val text: String,
    val note: String? = null,
)
