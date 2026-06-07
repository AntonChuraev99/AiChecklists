package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * Modal bottom sheet that lets the user choose how to fill a checklist.
 *
 * Replaces the two "Fill" buttons that previously lived in the checklist-detail bottom bar
 * (now the [ChecklistDetailChatDock] overlay). It is opened from a TopAppBar action on
 * ChecklistDetailScreen (suggested icon: `Icons.Outlined.NoteAdd` — already imported there).
 *
 * Two options, presented as tappable rows (ListItem-style — richer than bare buttons,
 * gives each option a supporting description and a clear "menu of actions" reading):
 *  1. **Fill Manually** — secondary emphasis. Neutral `surfaceContainerHigh` icon tile,
 *     `outlineVariant` border. Description: "Create an empty fill".
 *  2. **Fill via AI** — accent emphasis. `primaryContainer` icon tile + `primary` row
 *     border so it reads as the recommended path WITHOUT a full fill (text stays on the
 *     standard `onSurface` role for legibility). Description:
 *     "Photo, PDF, link or text → AI fills it".
 *
 * Pure UI — no ViewModel. The host owns the open/close flag and supplies callbacks.
 *
 * Token mapping:
 * - Sheet container shape: 28dp (M3 `extra-large`, the bottom-sheet corner spec).
 * - Manual tile: `surfaceContainerHigh` + `outlineVariant` border, icon `onSurfaceVariant`.
 * - AI tile: `primaryContainer`, icon `onPrimaryContainer`; row border `primary`.
 * - Titles: `titleMedium` (SemiBold); descriptions: `bodyMedium` `onSurfaceVariant`.
 *
 * @param onFillManually  Called when the manual option is tapped. The host should dismiss
 *                        the sheet then open FillDetail (existing behaviour).
 * @param onFillViaAi     Called when the AI option is tapped. The host should dismiss the
 *                        sheet then open the AI fill flow.
 * @param onDismiss       Called when the sheet is dismissed (scrim tap, swipe, back).
 * @param sheetState      Hoisted [SheetState]. Defaults to
 *                        `rememberModalBottomSheetState(skipPartiallyExpanded = true)` —
 *                        the content is short, so a half-expanded state is unnecessary.
 * @param title           Sheet headline. Defaults to "Fill this checklist".
 * @param fillManuallyLabel       Manual option title. Defaults to "Fill Manually".
 * @param fillManuallyDescription Manual option supporting text. Defaults to "Create an empty fill".
 * @param fillViaAiLabel          AI option title. Defaults to "Fill via AI".
 * @param fillViaAiDescription    AI option supporting text. Defaults to "Photo, PDF, link or text → AI fills it".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillOptionsSheet(
    onFillManually: () -> Unit,
    onFillViaAi: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String = "Fill this checklist",
    fillManuallyLabel: String = "Fill Manually",
    fillManuallyDescription: String = "Create an empty fill",
    fillViaAiLabel: String = "Fill via AI",
    fillViaAiDescription: String = "Photo, PDF, link or text → AI fills it",
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    top = AppDimens.SpacingXs,
                    bottom = AppDimens.SpacingSm,
                ),
            )

            // Fill Manually — secondary emphasis
            FillOptionRow(
                icon = Icons.Outlined.NoteAdd,
                label = fillManuallyLabel,
                description = fillManuallyDescription,
                onClick = onFillManually,
                tileColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tileContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                rowBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            )

            // Fill via AI — accent emphasis (recommended path)
            FillOptionRow(
                icon = Icons.Outlined.AutoAwesome,
                label = fillViaAiLabel,
                description = fillViaAiDescription,
                onClick = onFillViaAi,
                tileColor = MaterialTheme.colorScheme.primaryContainer,
                tileContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                rowBorder = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * A single tappable option row inside [FillOptionsSheet].
 *
 * Layout: [44dp icon tile] · [title + description column].
 * The whole row is the click target (lifted to the outer [Surface] so the full card
 * area — including padding — responds to taps, per the project hit-zone rule).
 */
@Composable
private fun FillOptionRow(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    tileColor: Color,
    tileContentColor: Color,
    rowBorder: BorderStroke,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = rowBorder,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(AppDimens.SpacingMd),
        ) {
            // Icon tile
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = tileColor,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null, // label below carries the meaning
                        tint = tileContentColor,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                    )
                }
            }

            Spacer(modifier = Modifier.size(AppDimens.SpacingMd))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXxs),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
