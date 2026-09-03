---
title: "AI Chat D1 — follow-ups from the bug-pattern review"
date: 2026-07-15
type: todo
status: deferred
severity: low-medium
modules: [feature/aichat/impl, composeApp]
keywords: [ai-chat, D1, moveAddedItem, undo, double-tap, weekday-case, plural, ChatDateFormatter]
---

# D1 follow-ups — found by review, deliberately not fixed in this release

Context: `docs/active/ai-chat-question-ux-d1-2026-07-15.md`. Everything below was found by
`@bug-pattern-reviewer` on the D1 diff (2026-07-15) and triaged as "real, but not worth rushing at
the end of the session". None of it blocks D1: the reported bug (the object of the action was
invisible) is fixed and verified live.

## 1. Move drops the item's state — medium

`ToolCallDispatcherImpl.moveAddedItem` relocates via `handleAddItem`, which always creates a fresh
row: `checked = false`, no `note`, no `attachments`, no reminder fields. The post-action chips live
until the user dismisses them, so this is reachable: add an item via chat → tick it off / add a note
in the list → come back to the chat → "Move to another list" → **the tick and the note vanish
silently**. Data loss, no feedback.

Fix: read the source row by id (`handle.fillId` + `handle.fillItemId`) before the add and carry its
state onto the new row. Watch out — `ChecklistFillItem` has a **private constructor** with `withX`
helpers (`withChecked`, `withReminderAt`), so check what is actually copyable before designing the
patch; a blind `copy()` may not compile. Keep the add-then-remove order (worst case a duplicate,
never a loss).

## 2. Double-tap window in executeMove — medium, low confidence

`handleChoiceSelected` guards on `executingId != null`, but `markChipExecuting` is only reached
**after** the `choiceString()` suspend point inside `viewModelScope.launch`. Two fast taps both get
through. For `executeMove` the cost is real data: `handleAddItem` runs twice → the item lands in the
target list **twice** (the dispatcher's own log literally predicts "the item now exists twice").
Same pattern pre-exists in `executeChoice`. Fix: close the window synchronously, before the first
suspend point.

## 3. RU weekday case after a preposition — low

`ChatDateFormatterImpl.weekdayRes` returns the nominative ("Среда"), and the RU strings put it after
the preposition «на»: `chat_dispatch_moved_one`, `chat_dispatch_moved_many`,
`chat_dispatch_no_reminders_on_day` → «на **Среда**» (should be «на среду»). Feminine days
(среда/пятница/суббота) are wrong; masculine ones are accidentally right. Months were done properly
(genitive) in D1, weekdays were reused from `feature/home` in the nominative.

Not a regression — that copy was English before D1 — but the localisation is half-done. Fix by
adding an accusative weekday set, or by rewording the strings so no case is needed. Do not add a
dependency on `feature/home`.

## 4. Plurals — low

RU `chat_preview_files_count` = «%1$s файлов» has no plural forms → «2 файлов». EN «%1$s files» can
render «1 files» when `count == 1 && firstFileName == null`.

## 5. Stale test comments — low

- `executeChoice_ambiguousMatch_buildsCandidateChips` is green but now passes through a **different
  route** (post-dispatch `AmbiguousMatch`, not tap-execute). Its name and its "Tap execute →"
  comment lie.
- `AiChatScenariosTest` C4/C7/C21/C22/C23 carry "RED now" comments while being GREEN.

## Resume

"добей D1 followups" — start with #1 (the only one that loses user data).
