package com.antonchuraev.homesearchchecklist.feature.create.presentation.templates

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State

data object TemplatesScreenState : State

sealed interface TemplatesScreenIntent : Intent {
    data object OnBackClick : TemplatesScreenIntent
}
