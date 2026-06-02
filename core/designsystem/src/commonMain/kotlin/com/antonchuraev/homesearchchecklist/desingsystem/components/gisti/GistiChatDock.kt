package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme

/**
 * Collapsed AI-chat dock bar used on both MainScreen and ChecklistDetailScreen bottomBar.
 *
 * This is a **drop-in replacement** for the existing [AskGistiBar] in contexts where the bar
 * should signal it opens a bottom-sheet (rather than navigating full-screen). The visual
 * language is identical to [AskGistiBar]; the only additions are:
 *
 *  1. A **ChevronUp icon** at the trailing-start position (before the mic), rendered in
 *     `primary` color to reinforce "tap to expand" affordance. On MainScreen this replaces
 *     the blank space after the mic; on Detail it reads as "lift the sheet".
 *  2. Optional **`contextLabel`** replaces the placeholder text when the host screen is
 *     context-aware (e.g. "Ask about this list…" on ChecklistDetailScreen). Pass `null`
 *     to show the default placeholder.
 *
 * Visual spec:
 *  - Height: 56dp, corner radius: 16dp (matches AskGistiBar)
 *  - Container: `surfaceContainerLowest`
 *  - Border: 1.5dp `outlineVariant`
 *  - Shadow: 2dp (light only)
 *  - Left:  [SparkleTile] 28dp
 *  - Center: placeholder/context text, `onSurfaceVariant`
 *  - Trailing: ChevronUp 24dp in `primary` + Mic 40dp IconButton in `onSurfaceVariant`
 *
 * @param placeholder     Default placeholder when [contextLabel] is null.
 * @param onClick         Opens the chat sheet (the whole row except the mic button).
 * @param onMicClick      Starts voice input — opens the sheet with the recording overlay.
 * @param contextLabel    When non-null, shown instead of [placeholder] (e.g. "Ask about this list…").
 * @param micContentDescription Accessibility label for the mic [IconButton].
 */
@Composable
fun GistiChatDock(
    placeholder: String,
    onClick: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
    contextLabel: String? = null,
    micContentDescription: String = "Voice input",
) {
    val isDark = LocalIsDarkTheme.current
    val shape = RoundedCornerShape(16.dp)
    val displayText = contextLabel ?: placeholder

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (isDark) 0.dp else 2.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                    role = Role.Button,
                )
                .semantics {
                    contentDescription = "Open Gisti AI chat"
                }
                .padding(start = 14.dp, end = 4.dp),
        ) {
            SparkleTile(size = 28.dp)

            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            // ChevronUp: visual affordance that this bar expands upward into a sheet.
            // Rendered in `primary` so it reads as the "active" cue alongside the neutral text.
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null, // decorative — Row semantics covers the action
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )

            // Mic — independent 48dp touch target (does NOT trigger the sheet)
            IconButton(
                onClick = onMicClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = micContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            }
        }
    }
}

/**
 * The bottomBar column used on **ChecklistDetailScreen**.
 *
 * Layout (inside the Surface):
 *
 * ```
 * ┌─────────────────────────────────────────────────────┐  ← Surface (surface,
 * │  ┌─[GistiChatDock]──────────────────────────────┐  │      shadowElevation=8dp)
 * │  │ ✨  Ask about this list…   ↑       🎤        │  │
 * │  └──────────────────────────────────────────────┘  │
 * └─────────────────────────────────────────────────────┘
 *   padding: horizontal=16dp, top=12dp, bottom=16dp + navBarsPadding
 * ```
 *
 * The two "Fill" actions are **no longer** in this bar — they moved to a TopAppBar action
 * that opens [FillOptionsSheet]. This bar now hosts ONLY the context-aware chat dock.
 *
 * This is a pure-UI shell. @android-expert:
 *  - Keep using this composable for `ChecklistDetailScreen.bottomBar`.
 *  - Add a TopAppBar action icon (e.g. `Icons.Outlined.PostAdd`) that toggles a
 *    `showFillSheet` flag; render [FillOptionsSheet] when true.
 *  - Pass `onChatClick` / `onMicClick` from App.kt (sheet wiring).
 *
 * @param checklistName       Current checklist name — passed as the context label.
 * @param onChatClick         Opens the chat sheet anchored to this checklist.
 * @param onMicClick          Opens the sheet in voice-input mode.
 * @param chatPlaceholder     Localised placeholder for the dock bar.
 * @param micContentDescription Accessibility label for mic.
 */
@Composable
fun ChecklistDetailBottomBar(
    checklistName: String,
    onChatClick: () -> Unit,
    onMicClick: () -> Unit,
    chatPlaceholder: String = "Ask Gisti…",
    micContentDescription: String = "Voice input",
    modifier: Modifier = Modifier,
) {
    Surface(
        // Flat bottom bar — NO shadow (user request 2026-06-02). The theme's `surface` and
        // `background` are the same warm cream (#FBFAF8), so a shadowElevation here only rendered
        // a dirty grey line above the bar in the system-nav zone — the "broken shadow" the user
        // saw. The dock pill inside carries its own outlineVariant border, so the bar reads as a
        // calm flat surface, consistent with the now-flat home screen (AskGistiBar, п.1).
        // Surface still fills width with NO bottom inset (background reaches the screen bottom);
        // the nav-bar inset stays on the inner Column.
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(top = AppDimens.SpacingMd, bottom = AppDimens.SpacingLg)
                .navigationBarsPadding(),
        ) {
            // Context-aware dock: "Ask about this list…"
            GistiChatDock(
                placeholder = chatPlaceholder,
                onClick = onChatClick,
                onMicClick = onMicClick,
                contextLabel = "“$checklistName”",
                micContentDescription = micContentDescription,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
