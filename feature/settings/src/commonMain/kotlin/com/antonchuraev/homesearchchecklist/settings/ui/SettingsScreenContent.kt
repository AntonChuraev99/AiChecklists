package com.antonchuraev.homesearchchecklist.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.settings_theme
import aichecklists.core.designsystem.generated.resources.settings_theme_dark
import aichecklists.core.designsystem.generated.resources.settings_theme_light
import aichecklists.core.designsystem.generated.resources.settings_theme_system
import aichecklists.core.designsystem.generated.resources.settings_title
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.settings.domain.AppThemeMode
import org.jetbrains.compose.resources.stringResource

/**
 * Pure UI layer for the Settings screen.
 *
 * Responsibilities:
 *   - Display current theme selection (Light / Dark / System default)
 *   - Delegate all state changes to [onThemeChange]
 *   - Delegate back navigation to [onBackClick]
 *
 * This composable has NO ViewModel / DI dependencies — android-expert wires
 * those separately.
 *
 * Accessibility:
 *   - Each row uses [Modifier.selectable] with [Role.RadioButton] → TalkBack
 *     announces "Light, radio button, 1 of 3" etc.
 *   - RadioButton has [onCheckedChange = null] so the parent Row absorbs
 *     the click and avoids double-toggle.
 *   - Touch targets are ≥ 48dp via [ListItem] default height (56dp).
 *   - Section header uses [onSurfaceVariant] for low-emphasis label per M3 spec.
 *
 * @param selectedTheme The currently active [AppThemeMode].
 * @param onThemeChange Called when the user selects a different theme option.
 * @param onBackClick Called when the user taps the back navigation icon.
 * @param modifier Optional modifier applied to the root scaffold.
 */
@Composable
fun SettingsScreenContent(
    selectedTheme: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        title = stringResource(Res.string.settings_title),
        onBackButtonClick = onBackClick,
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = AppDimens.SpacingMd),
        ) {
            // ----------------------------------------------------------------
            // "Appearance" section header
            // M3 pattern: labelLarge + onSurfaceVariant, horizontal padding
            // matches screen padding for visual alignment.
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

