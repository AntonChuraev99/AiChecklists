package com.antonchuraev.homesearchchecklist.feature.paywall.data.billing

class IosBillingPlatformPreCheck : BillingPlatformPreCheck {
    override suspend fun check(): PreCheckResult = PreCheckResult.Ok
}
