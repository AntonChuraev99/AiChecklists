package com.antonchuraev.homesearchchecklist.feature.paywall.di

import com.antonchuraev.homesearchchecklist.feature.paywall.data.repository.PaywallRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository

actual fun createPaywallRepository(): PaywallRepository = PaywallRepositoryImpl()
