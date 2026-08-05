package com.antonchuraev.homesearchchecklist.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.main_menu
import aichecklists.core.designsystem.generated.resources.settings_classic_navigation
import aichecklists.core.designsystem.generated.resources.settings_classic_navigation_description
import aichecklists.core.designsystem.generated.resources.settings_dynamic_color
import aichecklists.core.designsystem.generated.resources.settings_dynamic_color_description
import aichecklists.core.designsystem.generated.resources.settings_language
import aichecklists.core.designsystem.generated.resources.settings_language_english
import aichecklists.core.designsystem.generated.resources.settings_language_hindi
import aichecklists.core.designsystem.generated.resources.settings_language_russian
import aichecklists.core.designsystem.generated.resources.settings_language_system
import aichecklists.core.designsystem.generated.resources.settings_theme
import aichecklists.core.designsystem.generated.resources.settings_theme_dark
import aichecklists.core.designsystem.generated.resources.settings_theme_light
import aichecklists.core.designsystem.generated.resources.settings_theme_system
import aichecklists.core.designsystem.generated.resources.settings_title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSwitch
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.settings.domain.AppLanguage
import com.antonchuraev.homesearchchecklist.settings.domain.AppThemeMode
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Pure UI layer for the Settings screen.
 *
 * Responsibilities:
 *   - Display current theme selection (Light / Dark / System default)
 *   - Display the "Dynamic color" (Material You) opt-in toggle when the
 *     platform supports it — hidden entirely otherwise, so the user never
 *     sees a disabled control they cannot act on.
 *   - Delegate all state changes to [onThemeChange] / [onDynamicColorChange]
 *   - Delegate back navigation to [onBackClick]
 *
 * This composable has NO ViewModel / DI dependencies — android-expert wires
 * those separately.
 *
 * Accessibility:
 *   - Each theme row uses [Modifier.selectable] with [Role.RadioButton] →
 *     TalkBack announces "Light, radio button, 1 of 3" etc.
 *   - RadioButton has `onCheckedChange = null` so the parent Row absorbs
 *     the click and avoids double-toggle.
 *   - Dynamic color row uses [Modifier.toggleable] with [Role.Switch] →
 *     TalkBack announces "Dynamic color, switch, on/off".
 *   - Touch targets are ≥ 48dp via [ListItem] default height (56dp).
 *   - Section header uses [primary] for low-emphasis label accent per M3 spec.
 *
 * @param selectedTheme The currently active [AppThemeMode].
 * @param onThemeChange Called when the user selects a different theme option.
 * @param dynamicColorEnabled Whether Material You is currently opted in.
 * @param dynamicColorSupported Whether the current platform can honor the flag.
 *   When `false` the entire Dynamic Color section is hidden.
 * @param onDynamicColorChange Called when the user toggles Material You.
 * @param selectedLanguage The currently active [AppLanguage].
 * @param onLanguageChange Called when the user selects a different language option.
 * @param onBackClick Called when the user taps the back navigation icon.
 * @param modifier Optional modifier applied to the root scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    selectedTheme: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    dynamicColorEnabled: Boolean,
    dynamicColorSupported: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    selectedLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onBackClick: () -> Unit,
    /**
     * True while the app renders the previous (v1) shell. Defaulted so the Android preview file and
     * any other caller that predates this switch keeps compiling and renders the new navigation.
     */
    classicNavigationEnabled: Boolean = false,
    onClassicNavigationChange: (Boolean) -> Unit = {},
    /**
     * Host for the "couldn't save" message the classic-layout switch shows when its write fails.
     * Nullable so previews and screenshot tests, which have no route above them, render without one.
     */
    snackbarHostState: SnackbarHostState? = null,
    drawerState: DrawerState? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    AppScaffold(
        title = stringResource(Res.string.settings_title),
        navigationIcon = if (drawerState != null) {
            {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = stringResource(Res.string.main_menu),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else null,
        onBackButtonClick = if (drawerState == null) onBackClick else null,
        scrollBehavior = scrollBehavior,
        snackbarHost = {
            if (snackbarHostState != null) SnackbarHost(hostState = snackbarHostState)
        },
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .adaptiveContentWidth()
                .padding(top = AppDimens.SpacingMd),
        ) {
            // ----------------------------------------------------------------
            // "Language" section — placed ABOVE Appearance because language
            // affects the labels of all other sections. Seeing it first is the
            // natural UX order (especially for users on a foreign-locale device).
            // M3 pattern: labelLarge + primary header, selectableGroup radio rows.
            // ----------------------------------------------------------------
            Text(
                text = stringResource(Res.string.settings_language),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    horizontal = AppDimens.ScreenPaddingHorizontal,
                    vertical = AppDimens.SpacingSm,
                ),
            )

            Column(modifier = Modifier.selectableGroup()) {
                LanguageOption(
                    label = stringResource(Res.string.settings_language_system),
                    selected = selectedLanguage == AppLanguage.System,
                    onClick = { onLanguageChange(AppLanguage.System) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                LanguageOption(
                    label = stringResource(Res.string.settings_language_english),
                    selected = selectedLanguage == AppLanguage.English,
                    onClick = { onLanguageChange(AppLanguage.English) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                LanguageOption(
                    label = stringResource(Res.string.settings_language_russian),
                    selected = selectedLanguage == AppLanguage.Russian,
                    onClick = { onLanguageChange(AppLanguage.Russian) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                LanguageOption(
                    label = stringResource(Res.string.settings_language_hindi),
                    selected = selectedLanguage == AppLanguage.Hindi,
                    onClick = { onLanguageChange(AppLanguage.Hindi) },
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            // ----------------------------------------------------------------
            // "Appearance" section header
            // M3 pattern: labelLarge + primary, horizontal padding matches
            // screen padding for visual alignment.
            // ----------------------------------------------------------------
            Text(
                text = stringResource(Res.string.settings_theme),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    horizontal = AppDimens.ScreenPaddingHorizontal,
                    vertical = AppDimens.SpacingSm,
                ),
            )

            // ----------------------------------------------------------------
            // Radio group — selectableGroup() semantics groups the three items
            // into a single radio group for accessibility tools.
            // ----------------------------------------------------------------
            Column(modifier = Modifier.selectableGroup()) {
                ThemeOption(
                    label = stringResource(Res.string.settings_theme_light),
                    selected = selectedTheme == AppThemeMode.Light,
                    onClick = { onThemeChange(AppThemeMode.Light) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                ThemeOption(
                    label = stringResource(Res.string.settings_theme_dark),
                    selected = selectedTheme == AppThemeMode.Dark,
                    onClick = { onThemeChange(AppThemeMode.Dark) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                ThemeOption(
                    label = stringResource(Res.string.settings_theme_system),
                    selected = selectedTheme == AppThemeMode.System,
                    onClick = { onThemeChange(AppThemeMode.System) },
                )
            }

            // ----------------------------------------------------------------
            // Dynamic Color (Material You) — only shown on platforms that can
            // actually produce a wallpaper-based palette. On iOS and Android
            // < 12 the section is omitted entirely.
            // ----------------------------------------------------------------
            if (dynamicColorSupported) {
                Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

                DynamicColorRow(
                    enabled = dynamicColorEnabled,
                    onToggle = onDynamicColorChange,
                )
            }

            // ----------------------------------------------------------------
            // Navigation layout. Last on the screen on purpose: it restructures
            // the whole app, so it must not sit next to the everyday theme and
            // language rows where it invites an idle tap.
            // ----------------------------------------------------------------
            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            ClassicNavigationRow(
                enabled = classicNavigationEnabled,
                onToggle = onClassicNavigationChange,
            )

            // Bottom breathing room: this is the last row of a scrollable column, and without it the
            // switch sits flush against the navigation bar.
            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
        }
    }
}

/**
 * Single theme option row.
 *
 * Uses M3 [ListItem] to guarantee:
 *   - Correct height (56dp default → ≥ 48dp touch target ✓)
 *   - Proper text baseline alignment
 *   - Consistent color: [ListItem] uses [MaterialTheme.colorScheme.surface] container
 *
 * The entire row is tappable via [Modifier.selectable]; [RadioButton.onCheckedChange]
 * is null to prevent double-toggle (KMP platform gotcha — see CLAUDE.md).
 */
@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingContent = {
            RadioButton(
                selected = selected,
                // null: row's selectable modifier handles the click.
                // Avoids double-toggle (Switch+clickable Row pattern applied to RadioButton).
                onClick = null,
            )
        },
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
    )
}

/**
 * Single language option row.
 *
 * Verbatim copy of [ThemeOption] — same M3 [ListItem] + [Modifier.selectable]
 * + [Role.RadioButton] pattern. [RadioButton.onCheckedChange] is null to
 * prevent double-toggle (KMP platform gotcha — see CLAUDE.md).
 */
@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingContent = {
            RadioButton(
                selected = selected,
                // null: row's selectable modifier handles the click.
                // Avoids double-toggle (Switch+clickable Row pattern applied to RadioButton).
                onClick = null,
            )
        },
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
    )
}

/**
 * Dynamic Color toggle row.
 *
 * Uses [Modifier.toggleable] + [Role.Switch] for correct TalkBack semantics.
 * [AppSwitch.onCheckedChange] is `null` so the row itself owns the click —
 * prevents double-toggle (see KMP platform gotcha in CLAUDE.md).
 */
@Composable
private fun DynamicColorRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(Res.string.settings_dynamic_color),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(Res.string.settings_dynamic_color_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            AppSwitch(
                checked = enabled,
                // null: row's toggleable modifier owns the click — avoids double-toggle.
                onCheckedChange = null,
            )
        },
        modifier = modifier
            .toggleable(
                value = enabled,
                onValueChange = onToggle,
                role = Role.Switch,
            ),
    )
}

/**
 * Opt-out back to the previous navigation (drawer + bottom chat dock).
 *
 * Phrased as one switch rather than a two-option picker because it is not a symmetric choice: v2 is
 * the product since the 2026-08-03 direction change, and this is the escape hatch that exists while
 * v2 is unverified on web, tablet and desktop.
 *
 * The supporting line names what the user gets back — "drawer, chat at the bottom" — instead of
 * saying "classic layout" twice. A switch whose only explanation restates its title tells the user
 * nothing about what will change, and this one restructures the entire app.
 */
@Composable
private fun ClassicNavigationRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(Res.string.settings_classic_navigation),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(Res.string.settings_classic_navigation_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            AppSwitch(
                checked = enabled,
                // null: row's toggleable modifier owns the click — avoids double-toggle.
                onCheckedChange = null,
            )
        },
        modifier = modifier
            .toggleable(
                value = enabled,
                onValueChange = onToggle,
                role = Role.Switch,
            ),
    )
}
