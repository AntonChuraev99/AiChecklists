**Статус:** Done
**Дата:** 2026-07-10
**Start SHA:** 357f013089cc079cbe5d205427580140c2e293f6
**Тип:** refactor / architecture
**Сложность:** Complex
**Impact:** High
**Затронутые модули:** feature/aichat/impl (parser + lexicons + tests)

# AI Chat Layer 1 → precision-first (implementation)

Implements the decision `docs/decisions/2026-07-10-ai-chat-layer1-precision-first.md`.
Shrinks Layer 1 (`LocalIntentRouterImpl`) from recall-first to precision-first: fires only on
unambiguous local imperatives, escalates everything else to L2/L3.

## Цель

Fix the whole class of Layer-1 mis-fires (greedy parse, broad-keyword false-positives, dead-ends)
by escalating ambiguous input, while preserving the 0-credit/instant/offline fast-path for the
core mutations (add/complete/delete a to-do). Contract change: ~16 existing parser tests flip
local→escalate (deliberate, user-approved 2026-07-10).

## Технический план

User-approved scope (both forks = Recommended):
- **Fork FindItems:** demote broad triggers only (`где/покажи/показать/show/where/where is/where are`)
  → escalate; keep explicit (`найди/найти/поиск/ищи/искать/find/search/search for/look for`).
- **Fork CreateChecklist:** topic-form (`для`/`for` present) → escalate to AI-fill; plain-name
  forms stay local (empty list).

Phase 1 (safe hardening):
- Remove ambiguous infinitives `сделать`, `выполнить` from `RuIntentLexicon.completeItem`
  (root of the 2026-07-10 planning-question bug; keeps `сделано/сделал/выполнил/выполнено`).
- Structural-marker guard: escalate CreateItem when payload has a meta-marker
  (`пункт`/`папк`/`чек-лист`/`чеклист` · `item`/`folder`/`checklist`) **AND** a hint-preposition
  (fixes `«добавь в ai чек-лист пункт тест»`, `«добавь в апки в папку баги … пункт …»`; does NOT
  touch `«добавь молоко в список покупок»` or `«добавь заказать два ведра в дела»`).
- Generalize the question guard: escalate WH-interrogative starts
  (`что/чо/чё/как/почему/зачем/сколько/какой/какая/какие/чем · what/how/why/which`)
  (fixes `«как мне найти работу в Китае?»`).

Phase 2 (forks above).
Phase 3 (keyword-at-start requirement) — **REJECTED 2026-07-10**, removed from the plan. It would
break the mass mid-sentence add pattern (`«I need to buy milk»` / `«надо купить X»`, passing test
`wordBoundary_buyNotPartOfLongerWord`) while fixing no open bad-feedback case. See ADR.

## Лог итераций

### Итерация 1 — 2026-07-10 — kotlin-expert (TDD contract migration)

**Итог итерации:** Phases 1+2 shipped. TDD RED→GREEN: 168 tests, exactly 21 flipped (16 deliberate local→escalate + 5 new escalation guards), 0 collateral damage, 4 pre-existing @Ignore. Implementation complete for approved forks; Phase 3 (keyword-at-start affirmative-prefix allowlist) deferred.

**Що реализовано:**
- Phase 1 hardening: removed ambiguous infinitives `сделать`/`выполнить` from `RuIntentLexicon.completeItem` (both are root-cause verbs for 2026-07-10 planning-question bug; kept inflected forms `сделано/сделал/выполнил/выполнено`).
- Structural-marker guard in `CreateItem`: escalate when payload contains meta-marker (`пункт`/`папк`/`чек-лист`/`чеклист` · `item`/`folder`/`checklist`) AND hint-preposition (fixes `«добавь в ai чек-лист пункт тест»`; passes through `«добавь молоко в список покупок»`).
- WH-interrogative broadening: escalate starts with `что/чо/чё/как/почему/зачем/сколько/какой/какая/какие/чем/what/how/why/which` (fixes `«как мне найти работу?»`).
- Fork FindItems: demoted broad triggers (`где/покажи/показать/show/where/where is/where are`) → escalate; kept explicit (`найди/найти/поиск/ищи/искать/find/search/search for/look for`).
- Fork CreateChecklist: topic-form (contains `для`/`for`) → escalate to L3 (AI-fill); plain-name forms stay local.

**Почему так:** TDD contract-first (RED run validates that only intended tests flip, +5 structural/WH tests confirm guards fire). Each phase tied to user-approved fork decision (2026-07-10). Escalations trade some precision recall for zero false-complete (the primary failure mode).

**Баги/проблемы:** None in RED/GREEN cycle; implementation passed as-is.

**Open risks — accepted for precision-first:**
- `для`/`for` topic-check is coarse substring (any create containing it escalates, incl. `«список дел для дома»`); tighten only if feedback shows over-escalation.
- `папк` substring-stems (папку/папке); same defer-and-feedback approach.

**Deferred:** Phase 3 (require command keyword at start of input with affirmative-prefix allowlist) → separate push. NOT committed yet.

**Ref:** Decision + rationale → `docs/decisions/2026-07-10-ai-chat-layer1-precision-first.md`.

## Выводы

- B (Layer 1 precision-first) **complete** with Phases 1+2 (commit `caf02df3`, seeded `357f0130`).
  Phase 3 (keyword-at-start) evaluated and **rejected** — would erode the free path for the mass
  `«I need to buy milk»` add pattern for zero bad-feedback benefit. There is no Phase 3.
- The whole ai_chat_feedback mis-fire class (greedy add, broad-keyword false-positive, question
  dead-end, topic-create-empty) is now escalated, not lexicon-patched. `/ai-chat-feedback-fixer`
  should surface far fewer cases going forward.
- Accepted-risk boundaries (`для`/`for` coarse substring; `папк` stemming) are documented in the
  ADR — tighten only if real feedback shows over-escalation, do not pre-optimize.
- **Verify-before-ship (not blocking commit):** this is commonMain routing logic fully covered by
  168 unit tests; before a user-facing release, exercise the demoted classes live (device/web) to
  confirm they reach L2/L3 and answer better, not silently fail there.

## Предложения по улучшению агентов
