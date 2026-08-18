---
title: "Hindi (and future locales): localize onboarding example-content + bundled template library"
date: 2026-07-21
status: deferred
blocked_by: none — scope decision (MIN shipped UI chrome only)
resume: "доделай локализацию контента онбординга и шаблонов на хинди" / "templates JSON на другие языки"
---

# Deferred: content-layer localization (onboarding examples + templates)

## Context
The Hindi MIN localization (2026-07-21) translated the **UI chrome** — all 1236 `strings.xml`
keys (buttons, titles, subtitles, errors, paywall, chat, onboarding step labels). It did **not**
localize the **content layer**, which is English-sourced and lives outside `strings.xml`:

1. **Onboarding interactive example items** — hardcoded English literals in
   `feature/onboarding/.../interactive/InteractiveOnboardingViewModel.kt`:
   - `getExtraItems()` — ~30 example checklist items across 10 categories (e.g. "Emergency snack
     stash", "Preheat oven if needed").
   - `buildGenericFallback()` — `name = "My Checklist"`, `description = "A fresh start"`, 5 generic
     item literals.
   - `handleSaveChecklist()` — `?: "My Checklist"` fallback name.
   These violate the "no hardcoded user-facing strings" rule and show English on a Hindi device.

2. **Bundled template library** — the interactive onboarding builds its preview checklist from
   `templatesRepository.getTemplates()`, whose items come from an English bundled Compose-Resource
   JSON (`feature/create`, `templates_json` is dead per CLAUDE.md). The template item TEXT is the
   bulk of the generated onboarding checklist and is English.

## Why deferred
Localizing only `getExtraItems` is pointless while template items stay English (inconsistent
half-Hindi checklist). A coherent content localization = translate the template library JSON per
locale — a separate, larger content-translation effort (and a data-model decision: per-locale
template files vs a translation layer). MIN correctly shipped the chrome; the India ad-install
funnel gets a Hindi UI immediately.

## Scope when resumed
- Move the VM hardcodes to `strings.xml` (or a localized seed source) — small.
- Decide + build per-locale template content strategy (bigger). Note: AI-generated content already
  respects language (chat feature); only bundled/seed content is English.

## Related
- Shipped: `docs/completed/hindi-localization-min-2026-07-21.md`
- Recurring rule: no hardcoded user-facing strings (`docs/solutions/ui-improvements/hardcoded-strings-to-compose-resources-2026-06-07.md`)
