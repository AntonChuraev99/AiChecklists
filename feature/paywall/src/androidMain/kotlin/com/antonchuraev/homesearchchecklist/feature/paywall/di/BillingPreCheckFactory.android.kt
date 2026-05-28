package com.antonchuraev.homesearchchecklist.feature.paywall.di

import android.content.Context
import com.antonchuraev.homesearchchecklist.feature.paywall.data.billing.AndroidBillingPlatformPreCheck
import com.antonchuraev.homesearchchecklist.feature.paywall.data.billing.BillingPlatformPreCheck
import org.koin.core.context.GlobalContext

actual fun createBillingPlatformPreCheck(): BillingPlatformPreCheck {
    val context = GlobalContext.get().get<Context>()
    return AndroidBillingPlatformPreCheck(context = context)
}
