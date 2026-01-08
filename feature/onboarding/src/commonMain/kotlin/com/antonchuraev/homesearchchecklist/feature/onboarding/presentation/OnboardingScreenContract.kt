package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State

data object OnboardingState : State {

}

sealed interface OnboardingIntent : Intent {

    data object OnComplete : OnboardingIntent


}