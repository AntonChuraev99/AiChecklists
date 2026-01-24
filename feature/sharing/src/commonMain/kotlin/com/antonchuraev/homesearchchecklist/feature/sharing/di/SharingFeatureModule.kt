package com.antonchuraev.homesearchchecklist.feature.sharing.di

import com.antonchuraev.homesearchchecklist.feature.sharing.domain.formatter.ChecklistFormatter
import com.antonchuraev.homesearchchecklist.feature.sharing.presentation.ShareViewModel
import com.antonchuraev.homesearchchecklist.feature.sharing.presentation.pdf.PdfGenerator
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharingFeatureModule = module {
    // Formatter
    single { ChecklistFormatter() }

    // PDF Generator
    single { PdfGenerator() }

    // ViewModel with checklistId parameter
    viewModel { (checklistId: Long) ->
        ShareViewModel(
            checklistId = checklistId,
            repository = get(),
            navigator = get(),
            formatter = get(),
            pdfGenerator = get()
        )
    }
}
