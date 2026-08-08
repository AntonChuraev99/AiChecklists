package com.antonchuraev.homesearchchecklist.feature.home.presentation.projects

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

private const val TAG = "ProjectsViewModel"

/**
 * Projects one checklist + its default fill into a list row.
 *
 * Extracted from the flow and made `internal` so the counting rules can be tested directly:
 * `ChecklistRepository` has ~60 members with no defaults, so covering this through the ViewModel
 * would mean a 150-line fake whose only interesting part is the counting below.
 *
 * ## The rules, and why each is not the obvious one
 * - The count comes from the **fill**, not the template. `checklist.items` is the template; what the
 *   user ticks lives in the fill. Counting the template gives a number that never moves.
 * - **Folder rows do not count.** `ChecklistRepositoryImpl.addChecklist`/`updateChecklist` mirror
 *   EVERY template node into the fill, folders included, and a folder mirror is never checked — so
 *   counting it adds a permanent +1 per folder and makes `isComplete` unreachable. The Inbox toolbar
 *   filters them the same way (`InboxViewModel.toPage`), and the two screens showing different
 *   numbers for the same checklist is exactly what this rule prevents.
 * - A **missing fill** (freshly synced row whose fill has not been created yet) means "nothing has
 *   been checked", so every template leaf counts as open — not "zero items", which would render an
 *   apparently finished checklist.
 * - [ProjectRow.isComplete] requires a NON-EMPTY list. An empty checklist also has zero open items,
 *   and treating it as complete congratulates the user for a list they never wrote.
 */
internal fun toProjectRow(checklist: Checklist, fill: ChecklistFill?): ProjectRow {
    val folderTemplateIds = checklist.items.filter { it.isFolder }.map { it.id }.toSet()
    val items = fill?.items
        ?.filterNot { item -> item.templateItemId?.let { it in folderTemplateIds } == true }
    val total = items?.size ?: checklist.items.count { !it.isFolder }
    val open = items?.count { !it.checked } ?: total
    return ProjectRow(
        checklistId = checklist.id,
        title = checklist.name,
        openCount = open,
        isComplete = total > 0 && open == 0,
    )
}

/**
 * Backs the v2 "Projects" tab: a flat list of checklists with their open-task counts.
 *
 * ## Why not MainScreenViewModel
 * That one backs the v1 HOME screen and carries everything that screen needs — edit mode, drag
 * reordering, cover images, the sync banner, the activation hero, the chat dock's state, the CSAT
 * hook. The v2 tab needs a name and a number. Reusing it would have meant every change to this tab
 * touching the v1 home screen's state machine, and the classic layout has to keep working exactly
 * as it does today.
 *
 * ## The count is computed from the FILL, not the template
 * `checklist.items` is the template; what the user checks off lives in the default fill. Counting
 * template items would show a stale number that never moves as tasks get done. The template is used
 * only as the fallback for a checklist whose fill has not been created yet (a freshly synced row),
 * where "no fill" means "nothing checked", not "no items".
 */
class ProjectsViewModel(
    private val repository: ChecklistRepository,
    private val navigator: AppNavigator,
    private val logger: AppLogger,
) : AppViewModel<ProjectsScreenState, ProjectsIntent, Nothing>() {

    /** Incrementing this re-subscribes the checklist stream through flatMapLatest. */
    private val _retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val screenState: StateFlow<ProjectsScreenState> = _retryTrigger
        .flatMapLatest { attempt ->
            // Emit Loading on retries only. screenState is a StateFlow, which conflates equal
            // values, and Error is a data object equal to itself: without a distinct value in
            // between, a retry that fails again re-emits Error == Error, nothing recomposes, and
            // the user sees a frozen screen after tapping Try Again. Gating on attempt > 0 keeps
            // the first subscription free of a spurious spinner — WhileSubscribed(5000) re-runs
            // this flow whenever the tab is re-entered, and that should show the cached state.
            buildProjectsFlow().onStart { if (attempt > 0) emit(ProjectsScreenState.Loading) }
        }
        .defaultStateIn(ProjectsScreenState.Loading)

    override fun onIntent(intent: ProjectsIntent) {
        when (intent) {
            is ProjectsIntent.OnProjectClick -> navigator.navigateToChecklistDetail(intent.checklistId)

            // The CREATE screen, not the template gallery. Both of this tab's create affordances
            // (toolbar "+" and the empty-state CTA) go through this one branch, so this is the whole
            // change.
            //
            // The gallery used to be the front door because the old create form was a bare
            // name + items pair — a template was the faster way to a useful list. The v2 form owns
            // that job now: it opens focused on the name field, carries the project settings, and
            // offers "Choose a template" as a ROW inside itself, so the gallery is one tap further
            // away rather than gone. Landing on the gallery instead skips the step the user asked
            // for ("new project") and makes naming a list a two-screen detour.
            //
            // v2-only surface, so the classic arm is untouched: the Projects tab does not exist
            // there, and the v1 home screen keeps its own routing (MainScreenViewModel).
            ProjectsIntent.OnCreateChecklistClick -> navigator.navigateToCreateChecklistScreen()

            ProjectsIntent.OnRetry -> _retryTrigger.update { it + 1 }
        }
    }

    /**
     * Builds the row list, once per [_retryTrigger] emission.
     *
     * [catch] is mandatory, not defensive dressing: an exception here cancels the [defaultStateIn]
     * sharing scope and the StateFlow stays pinned on Loading forever — an infinite spinner with no
     * crash and no log. Same failure this project already hit on MainScreen and Today, and the same
     * resolution: a logged, retryable [ProjectsScreenState.Error]. It must NOT be an empty
     * [ProjectsScreenState.Content] — that renders "No checklists yet / Create one", telling a user
     * whose database failed to read that their checklists do not exist.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun buildProjectsFlow(): Flow<ProjectsScreenState> {
        // Explicit Flow<ProjectsScreenState>: without it the chain infers Flow<Content> and the
        // catch below cannot emit Error.
        val content: Flow<ProjectsScreenState> = repository.projects
            .flatMapLatest { checklists ->
                if (checklists.isEmpty()) {
                    // combine() over an empty list never emits at all, so the empty case has to be
                    // its own branch — otherwise a user with no checklists sits on the spinner
                    // forever.
                    flowOf(ProjectsScreenState.Content(emptyList()))
                } else {
                    // One child flow per checklist: a single item checked anywhere re-emits only
                    // that row's count instead of rebuilding the whole list. Same shape as
                    // MainScreenViewModel.checklistsWithProgress and InboxViewModel.observePages.
                    combine(
                        checklists.map { checklist ->
                            repository.getDefaultFillByChecklistId(checklist.id)
                                .map { fill -> toProjectRow(checklist, fill) }
                        }
                    ) { rows -> ProjectsScreenState.Content(rows.toList()) }
                }
            }

        return content.catch { e ->
            logger.error(TAG, "projects_fetch_failed: ${e.message}", e)
            emit(ProjectsScreenState.Error)
        }
    }
}
