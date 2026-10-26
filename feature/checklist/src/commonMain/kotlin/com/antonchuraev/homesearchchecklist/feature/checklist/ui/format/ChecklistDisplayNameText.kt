package com.antonchuraev.homesearchchecklist.feature.checklist.ui.format

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_checklist_name
import androidx.compose.runtime.Composable
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.checklistDisplayName as resolveDisplayName

/**
 * Composable entry point to the display-name rule — see
 * [com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.checklistDisplayName].
 *
 * Every surface that prints a checklist's name and can be handed the system Inbox goes through
 * here, so the Inbox reads in the CURRENT UI language rather than the one it was created in.
 */
@Composable
fun checklistDisplayName(storedName: String, isInbox: Boolean): String =
    resolveDisplayName(storedName, isInbox, stringResource(Res.string.inbox_checklist_name))

/** [checklistDisplayName] for a whole [Checklist]. */
@Composable
fun Checklist.displayName(): String = checklistDisplayName(name, isInbox)
