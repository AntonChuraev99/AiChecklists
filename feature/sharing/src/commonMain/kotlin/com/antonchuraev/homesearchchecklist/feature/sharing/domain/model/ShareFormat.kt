package com.antonchuraev.homesearchchecklist.feature.sharing.domain.model

sealed interface ShareFormat {
    data object Text : ShareFormat
    data object Pdf : ShareFormat
}
