package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.drawer_item_home
import aichecklists.core.designsystem.generated.resources.today_title
import aichecklists.core.designsystem.generated.resources.drawer_logo_content_description
import aichecklists.core.designsystem.generated.resources.drawer_section_about
import aichecklists.core.designsystem.generated.resources.drawer_section_help
import aichecklists.core.designsystem.generated.resources.drawer_section_navigate
import aichecklists.core.designsystem.generated.resources.drawer_tagline
import aichecklists.core.designsystem.generated.resources.drawer_version_label
import aichecklists.core.designsystem.generated.resources.main_menu_leave_feedback
import aichecklists.core.designsystem.generated.resources.main_menu_rate_app
import aichecklists.core.designsystem.generated.resources.main_menu_support
import aichecklists.core.designsystem.generated.resources.paywall_privacy
import aichecklists.core.designsystem.generated.resources.paywall_terms
import aichecklists.core.designsystem.generated.resources.settings_title
import aichecklists.core.designsystem.generated.resources.update_feed_menu_item
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
}

@Composable
fun AppNavigationDrawerContent(
    selectedItemId: String,
    onCloseDrawer: () -> Unit,
    onHomeClick: () -> Unit,
    onTodayClick: () -> Unit = {},
    onUpdateFeedClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRateAppClick: () -> Unit,
    onLeaveFeedbackClick: () -> Unit,
    versionName: String,
) {
    val uriHandler = LocalUriHandler.current
    val drawerItemColors = NavigationDrawerItemDefaults.colors(
        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        unselectedIconColor = MaterialTheme.colorScheme.onSurface,
    )

    Column(modifier = Modifier.fillMaxHeight()) {
        DrawerBrandHeader()

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
            label = { Text(stringResource(Res.string.today_title)) },
            icon = { Icon(Icons.Outlined.WbSunny, contentDescription = null) },
            selected = selectedItemId == DrawerDestination.Today,
            onClick = {
                onCloseDrawer()
                onTodayClick()
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

        Spacer(modifier = Modifier.weight(1f))
        DrawerFooter(versionName)
    }
}

@Composable
private fun DrawerBrandHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimens.SpacingLg,
                vertical = AppDimens.SpacingLg
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = stringResource(Res.string.drawer_logo_content_description),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Column {
            Text(
                text = "Gisti",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.drawer_tagline),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
        Text(
            text = stringResource(Res.string.drawer_version_label, versionName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingMd)
                .navigationBarsPadding()
        )
    }
}
