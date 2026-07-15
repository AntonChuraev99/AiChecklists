---
date: 2026-07-15
title: Folder feature has no Russian strings (18 keys)
severity: low
type: localization
status: open
---

# `folder_*` strings are English-only

## Problem

All 18 `folder_*` keys exist in `core/designsystem/src/commonMain/composeResources/values/strings.xml` and **none** of them in `values-ru/strings.xml`. On a Russian device the whole folder feature renders in English — `FolderActionsSheet` already behaves this way today.

Verified list (`grep -oE 'name="folder_[a-z_]+"'`):

```
folder_actions_title        folder_delete_message_other   folder_open
folder_create               folder_delete_title           folder_progress
folder_default_name         folder_move                   folder_reminder
folder_delete               folder_name_label             folder_reminder_active
folder_delete_message_empty folder_name_placeholder       folder_reminder_unavailable
folder_delete_message_one   folder_rename                 folder_rename_title
```

## Why it is open now

The 2026-07-15 overflow-sheet change (`1430fac2`) made this visible in a new place: inside a folder the destructive row now reads `folder_delete` ("Delete folder") where it previously read `delete_checklist` — which **is** translated ("Удалить чек-лист"). So on RU one row regressed from Russian to English.

Translating just that one key was deliberately rejected: 1 of 18 translated is worse than 0 of 18 (inconsistent within the same sheet), and project policy is English-only unless RU is explicitly requested.

## Decision needed

Either translate the whole `folder_*` block (18 keys, needs a native check on the plural forms `folder_delete_message_empty/one/other`), or accept the feature as English-only and close this.

Note `folder_delete_message_other` takes `%1$d` — keep the placeholder.

## Files

- `core/designsystem/src/commonMain/composeResources/values/strings.xml` (source)
- `core/designsystem/src/commonMain/composeResources/values-ru/strings.xml` (target)

Escaping rule for this repo: apostrophes and quotes go in **literally** (`can't`), never `\'` — Compose Resources is not AAPT. See rule `compose-resources-kmp`.

## Verification

Switch device/emulator locale to Russian, open a checklist with folders, exercise: folder card, folder actions sheet, rename, delete-confirm (all three plural branches), overflow sheet inside a folder.
