package com.antonchuraev.homesearchchecklist.feature.paywall.di

import com.antonchuraev.homesearchchecklist.feature.paywall.data.billing.BillingPlatformPreCheck
import com.antonchuraev.homesearchchecklist.feature.paywall.data.billing.IosBillingPlatformPreCheck

actual fun createBillingPlatformPreCheck(): BillingPlatformPreCheck = IosBillingPlatformPreCheck()
