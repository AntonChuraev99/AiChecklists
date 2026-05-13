package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Visual preview chip for the Smart Add feature. Shown when the local NL-parser recognises
 * a date/time/repeat phrase in the item text field.
 *
 * Uses [AssistChip] (M3 chip taxonomy):
 *  - AssistChip — "system suggests an action", has a leading icon, non-selectable.
 *    Semantically correct for "a reminder will be set when you tap Add".
 *  - NOT FilterChip (no selection state), NOT InputChip (user doesn't enter this tag),
 *    NOT SuggestionChip (this isn't a query completion — it's a system affordance).
 *
 * Color treatment: primaryContainer / onPrimaryContainer — deliberately accented (not
 * neutral grey) to communicate "this is a deliberate signal, not a passive label".
 * Matches project Primary #2196F3; primaryContainer is the light-blue tonal variant.
 *
 * The chip is non-interactive by design — [onClick] is a no-op. Touch handling (dismiss,
 * confirm) belongs to the ViewModel layer (Phase 3). The chip disappears automatically
 * when the user backspaces the matched phrase from the input.
 *
 * @param label    Fully resolved, localized label string. Callers in feature-layer modules
 *                 use [resolveChipLabel] (or equivalent) to produce this from [ChipDisplay].
 *                 Keeping label resolution in the feature layer preserves the
 *                 core:designsystem → feature:checklist dependency direction.
 * @param isRepeat When true, shows the [Icons.Outlined.Repeat] icon instead of the bell,
 *                 signalling a recurring schedule is the dominant information.
 */
@Composable
fun TokenChipPreview(
    label: String,
    modifier: Modifier = Modifier,
    isRepeat: Boolean = false,
) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = if (isRepeat) Icons.Outlined.Repeat else Icons.Outlined.Notifications,
                contentDescription = null, // decorative — label conveys the meaning
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = null,
        modifier = modifier,
    )
}
