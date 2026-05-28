package com.antonchuraev.homesearchchecklist.feature.paywall.data.billing

class WebBillingPlatformPreCheck : BillingPlatformPreCheck {
    override suspend fun check(): PreCheckResult = PreCheckResult.Ok
}
