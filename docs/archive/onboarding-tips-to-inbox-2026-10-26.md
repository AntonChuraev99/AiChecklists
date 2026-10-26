# Onboarding getting-started tips go to the Inbox (v2)

**Статус:** Done
**Дата старта:** 2026-10-26
**Start SHA:** 7d0c83a9
**Project:** Checklists
**Тип:** feature
**Затронутые модули:** feature/onboarding, core/designsystem

⚠ INIT пропущен — документ реконструирован на COMPLETE.

## Цель (продуктовая)

After the "AI Welcome" onboarding, a user who picked no starter chip and typed nothing used to get a separate "My Checklist" project holding four how-to-use-the-app tips. The owner wants those tips as plain tasks in the Inbox. The second tip ("Swipe to check things off") also stopped being true after the redesign.

## Технический план

- Branch 3 of `WelcomeOnboardingViewModel.resolveFirstChecklist()` (empty input, no chip): in the v2 shell → `FirstChecklistAction.InboxTips`, appended to the system Inbox by `appendTipsToInbox()`; in v1 → the old `Seed` ("My Checklist") because v1 has no Inbox.
- Shell read via `NavExperimentResolver.ensureResolved()`, not `currentArm()` — the latter reports the v2 default before the stored choice is read.
- Each tip written as a template + fill pair (`templateItemId`), same shape as the Inbox quick-add in `InboxViewModel`.
- Tip `onboarding_welcome_default_item_2` reworded (EN + HI) to "Tap the circle to check things off".
- Chip and AI typed-text branches unchanged: they produce themed lists that belong in their own project.

## Лог итераций

### Итерация 1 — 2026-10-26 — feature-expert: branch 3 → Inbox in v2, DI, strings, tests → green, wasmJs compiles
### Итерация 2 — 2026-10-26 — feature-expert: diff review found that a CTA retry after `completeOnboardingUseCase()` throws re-appends the tips; latch `inboxTipsWritten` set after both writes → red (8 rows instead of 4) → green

## Выводы

- Done as planned in one iteration. After onboarding the v2 user lands on `navigateToMainScreen(clearBackStack = true)`, which resolves to the Inbox tab because the v2 shell declares it as the default root.
- Why the swipe tip was wrong: in the checklist detail screen swipe end-to-start deletes the item (`SwipeableChecklistItemCard` in `ChecklistDetailScreen.kt`); in the v2 Inbox task rows have no swipe at all — only the plan-your-day nudge row swipes (`PlanNudgeRow` in `InboxScreen.kt`). Checking off is a tap on the round checkbox.
- Analytics: the Inbox path fires `Onboarding.COMPLETED` with `checklist_created=false`, `seed=inbox` — no user checklist is created, so counting it as one would inflate the creation funnel (same reasoning as `EnsureInboxUseCase`). Funnel dashboards that read `seed=default` will see v2 traffic move to `seed=inbox`.
- A retry after a failure past both writes (e.g. the onboarding-passed write) no longer duplicates the tips: the `inboxTipsWritten` latch skips the writes on the second tap.
- Known gap, not fixed here: if `updateFill` succeeds and `updateChecklistTemplate` throws, the latch stays down and a CTA retry appends the tips to the fill again. `InboxViewModel` has the same write order and the same gap; closing it needs an atomic template+fill append in the repository.
- Not verified on a device.

## Предложения по улучшению агентов

