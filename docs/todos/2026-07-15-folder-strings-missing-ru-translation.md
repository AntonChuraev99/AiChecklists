---
date: 2026-07-15
title: Folder feature is English-only (18 keys) — accepted, do not re-open
severity: low
type: localization
status: closed
resolution: wont-do
resolved: 2026-07-15
---

# `folder_*` strings stay English — decided

**Decision (2026-07-15, by the project owner): no Russian translation for the folder feature. Accepted as-is.**

This file exists so the gap is not "discovered" again and re-opened as a bug. It is a deliberate choice, not an oversight.

## What the gap is

All 18 `folder_*` keys live in `core/designsystem/src/commonMain/composeResources/values/strings.xml` and **none** in `values-ru/strings.xml`. On a Russian device the whole folder feature renders in English — `FolderActionsSheet` has always behaved this way.

Verified list (`grep -oE 'name="folder_[a-z_]+"'`):

```
folder_actions_title        folder_delete_message_other   folder_open
folder_create               folder_delete_title           folder_progress
folder_default_name         folder_move                   folder_reminder
folder_delete               folder_name_label             folder_reminder_active
folder_delete_message_empty folder_name_placeholder       folder_reminder_unavailable
folder_delete_message_one   folder_rename                 folder_rename_title
```

## Why it surfaced

The overflow-sheet change (`1430fac2`) made it visible in one new place: inside a folder the destructive row now reads `folder_delete` ("Delete folder") where it previously read `delete_checklist` — which **is** translated ("Удалить чек-лист"). So on RU exactly one row went from Russian to English.

Translating only that key was rejected during the work (1 of 18 is worse than 0 of 18 — inconsistent within the same sheet), and the owner then confirmed the whole block is not wanted.

## Consistent with project policy

CLAUDE.md: copy is **English only**; RU localization happens only on explicit request or when fixing existing RU strings. This is that policy applied, not an exception to it.

## If this is ever revisited

- 18 keys; `folder_delete_message_empty/one/other` are plural branches and need a native check.
- `folder_delete_message_other` carries a `%1$d` placeholder — keep it.
- Escaping in this repo: apostrophes and quotes go in **literally** (`can't`), never `\'` — Compose Resources is parsed by `org.jetbrains.compose.resources`, not AAPT. See rule `compose-resources-kmp`.
