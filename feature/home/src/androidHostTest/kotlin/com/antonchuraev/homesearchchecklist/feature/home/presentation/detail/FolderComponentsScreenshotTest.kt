package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM/Robolectric screenshot (golden) tests for the **folder** UI of the checklist-detail screen
 * (Phase 4 — folders / nested checklists).
 *
 * Covers the design-system surface of the folder feature:
 *  - [FolderCard] — empty / progress chip / reminder bell / long-name ellipsis (light + dark)
 *  - [MoveToFolderSheet] — the depth-indented destination tree with disabled (illegal) rows,
 *    the current-parent check, and the "Move to root" leading row
 *  - The folder dialogs/sheet from `ChecklistDetailScreen.kt`:
 *    [FolderActionsSheet], [RenameFolderDialog], [DeleteFolderConfirmationDialog],
 *    [DisableFoldersConfirmationDialog]
 *
 * Two capture strategies are used:
 *  - [captureRoboImage] on `onRoot()` for **inline** content (FolderCard, the Move tree rows).
 *  - [captureScreenRoboImage] for the dialog/sheet composables, because they present through
 *    `AdaptiveSheetOrDialog` (a window-level `AlertDialog` / `ModalBottomSheet`) whose content
 *    renders in a separate window that `onRoot()` does not see. The dialog cases are pinned to
 *    `@Config(qualifiers = "w800dp-...")` so `AdaptiveSheetOrDialog` takes the **AlertDialog**
 *    branch (Medium/Expanded) — `AlertDialog`/`Dialog` windows capture deterministically under
 *    Robolectric, whereas `ModalBottomSheet` (the Compact branch) is known to flake to white
 *    screenshots (roborazzi#512).
 *
 * Record goldens:
 *   ./gradlew :feature:home:recordRoborazziAndroidHostTest --tests "*FolderComponentsScreenshotTest*"
 *
 * Verify (CI):
 *   ./gradlew :feature:home:verifyRoborazziAndroidHostTest --tests "*FolderComponentsScreenshotTest*"
 *
 * Golden PNGs land in:
 *   feature/home/src/androidHostTest/roborazzi/
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderComponentsScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // -------------------------------------------------------------------------
    // FolderCard — all visual states in one column (light + dark)
    // -------------------------------------------------------------------------

    @Test
    fun folderCard_states_light() {
        composeTestRule.setContent { FolderCardStatesContent(darkTheme = false) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun folderCard_states_dark() {
        composeTestRule.setContent { FolderCardStatesContent(darkTheme = true) }
        composeTestRule.onRoot().captureRoboImage()
    }

    /**
     * Long-name ellipsis edge case isolated so the truncation behaviour gets its own golden
     * (the meta chip + chevron must stay pinned to the right; the name takes the remaining width
     * and ellipsises at one line).
     */
    @Test
    fun folderCard_longName_light() {
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(AppDimens.SpacingLg),
                ) {
                    FolderCard(
                        name = "Quarterly planning, budgets, and roadmap reviews for 2026",
                        total = 12,
                        progressLabel = "7/12",
                        hasReminder = true,
                        onOpen = {},
                        onLongPress = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    // -------------------------------------------------------------------------
    // MoveToFolderSheet — destination tree (inline rows, deterministic capture)
    // -------------------------------------------------------------------------

    /**
     * Captures the actual [MoveToFolderSheet] body. The sheet presents through
     * `AdaptiveSheetOrDialog`; under the `w800dp` qualifier (see [moveToFolderSheet_tree_dialog])
     * it would be an AlertDialog window — but for the tree we prefer the inline-rows golden below
     * which captures every row state (indent / disabled / current-parent) without window timing.
     */
    @Test
    fun moveToFolderTree_rows_light() {
        composeTestRule.setContent { MoveTargetTreeContent(darkTheme = false) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun moveToFolderTree_rows_dark() {
        composeTestRule.setContent { MoveTargetTreeContent(darkTheme = true) }
        composeTestRule.onRoot().captureRoboImage()
    }

    // -------------------------------------------------------------------------
    // Window-level dialogs / sheet — forced to the AlertDialog branch (Medium) and
    // captured via captureScreenRoboImage so the popup window is included.
    // -------------------------------------------------------------------------

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-normal-long-notround-any-320dpi-keyshidden-nonav")
    fun folderActionsSheet_dialog() {
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                FolderActionsSheet(
                    folderName = "Quarterly planning",
                    hasReminder = true,
                    onReminder = {},
                    onRename = {},
                    onMove = {},
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        captureScreenRoboImage()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-normal-long-notround-any-320dpi-keyshidden-nonav")
    fun moveToFolderSheet_dialog() {
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                MoveToFolderSheet(
                    targets = sampleMoveTargets(),
                    onTargetSelected = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        captureScreenRoboImage()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-normal-long-notround-any-320dpi-keyshidden-nonav")
    fun renameFolderDialog_dialog() {
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                val name = remember { mutableStateOf("Quarterly planning") }
                RenameFolderDialog(
                    name = name.value,
                    onNameChanged = { name.value = it },
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        captureScreenRoboImage()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-normal-long-notround-any-320dpi-keyshidden-nonav")
    fun deleteFolderConfirmationDialog_withDescendants_dialog() {
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                DeleteFolderConfirmationDialog(
                    descendantCount = 5,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        captureScreenRoboImage()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-normal-long-notround-any-320dpi-keyshidden-nonav")
    fun deleteFolderConfirmationDialog_empty_dialog() {
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                DeleteFolderConfirmationDialog(
                    descendantCount = 0,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        captureScreenRoboImage()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp-normal-long-notround-any-320dpi-keyshidden-nonav")
    fun disableFoldersConfirmationDialog_dialog() {
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                DisableFoldersConfirmationDialog(
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        captureScreenRoboImage()
    }
}

// =============================================================================
// Stateless preview content helpers — explicit darkTheme param so Robolectric
// controls the theme deterministically (not isSystemInDarkTheme()).
// =============================================================================

@Composable
private fun FolderCardStatesContent(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(AppDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            // Empty folder — progress chip hidden (total == 0).
            FolderCard(
                name = "New folder",
                total = 0,
                progressLabel = "0/0",
                hasReminder = false,
                onOpen = {},
                onLongPress = {},
            )
            // In-progress folder — meta chip visible.
            FolderCard(
                name = "Groceries",
                total = 5,
                progressLabel = "3/5",
                hasReminder = false,
                onOpen = {},
                onLongPress = {},
            )
            // Folder with an active reminder — bell renders before the chevron.
            FolderCard(
                name = "Weekly review",
                total = 8,
                progressLabel = "8/8",
                hasReminder = true,
                onOpen = {},
                onLongPress = {},
            )
        }
    }
}

/**
 * Renders the [MoveToFolderSheet] body content (the destination rows) inline — every row state in
 * one golden: the "Move to root" home row, depth-indented folders, the current-parent row (check +
 * "Current location" subtitle) and disabled (illegal-target) rows greyed out.
 *
 * This mirrors exactly what `MoveToFolderSheet` renders inside `AdaptiveSheetOrDialog`, minus the
 * window chrome, so the tree is captured deterministically via `onRoot()`.
 */
@Composable
private fun MoveTargetTreeContent(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = AppDimens.SpacingMd),
        ) {
            sampleMoveTargets().forEach { target ->
                MoveTargetRow(target = target, onClick = {})
            }
        }
    }
}

/**
 * Sample destination tree exercising every [MoveTargetUiModel] state:
 *  - root "Move to root" row (id == null)
 *  - enabled folder at depth 1
 *  - the node's current parent (depth 1, check + subtitle, still tappable)
 *  - a disabled folder (illegal target — the node itself or a descendant) at depth 2
 *  - an enabled deeper folder at depth 2
 */
private fun sampleMoveTargets(): List<MoveTargetUiModel> = listOf(
    MoveTargetUiModel(id = null, name = "", depth = 0, enabled = true, isCurrentParent = false),
    MoveTargetUiModel(id = "a", name = "Work", depth = 1, enabled = true, isCurrentParent = false),
    MoveTargetUiModel(id = "b", name = "Personal", depth = 1, enabled = true, isCurrentParent = true),
    MoveTargetUiModel(id = "c", name = "Archive (this folder)", depth = 2, enabled = false, isCurrentParent = false),
    MoveTargetUiModel(id = "d", name = "Travel", depth = 2, enabled = true, isCurrentParent = false),
)
