package com.antonchuraev.homesearchchecklist.feature.paywall.di

import com.antonchuraev.homesearchchecklist.feature.paywall.data.billing.BillingPlatformPreCheck
import com.antonchuraev.homesearchchecklist.feature.paywall.data.billing.WebBillingPlatformPreCheck
import com.antonchuraev.homesearchchecklist.feature.paywall.data.repository.WebPaywallRepositoryStub
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository

actual fun createPaywallRepository(): PaywallRepository = WebPaywallRepositoryStub()

actual fun createBillingPlatformPreCheck(): BillingPlatformPreCheck = WebBillingPlatformPreCheck()
