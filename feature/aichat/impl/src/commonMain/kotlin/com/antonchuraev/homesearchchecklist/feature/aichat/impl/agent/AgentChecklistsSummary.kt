package com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistItemContext
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.first

/**
 * The `checklists_summary` every agent turn sends: the text chat ([ChatViewModel]) and the voice call
 * build it here, so the two can never describe the user's lists differently.
 *
 * Each entry carries the name + counts AND a bounded tail-of-list slice of item text
 * ([ChecklistContext.recentItems]) so the model can answer "what did I add recently / find the task
 * about X". Token budgeting:
 *  - only leaf items are sent; FOLDER nodes are skipped (they carry no user task text);
 *  - per checklist the LAST [RECENT_ITEMS_PER_CHECKLIST] leaves (items are appended, so the tail is
 *    the freshest end);
 *  - a global cap of [RECENT_ITEMS_TOTAL_BUDGET] items across ALL checklists.
 *
 * Recency is POSITIONAL ([ChecklistItemContext.position] = list index): the domain item has no
 * add-timestamp.
 */
internal object AgentChecklistsSummary {

    /** Max checklists in the summary. */
    const val CHECKLIST_SUMMARY_LIMIT = 8

    /** Max recent items sent PER checklist (the freshest tail of the list). */
    const val RECENT_ITEMS_PER_CHECKLIST = 6

    /** Global cap on recent items across ALL checklists in one request. */
    const val RECENT_ITEMS_TOTAL_BUDGET = 30

    /**
     * Reads `projects` once and builds the summary. A failure degrades to an empty summary (the turn
     * still works without list grounding) and is logged under [tag] — never a silent swallow.
     */
    suspend fun load(checklistRepository: ChecklistRepository, logger: AppLogger, tag: String): List<ChecklistContext> =
        runCatching { build(checklistRepository.projects.first()) }
            .getOrElse { e ->
                logger.error(tag, "buildChecklistsSummary failed — ${e.message}", e)
                emptyList()
            }

    /** The pure part of [load]: [projects] in the order they come, capped and budgeted. */
    fun build(projects: List<Checklist>): List<ChecklistContext> {
        var remainingBudget = RECENT_ITEMS_TOTAL_BUDGET
        return projects
            .take(CHECKLIST_SUMMARY_LIMIT)
            .map { checklist ->
                // Index the FULL item list first so position reflects the real list order,
                // then keep only leaves, then take the freshest tail within the global budget.
                val perListCap = minOf(RECENT_ITEMS_PER_CHECKLIST, remainingBudget).coerceAtLeast(0)
                val recent = if (perListCap == 0) {
                    emptyList()
                } else {
                    checklist.items
                        .mapIndexed { index, item -> index to item }
                        .filter { (_, item) -> item.type == ChecklistNodeType.ITEM }
                        .takeLast(perListCap)
                        .map { (index, item) ->
                            ChecklistItemContext(
                                text = item.text,
                                checked = item.checked,
                                position = index,
                            )
                        }
                }
                remainingBudget -= recent.size
                ChecklistContext(
                    name = checklist.name,
                    totalItems = checklist.items.count { it.type == ChecklistNodeType.ITEM },
                    doneItems = checklist.items.count { it.type == ChecklistNodeType.ITEM && it.checked },
                    recentItems = recent,
                )
            }
    }
}
