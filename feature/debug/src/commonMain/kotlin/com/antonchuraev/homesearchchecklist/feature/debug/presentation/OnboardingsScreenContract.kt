package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State

data class OnboardingsState(
    val placeholder: Unit = Unit
) : State

/** Each enum entry is one launchable onboarding variant. Adding a new variant = one new entry. */
enum class OnboardingVariant {
    Interactive,
    Slides,
}

sealed interface OnboardingsIntent : Intent {
    data object OnBack : OnboardingsIntent
    data class LaunchVariant(val variant: OnboardingVariant) : OnboardingsIntent
}
