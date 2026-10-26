package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

/**
 * The name to SHOW for a checklist row: the localized [inboxName] for the system Inbox, the stored
 * name for everything else.
 *
 * The Inbox's stored `name` is written once, at creation, in whatever language was active then — or
 * on another device, arriving by sync. Shown verbatim it left "Входящие" on an English UI. The row
 * is deliberately NOT renamed in the database: the name syncs through Firestore, and devices in
 * different languages would keep overwriting each other. So the fix lives at display time.
 *
 * Pure on purpose: the domain layer never touches Compose Resources, so the caller resolves
 * [inboxName] (`stringResource` / `getString`) and passes it in. Composables use the
 * `checklistDisplayName(name, isInbox)` wrapper in `feature.checklist.ui.format`, which re-reads the
 * resource when the in-app language changes — a string cached in a ViewModel would not.
 *
 * Safe for the Inbox because it cannot be renamed from the Inbox tab (`decideRename` refuses it).
 */
fun checklistDisplayName(storedName: String, isInbox: Boolean, inboxName: String): String =
    if (isInbox) inboxName else storedName

/** [checklistDisplayName] for a whole [Checklist]. */
fun Checklist.displayName(inboxName: String): String = checklistDisplayName(name, isInbox, inboxName)
