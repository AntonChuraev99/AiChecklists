---
title: "Folder delete persists two writes without a transaction (half-landed delete on throw)"
date: 2026-07-15
type: todo
status: deferred
severity: medium
modules: [feature/home, feature/checklist]
keywords: [confirmFolderDelete, updateFill, updateChecklistTemplate, transaction, atomicity, appScope, partial-write]
resume: "User says «доделай атомарность удаления папки / транзакция на удаление / half-landed delete»; ИЛИ при следующей работе над repository-слоем чек-листов; ИЛИ если придёт баг «удалил папку — вернулась пустой»"
---

# `confirmFolderDelete` — the two writes are still not one transaction

Found by `@bug-pattern-reviewer` during the review of PR #12 (2026-07-15), confirmed by the main
agent. **Not** a regression from that PR — the same gap existed before it.

## What is already fixed (PR #12 + review follow-up)

- The persist no longer runs on `viewModelScope`, which `confirmFolderDelete`'s own
  `navigator.onBack()` had just cancelled → the delete used to die at the first suspension point.
  Now on the injected app-wide scope.
- That app-wide scope now carries a `SupervisorJob` (`CommonCoreModule.kt`), so a throw here can no
  longer cancel the singleton scope and silently kill sync + credits convergence process-wide.
- The persist body is wrapped in `runCatching` + `AppLogger.error` — a failure is logged to
  Crashlytics instead of crashing via the default handler.

## What is still open

`ChecklistDetailViewModel.confirmFolderDelete` does:

```kotlin
repository.updateFill(updatedFill)
repository.updateChecklistTemplate(updatedChecklist)
```

Two independent writes, no transaction. A throw **between** them leaves the delete half-landed —
exactly the state the method's own comment claims to prevent ("fill written, template not → the
folder renders back, empty"). `runCatching` catches and logs it, but does not undo the first write.

Reordering the two calls was considered and **rejected**: it only swaps which half survives (an
orphan fill row pointing at a deleted template node, instead of a template node with no fill row),
and there is no test establishing which failure mode is less harmful. That is a guess, not a fix.

## Fix direction

One repository-level transaction covering both writes (Room `@Transaction` / `withTransaction`),
so the delete either fully lands or fully doesn't. Check the wasmJs path too — Room over the OPFS
Web Worker driver, where the window between the two writes is wider than on Android.

Alarm cancellation (`cancelItemReminder` / `cancelItemRepeat`) sits in the same block and is **not**
transactional by nature — decide whether it belongs before the transaction (alarms cancelled but
items still there = harmless, they just won't fire) or after (fires once for an already-deleted
item). Currently it runs first.

## No user-facing feedback on failure either

On the failure path the user sees the folder disappear from the UI (optimistic state was already
applied) while nothing was persisted — on the next launch it is back, with no explanation. Per the
project rule "every user action gets a visible response", this deserves a snackbar. Same block also
has three `?: return` early exits with no feedback (pre-existing, flagged by the same review).

## Verification recipe (from the review)

Make `repository.updateChecklistTemplate` throw once via a fake repo, then delete a folder:
assert the error is logged, the app does not crash, and — once the transaction lands — that the
fill write did **not** survive on its own.
