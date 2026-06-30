package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.user.data.device.getPlatformName
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import aichecklists.core.designsystem.generated.resources.drawer_promo_android_bottom
import aichecklists.core.designsystem.generated.resources.drawer_promo_android_cd
import aichecklists.core.designsystem.generated.resources.drawer_promo_android_top
import aichecklists.core.designsystem.generated.resources.drawer_promo_web_bottom
import aichecklists.core.designsystem.generated.resources.drawer_promo_web_cd
import aichecklists.core.designsystem.generated.resources.drawer_promo_web_top
import aichecklists.core.designsystem.generated.resources.nav_ai_chat
import aichecklists.core.designsystem.generated.resources.drawer_account
import aichecklists.core.designsystem.generated.resources.drawer_item_home
import aichecklists.core.designsystem.generated.resources.drawer_sign_in
import aichecklists.core.designsystem.generated.resources.drawer_sign_out
import aichecklists.core.designsystem.generated.resources.today_title
import aichecklists.core.designsystem.generated.resources.drawer_section_about
import aichecklists.core.designsystem.generated.resources.drawer_section_help
import aichecklists.core.designsystem.generated.resources.drawer_section_navigate
import aichecklists.core.designsystem.generated.resources.drawer_version_label
import aichecklists.core.designsystem.generated.resources.main_menu_leave_feedback
import aichecklists.core.designsystem.generated.resources.main_menu_rate_app
import aichecklists.core.designsystem.generated.resources.main_menu_support
import aichecklists.core.designsystem.generated.resources.paywall_privacy
import aichecklists.core.designsystem.generated.resources.paywall_terms
import aichecklists.core.designsystem.generated.resources.settings_title
import aichecklists.core.designsystem.generated.resources.update_feed_menu_item
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Shared Navigation Drawer content for all drawer-accessible destinations.
 * Wrap in ModalDrawerSheet at each composable route that owns its own
 * DrawerState.
 *
 * MD3 Affordance Scope: every in-app destination reachable from this drawer
 * must own a ModalNavigationDrawer and render a hamburger affordance in its
 * TopAppBar — otherwise the drawer is lost after navigation. See
 * material-3-skill references/navigation-patterns.md "Drawer Affordance Scope".
 */
object DrawerDestination {
    const val Main = "main"
    const val UpdateFeed = "update_feed"
    const val Settings = "settings"
    const val Today = "today"
    const val Calendar = "calendar"
    const val AiChat = "ai_chat"
}

@Composable
fun AppNavigationDrawerContent(
    selectedItemId: String,
    onCloseDrawer: () -> Unit,
    onHomeClick: () -> Unit,
    onTodayClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onAiChatClick: () -> Unit = {},
    onUpdateFeedClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRateAppClick: () -> Unit,
    onLeaveFeedbackClick: () -> Unit,
    versionName: String,
    isGoogleLinked: Boolean = false,
    googleEmail: String? = null,
    googleDisplayName: String? = null,
    onSignInClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val drawerItemColors = NavigationDrawerItemDefaults.colors(
        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        unselectedIconColor = MaterialTheme.colorScheme.onSurface,
    )

    // Whole drawer (header + sections + footer) scrolls as one region.
    // Adapts to small viewports (landscape, foldables, large font scales).
    // ModalDrawerSheet already applies DrawerDefaults.windowInsets, so we
    // must NOT add statusBarsPadding/navigationBarsPadding here — it would
    // double-pad.
    val platformName = remember { getPlatformName() }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        // Cross-promo store badge: points the user to the OTHER platform.
        // Lives inside the scrollable Column (not a pinned footer) so it never
        // gets clipped on landscape / large font scales.
        DrawerStorePromoBadge(
            platformName = platformName,
            onClick = { url ->
                onCloseDrawer()
                uriHandler.openUri(url)
            },
        )

        if (isGoogleLinked && googleEmail != null) {
            GoogleProfileSection(
                displayName = googleDisplayName,
                email = googleEmail,
                onSignOutClick = {
                    onCloseDrawer()
                    onSignOutClick()
                },
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = AppDimens.SpacingLg)
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

        DrawerSectionLabel(stringResource(Res.string.drawer_section_navigate))
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.drawer_item_home)) },
            icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
            selected = selectedItemId == DrawerDestination.Main,
            onClick = {
                onCloseDrawer()
                onHomeClick()
            },
            colors = drawerItemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.calendar_nav_label)) },
            icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            selected = selectedItemId == DrawerDestination.Calendar,
            onClick = {
                onCloseDrawer()
                onCalendarClick()
            },
            colors = drawerItemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_ai_chat)) },
            icon = { Icon(Icons.Outlined.SmartToy, contentDescription = null) },
            selected = selectedItemId == DrawerDestination.AiChat,
            onClick = {
                onCloseDrawer()
                onAiChatClick()
            },
            colors = drawerItemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.update_feed_menu_item)) },
            icon = { Icon(Icons.Outlined.Campaign, contentDescription = null) },
            selected = selectedItemId == DrawerDestination.UpdateFeed,
            onClick = {
                onCloseDrawer()
                onUpdateFeedClick()
            },
            colors = drawerItemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.settings_title)) },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            selected = selectedItemId == DrawerDestination.Settings,
            onClick = {
                onCloseDrawer()
                onSettingsClick()
            },
            colors = drawerItemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        if (isGoogleLinked) {
            NavigationDrawerItem(
                label = { Text(stringResource(Res.string.drawer_account)) },
                icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onSignInClick()
                },
                colors = drawerItemColors,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        } else {
            NavigationDrawerItem(
                label = { Text(stringResource(Res.string.drawer_sign_in)) },
                icon = { Icon(Icons.Outlined.Login, contentDescription = null) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onSignInClick()
                },
                colors = drawerItemColors,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        DrawerSectionLabel(stringResource(Res.string.drawer_section_help))
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.main_menu_rate_app)) },
            icon = { Icon(Icons.Outlined.Star, contentDescription = null) },
            selected = false,
            onClick = {
                onCloseDrawer()
                onRateAppClick()
            },
            colors = drawerItemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.main_menu_leave_feedback)) },
            icon = { Icon(Icons.Outlined.Feedback, contentDescription = null) },
            selected = false,
            onClick = {
                onCloseDrawer()
                onLeaveFeedbackClick()
            },
            colors = drawerItemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.main_menu_support)) },
            icon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) },
            selected = false,
            onClick = {
                onCloseDrawer()
                uriHandler.openUri("mailto:${PaywallConfig.SUPPORT_EMAIL}")
            },
            colors = drawerItemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        DrawerSectionLabel(stringResource(Res.string.drawer_section_about))
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.paywall_privacy)) },
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            selected = false,
            onClick = {
                onCloseDrawer()
                uriHandler.openUri(PaywallConfig.PRIVACY_POLICY_URL)
            },
            colors = drawerItemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.paywall_terms)) },
            icon = { Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = null) },
            selected = false,
            onClick = {
                onCloseDrawer()
                uriHandler.openUri(PaywallConfig.TERMS_OF_USE_URL)
            },
            colors = drawerItemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        DrawerFooter(versionName)
    }
}

