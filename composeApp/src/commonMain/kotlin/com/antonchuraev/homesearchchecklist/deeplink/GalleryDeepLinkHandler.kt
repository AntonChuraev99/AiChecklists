package com.antonchuraev.homesearchchecklist.deeplink

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistSource
import com.antonchuraev.homesearchchecklist.feature.create.domain.usecase.CreateChecklistFromGalleryTemplateUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Handles ONE gallery deep-link arrival: analytics funnel + create + outcome, then consumption.
 *
 * Extracted out of `App.kt`'s `LaunchedEffect` so the funnel invariant is unit-testable — the
 * cancel/restart path that produced the prod double-count only exists here, and inside a composable
 * it was unreachable from a test. `App.kt` supplies the UI-bound effects ([onCreated] navigates,
 * [showNotFoundMessage] / [showErrorMessage] raise the snackbar) as plain lambdas.
 *
 * ## Funnel contract
 *
 * `opened = created(gallery) + failed`, and every path out of here emits exactly one of the two —
 * a silent third outcome reads as "traffic that evaporated" in prod. Analytics lives here rather
 * than in the use case: the domain layer must not know about analytics (the same rule that keeps
 * Compose Resources out of it), and this is the only place that sees BOTH the outcome and the
 * deep-link's utm.
 *
 * ## Cancellation is the interesting case
 *
 * An Activity recreation (rotation / theme / locale — MainActivity declares no `configChanges`)
 * tears the collector down mid-create. Two deliberate asymmetries handle it:
 *  - the link is NOT consumed on cancellation (the `isActive` guard below), so the arrival is not
 *    lost and the next collector retries it — this is load-bearing, see [PendingGalleryDeepLink];
 *  - `opened` IS remembered across that retry ([PendingGalleryDeepLink.markOpenedReported]), so
 *    the retry does not count the same arrival a second time.
 *
 * @param createFromGallery injected as a lambda, not the use-case instance, so tests can drive the
 *   Created / NotFound / Error branches (and a hang) without a Firestore + Room stack.
 * @param onCreated invoked with the new checklist id BEFORE navigation; must not suspend.
 */
internal suspend fun handleGalleryDeepLink(
    link: GalleryDeepLink,
    pending: PendingGalleryDeepLink,
    analyticsTracker: AnalyticsTracker,
    createFromGallery: suspend (slug: String) -> CreateChecklistFromGalleryTemplateUseCase.Result,
    onCreated: (checklistId: Long) -> Unit,
    showNotFoundMessage: suspend () -> Unit,
    showErrorMessage: suspend () -> Unit,
) {
    val linkParams: Map<String, Any> =
        mapOf(AnalyticsParams.TEMPLATE_SLUG to link.slug) + link.utm
    // Top-of-funnel: fires BEFORE the fetch, so an arrival is counted even when the create then
    // fails. Without it, "no traffic" and "all slugs broken" look identical. Gated on
    // markOpenedReported so a cancelled-and-retried arrival is still ONE arrival.
    if (pending.markOpenedReported(link)) {
        analyticsTracker.event(AnalyticsEvents.Gallery.DEEPLINK_OPENED, linkParams)
    }
    // try/finally guarantees the pending link is consumed even on an unexpected throw, so the
    // collector cannot get stuck re-processing the same link; the catch guarantees the user still
    // gets feedback instead of a dead screen.
    // terminalEmitted guards the funnel invariant from the other side: the catch below must not add
    // a `failed` for an arrival that already reported `created` (a throw from navigation would
    // otherwise count one arrival twice).
    var terminalEmitted = false
    try {
        when (val result = createFromGallery(link.slug)) {
            is CreateChecklistFromGalleryTemplateUseCase.Result.Created -> {
                analyticsTracker.event(
                    AnalyticsEvents.Checklist.CREATED,
                    linkParams + mapOf(
                        AnalyticsParams.SOURCE to ChecklistSource.GALLERY.wire,
                        AnalyticsParams.CHECKLIST_ID to result.checklistId,
                    ),
                )
                terminalEmitted = true
                onCreated(result.checklistId)
            }
            // A stale slug means the live gallery page and Firestore have drifted apart —
            // a content bug that is otherwise invisible (the user just sees a snackbar).
            CreateChecklistFromGalleryTemplateUseCase.Result.NotFound -> {
                analyticsTracker.event(
                    AnalyticsEvents.Gallery.DEEPLINK_FAILED,
                    linkParams + mapOf(AnalyticsParams.REASON to "not_found"),
                )
                terminalEmitted = true
                showNotFoundMessage()
            }
            is CreateChecklistFromGalleryTemplateUseCase.Result.Error -> {
                analyticsTracker.event(
                    AnalyticsEvents.Gallery.DEEPLINK_FAILED,
                    linkParams + mapOf(
                        AnalyticsParams.REASON to "error",
                        AnalyticsParams.ERROR_MESSAGE to (result.cause.message ?: "unknown"),
                    ),
                )
                terminalEmitted = true
                showErrorMessage()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        if (!terminalEmitted) {
            analyticsTracker.event(
                AnalyticsEvents.Gallery.DEEPLINK_FAILED,
                linkParams + mapOf(
                    AnalyticsParams.REASON to "error",
                    AnalyticsParams.ERROR_MESSAGE to (e.message ?: "unknown"),
                ),
            )
        }
        showErrorMessage()
    } finally {
        // Two guards, and both are load-bearing:
        // - isActive: `finally` also runs on CANCELLATION. An Activity recreation (rotation, theme,
        //   locale — MainActivity declares no configChanges) tears this collector down mid-create.
        //   The holder is app-scoped and survives, so the next collector re-fires the link — unless
        //   we clear it here, which would lose the arrival entirely and re-open the very funnel
        //   hole this block exists to close.
        // - consume(link): identity-scoped, so a newer link submitted by a warm onNewIntent while
        //   this one was still creating is never swallowed.
        if (currentCoroutineContext().isActive) {
            pending.consume(link)
        }
    }
}
