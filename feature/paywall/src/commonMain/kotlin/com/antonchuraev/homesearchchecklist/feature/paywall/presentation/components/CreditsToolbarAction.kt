package com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCreditsChip
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium.CreditsBadgeProvider
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium.PremiumEntryPoint
import org.koin.compose.koinInject

/**
 * Analytics `source` of every always-on credits chip in the v2 navigation arm.
 *
 * ONE value per tab, never a shared "v2_credits_chip". The paywall's `source` is the only signal
 * that distinguishes these four surfaces in the funnel, and the question they exist to answer —
 * *which* tab actually carries people into the paywall — is unanswerable if they all report the same
 * string. (Precedent: `AppCreditsChip` on the v1 home is the app's best converter, 4 purchases out
 * of 7 in 90 days, and that is only knowable because it reports `main_credits_chip` alone.)
 *
 * ⚠️ Wire values. Renaming one silently breaks the dashboards built on it; add, never rename.
 */
object CreditsChipSource {
    const val V2_INBOX = "v2_inbox_credits_chip"
    const val V2_CALENDAR = "v2_calendar_credits_chip"
    const val V2_PROJECTS = "v2_projects_credits_chip"
    const val V2_OVERVIEW = "v2_overview_credits_chip"
}

/**
 * The credit-balance chip as a self-contained top-bar action: reads its own data, routes its own tap.
 *
 * Drop it into any `AppScaffold(actions = { … })` and the screen needs to know nothing about credits,
 * subscriptions or the paywall — which is what makes it cheap enough to be on EVERY tab. The v2 shell
 * shipped with no paywall entry point at all on any of its four tabs (owner, 2026-08-13: "я сейчас
 * даже не могу найти где пейвол открыть с главного экрана"); a component that costs a host one line
 * is the shape that keeps that from happening again the next time a tab is added.
 *
 * ## Always visible, never a silent no-op
 * The chip renders in every state: a count when the user has credits, the "Get More" CTA at zero, a
 * "PRO" badge for subscribers. It is never hidden, and its tap always lands somewhere real — free
 * users on the paywall (carrying [source]), subscribers on the subscription-status screen. Hiding it
 * at zero credits, or leaving it inert, would re-create exactly the defect it is here to fix.
 *
 * ## Data
 * [CreditsBadgeProvider] is a read-only projection over the repositories that already own credits and
 * subscription state, so four instances of this chip on four tabs cannot disagree. It is seeded
 * synchronously from `UserData` ([CreditsBadgeProvider.currentBadge]) because the subscription flow
 * is cold: without the seed every tab open flashes the zero-credit CTA at a user who has credits.
 *
 * Both collaborators come from Koin rather than from the host's ViewModel — threading credits through
 * four unrelated ViewModels would put four copies of the same wiring in four places, and the one that
 * got forgotten would be invisible (a missing chip looks like a design choice, not a bug).
 *
 * @param source which surface is reporting this tap — one of [CreditsChipSource].
 */
@Composable
fun CreditsToolbarAction(
    source: String,
    modifier: Modifier = Modifier,
) {
    val badgeProvider: CreditsBadgeProvider = koinInject()
    val entryPoint: PremiumEntryPoint = koinInject()

    // remember() on both: `badge()` allocates a new combine on every call, and re-subscribing to it
    // each recomposition would restart the upstream flows on a chip that recomposes with the bar.
    val seed = remember(badgeProvider) { badgeProvider.currentBadge() }
    val badgeFlow = remember(badgeProvider) { badgeProvider.badge() }
    val badge by badgeFlow.collectAsState(initial = seed)

    AppCreditsChip(
        credits = badge.credits,
        isPremium = badge.isPremium,
        onClick = { entryPoint.open(isPremium = badge.isPremium, source = source) },
        modifier = modifier,
    )
}
