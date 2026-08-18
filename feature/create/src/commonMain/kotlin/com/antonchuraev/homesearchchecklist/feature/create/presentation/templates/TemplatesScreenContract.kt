package com.antonchuraev.homesearchchecklist.feature.create.presentation.templates

import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory

data class TemplatesScreenState(
    val isLoading: Boolean = true,
    val categories: List<TemplateCategory> = emptyList(),
    val filteredCategories: List<TemplateCategory> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedTemplate: ChecklistTemplate? = null,
    val showPreviewDialog: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    // Gate: false when free user has hit the checklist creation limit
    val canCreateChecklist: Boolean = true,
    // Gate: false when free user has hit the weekly checklist limit (separate from the
    // overall limit — see UserLimits.canCreateWeeklyChecklist)
    val canCreateWeeklyChecklist: Boolean = true
) : State

sealed interface TemplatesScreenIntent : Intent {
    data object OnBackClick : TemplatesScreenIntent
    data class OnTemplateClick(val template: ChecklistTemplate) : TemplatesScreenIntent
    data object OnDismissPreview : TemplatesScreenIntent
    data object OnCreateFromTemplate : TemplatesScreenIntent
    data object OnDismissError : TemplatesScreenIntent
    data class OnSearchQueryChange(val query: String) : TemplatesScreenIntent
    data object OnToggleSearch : TemplatesScreenIntent

    // Bottom action buttons
    data object OnCreateWeeklyClick : TemplatesScreenIntent

    /**
     * "Create with AI" was tapped on this screen.
     *
     * ANALYTICS ONLY — opening the AI flow means raising the chat dock, which is shell state this
     * ViewModel cannot touch, so the composable also calls the host back (same broadcast shape as
     * the Inbox's add-task row). The emit lives here rather than in the composable so a tap is
     * recorded through the ViewModel like every other action on this screen.
     *
     * The string that labels it, `templates_create_with_ai`, sat in `strings.xml` in all three
     * languages with ZERO call sites: the button is on screenshot 6 of the Play listing and had
     * silently vanished from the build. This intent is what makes its absence measurable next time.
     *
     * Its former neighbour `templates_create_manually` was NOT restored with it, and its string is
     * gone from all three locales. Manual creation was moved off this screen deliberately in the
     * create-flow rework (2026-06-07) and is still reachable — the Projects tab, the shell's "new
     * list" action and the Inbox add-task row all lead there. Bringing the button back is a layout
     * decision about this screen, not a string restore.
     *
     * @param query the user's search text when the door was the empty-search CTA, else null. It is
     *   carried into the AI prompt — someone who typed words we could not match is the highest
     *   intent moment on the screen, and re-asking them what they want throws that away.
     */
    data class OnCreateWithAiClick(
        val source: AiEntrySource,
        val query: String? = null,
    ) : TemplatesScreenIntent
}
