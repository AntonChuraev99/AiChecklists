package com.antonchuraev.homesearchchecklist.feature.create.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.GalleryTemplateSyncData
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Creates a checklist from a public gallery template (`gallery_templates/{slug}`) AS-IS —
 * deterministic, no AI credit. Backs the SEO-gallery deep-link
 * `https://app.gisti-ai.com/?g=create&template={slug}`.
 *
 * Fetches the template via [FirestoreSyncDataSource.fetchGalleryTemplate], builds a [Checklist]
 * with the items in list order, persists it, then preserves each item's note into the
 * auto-created default fill.
 */
class CreateChecklistFromGalleryTemplateUseCase(
    private val firestoreSyncDataSource: FirestoreSyncDataSource,
    private val checklistRepository: ChecklistRepository,
    private val logger: AppLogger,
) {
    sealed interface Result {
        data class Created(val checklistId: Long) : Result
        data object NotFound : Result
        data class Error(val cause: Throwable) : Result
    }

    /**
     * @param slug the Firestore doc id / gallery URL slug carried on the deep-link.
     *
     * - unknown slug (fetch → Success(null)) → [Result.NotFound]
     * - fetch failure (AppResult.Error) → [Result.Error]
     * - otherwise → [Result.Created] with the new checklist id
     *
     * Items keep the template's list order. The template `ordered` flag documents intent only
     * (an ordered how-to vs an unordered set) and carries no extra behaviour here.
     *
     * NB [ChecklistRepository.addChecklist] already creates the default fill (note=null per item,
     * each linked back via `templateItemId`) — we then load it and back-fill the notes. We do NOT
     * call addFill (that would create a SECOND fill).
     */
    suspend operator fun invoke(slug: String): Result {
        val template: GalleryTemplateSyncData =
            when (val res = firestoreSyncDataSource.fetchGalleryTemplate(slug)) {
                is AppResult.Success -> res.data ?: return Result.NotFound
                is AppResult.Error -> {
                    logger.error(TAG, "fetchGalleryTemplate('$slug') failed: ${res.exception.message}", res.exception)
                    return Result.Error(res.exception)
                }
                // NOT NotFound: mapping Loading to NotFound made the funnel lie. `not_found` is the
                // signal that the live gallery page and Firestore have drifted apart (a content bug
                // worth chasing); a terminal Loading is an unfinished fetch and must not be counted
                // as a stale slug, or every such case pollutes the drift signal.
                AppResult.Loading -> {
                    val e = IllegalStateException("fetchGalleryTemplate('$slug') returned Loading as a terminal value")
                    logger.error(TAG, "unexpected terminal Loading for slug '$slug'", e)
                    return Result.Error(e)
                }
            }

        return runCatching {
            val templateItems = template.items.map { item ->
                ChecklistItem(text = item.text, checked = false)
            }
            val checklist = Checklist(
                name = template.title,
                items = templateItems,
            )
            val checklistId = checklistRepository.addChecklist(checklist)

            // Preserve per-item notes: addChecklist auto-created the default fill with note=null,
            // each fill item linked to its template item via templateItemId. Map templateItemId → note
            // and back-fill only when there is at least one note (skip the fill write otherwise, to
            // avoid needless sync churn for note-less templates).
            val noteByTemplateId: Map<String, String> = templateItems
                .zip(template.items)
                .mapNotNull { (created, source) ->
                    source.note?.takeIf { it.isNotBlank() }?.let { created.id to it }
                }
                .toMap()

            if (noteByTemplateId.isNotEmpty()) {
                // BOUNDED wait. `first()` on a filterNotNull()'d flow suspends FOREVER when the
                // default fill never materialises — and because the checklist is already persisted
                // at this point, the caller then emits neither `checklist_created` nor
                // `gallery_deeplink_failed`, shows no snackbar, and never consumes the pending link.
                // That is exactly the prod symptom: 7 `gallery_deeplink_opened` against 1 created
                // and 0 failed. Notes are a nice-to-have; the checklist is the deliverable, so a
                // timeout degrades to "created without notes" instead of hanging the whole flow.
                val defaultFill = withTimeoutOrNull(DEFAULT_FILL_TIMEOUT_MS) {
                    checklistRepository
                        .getDefaultFillByChecklistId(checklistId)
                        .filterNotNull()
                        .first()
                }
                if (defaultFill == null) {
                    logger.error(
                        TAG,
                        "default fill for checklist $checklistId (slug '$slug') did not appear " +
                            "within ${DEFAULT_FILL_TIMEOUT_MS}ms — checklist created WITHOUT notes",
                        IllegalStateException("default fill timeout"),
                    )
                } else {
                    val withNotes = defaultFill.copy(
                        items = defaultFill.items.map { fillItem ->
                            val note = fillItem.templateItemId?.let { noteByTemplateId[it] }
                            if (note != null) fillItem.withNote(note) else fillItem
                        },
                    )
                    checklistRepository.updateFill(withNotes)
                }
            }

            Result.Created(checklistId)
        }.getOrElse { e ->
            // runCatching catches Throwable, which includes CancellationException — swallowing it
            // breaks structured concurrency and reports a cancelled coroutine as a genuine failure.
            if (e is CancellationException) throw e
            logger.error(TAG, "create from gallery template '$slug' failed: ${e.message}", e)
            Result.Error(e)
        }
    }

    private companion object {
        const val TAG = "GalleryDeepLink"

        /**
         * Upper bound on waiting for the auto-created default fill. Generous on purpose — the fill
         * is written by [ChecklistRepository.addChecklist] in the same transaction, so in practice
         * it is already there; this only guards the pathological case that used to hang forever.
         */
        const val DEFAULT_FILL_TIMEOUT_MS = 5_000L
    }
}
