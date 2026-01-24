package com.antonchuraev.homesearchchecklist.feature.sharing.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.sharing.domain.model.ShareFormat

data class ShareScreenState(
    val checklist: Checklist? = null,
    val checklistFill: ChecklistFill? = null,
    val selectedFormat: ShareFormat? = null,
    val isLoading: Boolean = true,
    val isGeneratingPdf: Boolean = false,
    val generatedPdfPath: String? = null,
    val formattedText: String? = null,
    val shouldShare: Boolean = false,
    val error: String? = null
) : State

sealed interface ShareScreenIntent : Intent {
    data object OnBackClick : ShareScreenIntent
    data class OnFormatSelected(val format: ShareFormat) : ShareScreenIntent
    data object OnShareClick : ShareScreenIntent
    data object OnShareComplete : ShareScreenIntent
}
