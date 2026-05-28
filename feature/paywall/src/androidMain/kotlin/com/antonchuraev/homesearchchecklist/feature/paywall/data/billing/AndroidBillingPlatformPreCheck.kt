package com.antonchuraev.homesearchchecklist.feature.paywall.data.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidBillingPlatformPreCheck(
    private val context: Context,
) : BillingPlatformPreCheck {

    override suspend fun check(): PreCheckResult {
        // Step 1: GMS availability (synchronous, <10ms)
        val gmsResult = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)
        if (gmsResult != ConnectionResult.SUCCESS) {
            return PreCheckResult.Failed(
                reason = PreCheckFailReason.GoogleApiUnavailable,
                debugMessage = "GMS unavailable: resultCode=$gmsResult"
            )
        }

        // Step 2: BillingClient feature support — needs a real connection.
        // Wrapped in 3s timeout — if Play Store hangs, fall back to Ok and let
        // awaitPurchasesReady() handle it with its own retry logic.
        return withTimeoutOrNull(3000L) {
            suspendCancellableCoroutine<PreCheckResult> { continuation ->
                val client = BillingClient.newBuilder(context)
                    .setListener { _, _ -> } // no-op PurchasesUpdatedListener — only checking features
                    .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                    )
                    .build()

                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        // Handle BILLING_UNAVAILABLE up front — Play Store entirely missing
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
                            client.endConnection()
                            if (continuation.isActive) {
                                continuation.resume(
                                    PreCheckResult.Failed(
                                        reason = PreCheckFailReason.GoogleApiUnavailable,
                                        debugMessage = "Play Billing unavailable on connect: ${billingResult.debugMessage}"
                                    )
                                )
                            }
                            return
                        }

                        // Connection OK — check PRODUCT_DETAILS support
                        val featureResult = client.isFeatureSupported(
                            BillingClient.FeatureType.PRODUCT_DETAILS
                        )
                        client.endConnection()

                        if (!continuation.isActive) return

                        if (featureResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            continuation.resume(PreCheckResult.Ok)
                        } else {
                            continuation.resume(
                                PreCheckResult.Failed(
                                    reason = PreCheckFailReason.ProductDetailsUnsupported,
                                    debugMessage = "PRODUCT_DETAILS unsupported: code=${featureResult.responseCode}, msg=${featureResult.debugMessage}"
                                )
                            )
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        if (continuation.isActive) {
                            continuation.resume(
                                PreCheckResult.Failed(
                                    reason = PreCheckFailReason.GoogleApiUnavailable,
                                    debugMessage = "BillingClient disconnected during pre-flight"
                                )
                            )
                        }
                    }
                })

                continuation.invokeOnCancellation { client.endConnection() }
            }
        } ?: PreCheckResult.Ok // timeout → fall back to Ok, let awaitPurchasesReady handle
    }
}
