package com.antonchuraev.homesearchchecklist.feature.sharing.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.sharing.domain.formatter.ChecklistFormatter
import com.antonchuraev.homesearchchecklist.feature.sharing.domain.model.ShareFormat
import com.antonchuraev.homesearchchecklist.feature.sharing.presentation.pdf.PdfGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ShareViewModel(
    private val checklistId: Long,
    private val repository: ChecklistRepository,
    private val navigator: AppNavigator,
    private val formatter: ChecklistFormatter,
    private val pdfGenerator: PdfGenerator
) : AppViewModel<ShareScreenState, ShareScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(ShareScreenState())
    override val screenState: StateFlow<ShareScreenState> = _screenState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val checklist = repository.getChecklistById(checklistId)
            if (checklist == null) {
                _screenState.update { it.copy(isLoading = false, error = "Checklist not found") }
                return@launch
            }

            repository.getDefaultFillByChecklistId(checklistId).collect { fill ->
                _screenState.update {
                    it.copy(
                        checklist = checklist,
                        checklistFill = fill,
                        isLoading = false
                    )
                }
            }
        }
    }

    override fun onIntent(intent: ShareScreenIntent) {
        when (intent) {
            ShareScreenIntent.OnBackClick -> navigator.onBack()

            is ShareScreenIntent.OnFormatSelected -> {
                _screenState.update {
                    it.copy(
                        selectedFormat = intent.format,
                        generatedPdfPath = null,
                        formattedText = null,
                        error = null
                    )
                }
            }

            ShareScreenIntent.OnShareClick -> prepareAndShare()

            ShareScreenIntent.OnShareComplete -> {
                _screenState.update { it.copy(shouldShare = false) }
            }
        }
    }

    private fun prepareAndShare() {
        val state = _screenState.value
        val checklist = state.checklist ?: return
        val fill = state.checklistFill ?: return
        val format = state.selectedFormat ?: return

        when (format) {
            ShareFormat.Text -> {
                val text = formatter.formatAsText(checklist, fill)
                _screenState.update {
                    it.copy(
                        formattedText = text,
                        shouldShare = true
                    )
                }
            }

            ShareFormat.Pdf -> {
                if (state.isGeneratingPdf) return

                _screenState.update { it.copy(isGeneratingPdf = true, error = null) }

                viewModelScope.launch {
                    val pdfContent = formatter.formatForPdf(checklist, fill)
                    val fileName = sanitizeFileName(checklist.name)
                    val pdfPath = pdfGenerator.generatePdf(pdfContent, fileName)

                    if (pdfPath != null) {
                        _screenState.update {
                            it.copy(
                                isGeneratingPdf = false,
                                generatedPdfPath = pdfPath,
                                shouldShare = true
                            )
                        }
                    } else {
                        _screenState.update {
                            it.copy(
                                isGeneratingPdf = false,
                                error = "Failed to generate PDF"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "_")
            .take(50)
            .ifBlank { "checklist" }
    }
}