/**
 * Cross-promotion store badge in the drawer header. Surfaces the OTHER platform:
 * - on Web (wasmJs) → "GET IT ON Google Play" → [GOOGLE_PLAY_URL]
 * - on Android (and iOS / anything else, since iOS isn't shipped) → the web app at
 *   [WEB_APP_URL].
 *
 * Styled as a distinct outlined badge (not a NavigationDrawerItem) per design ask,
 * mirroring the swapface StoreBadge: surfaceContainerHigh fill, outlineVariant
 * border, 12dp corners, two-line label (small caps top / bold bottom). Horizontal
 * padding matches the section items so it aligns with the list below.
 */
@Composable
private fun DrawerStorePromoBadge(
    platformName: String,
    onClick: (url: String) -> Unit,
) {
    val isWeb = platformName == "web"
    val targetUrl = if (isWeb) GOOGLE_PLAY_URL else WEB_APP_URL
    val topRes = if (isWeb) Res.string.drawer_promo_android_top else Res.string.drawer_promo_web_top
    val bottomRes = if (isWeb) Res.string.drawer_promo_android_bottom else Res.string.drawer_promo_web_bottom
    // stringResource resolves async on wasmJs. These promo strings are first requested
    // only when the drawer opens, so the first composition can land before the value
    // loads — and it didn't recompose until an unrelated invalidation (window resize),
    // leaving the label blank. produceState + the suspend getString forces a recomposition
    // the moment the value arrives, so the label appears on first open.
    val topLine by produceState("", topRes) { value = getString(topRes) }
    val bottomLine by produceState("", bottomRes) { value = getString(bottomRes) }
    val cd = stringResource(
        if (isWeb) Res.string.drawer_promo_android_cd else Res.string.drawer_promo_web_cd
    )

    val badgeShape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimens.SpacingLg,
                vertical = AppDimens.SpacingSm
            )
            .clip(badgeShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, badgeShape)
            .clickable { onClick(targetUrl) }
            .semantics { contentDescription = cd }
            .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingMd),
    ) {
        if (isWeb) {
            // Official multi-color Google Play glyph as a code-built ImageVector
            // (see GooglePlayLogo) — NOT the XML drawable, whose runtime parser
            // renders blank on wasmJs/Skiko. contentDescription is null — the badge
            // Row already owns the semantic label.
            Image(
                imageVector = GooglePlayLogo,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Column {
            Text(
                text = topLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = bottomLine,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// Cross-promo URLs. NOTE: GOOGLE_PLAY_URL duplicates feature:paywall's internal
// GISTI_GOOGLE_PLAY_URL (same value) — that const is module-internal and not
// reachable from composeApp. Candidate for future consolidation into a shared
// core constant.
private const val WEB_APP_URL = "https://gisti-ai.com/"
private const val GOOGLE_PLAY_URL =
    "https://play.google.com/store/apps/details?id=com.antonchuraev.aichecklists"

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = AppDimens.SpacingLg,
            end = AppDimens.SpacingLg,
            top = AppDimens.SpacingMd,
            bottom = AppDimens.SpacingSm
        )
    )
}

@Composable
private fun DrawerFooter(versionName: String) {
    if (versionName.isNotBlank()) {
        // No navigationBarsPadding here: ModalDrawerSheet already applies
        // DrawerDefaults.windowInsets (= WindowInsets.systemBars) to its Surface.
        Text(
            text = stringResource(Res.string.drawer_version_label, versionName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingMd)
        )
    }
}

@Composable
private fun GoogleProfileSection(
    displayName: String?,
    email: String,
    onSignOutClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        // Avatar circle with first letter of display name (or email initial)
        val initial = (displayName?.firstOrNull() ?: email.firstOrNull())
            ?.uppercaseChar()
            ?.toString()
            ?: "?"
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        ) {
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.drawer_sign_out),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onSignOutClick),
            )
        }
    }
}
