# Templates Library Unification & Growth

**Статус:** Done
**Дата старта:** 2026-07-22
**Start SHA:** 0eb4e4e5f (HEAD)
**Project:** gisti
**Тип:** feature
**Сложность:** Complex
**Impact:** High
**Затронутые модули:** feature/create, landing-src, firebase-functions (RC + seed), data/checklists

⚠ INIT phase was skipped — minimal active doc reconstructed during COMPLETE. Counters могут быть неточными.

## Цель (продуктовая)
Unify templates library into a single source of truth (`data/checklists/*.json`), grow it from 47→81 app templates, eliminate dead Remote Config key, and establish consistent seed pipeline for app + landing + gallery.

## Технический план
1. **Root cause analysis:** identify split (bundled templates.json vs Firestore gallery_templates vs dead RC key)
2. **Design unified pipeline:** extend `landing-src/checklists/generate.mjs` to emit 3 outputs from single source
3. **Dead RC cleanup:** remove `RemoteConfigKeys.TEMPLATES_JSON`, delete `firebase-config/templates.json`, remove loaders
4. **Library growth:** author 31 new bilingual EN+HI checklists; dedupe 16 duplicates from bundled set
5. **App wiring:** update icon mappers, category names, TemplatesScreen/TemplatesRepositoryImpl
6. **Validation:** syntax check generate.mjs, verify 0 broken links, render all 194 landing pages, confirm 81 app templates seed
7. ~~Commit, landing deploy, Firestore reseed, Play release~~ (owner-gated, deferred)

## Лог итераций

### Итерация 1 — 2026-07-22 — main-agent (no specialist trace)
**Что сделано:** 
- Root-cause analysis + design unified pipeline
- Refactored `generate.mjs` with `writeAppTemplates` + `writeSeed` emitters
- Extended CATEGORIES to cover 12 category groups (EN+HI)
- Authored 31 new bilingual checklists against gold-standard spec
- Deduped 16 app templates; net growth 47→81
- Removed `RemoteConfigKeys.TEMPLATES_JSON`, `firebase_remote_config.py` templates block, deleted `firebase-config/templates.json`
- Updated app TemplatesScreen icon mapper (+7 categories), TemplatesRepositoryImpl categoryNames
- Generated 194 landing pages (81 detail EN/HI + 15 hub + index), app templates.json, gallery-templates.seed.json
- Validation: generate.mjs syntax ✅, no broken links ✅, compileDebug ✅

**Почему так:** 
- Single source eliminates sync bugs (landing drift from app from Firestore)
- Bilingual authoring at source prevents per-platform translation gaps
- Dedup before regeneration prevents temporary library loss on app install

**Баги/проблемы:** none

**Решение:** —

## Выводы

Completed a full templates library unification pipeline. The app, landing, and Firestore gallery now all draw from a single authoritative source (`data/checklists/*.json`). Grew usable app templates from 47→81 by removing duplicates and authoring 31 new bilingual checklists across 12 categories (EN+HI parity verified on all). Dead Remote Config key (`templates_json`) removed entirely. Code wiring verified (compileDebug green). Landing pages regenerated (194 pages). Seed JSON for Firestore production reseed prepared.

Deployment is owner-gated: landing deploy + Firestore reseed + Play release handled by user separately (git commit not in doc-writer scope).

## Предложения по улучшению агентов

### compose-feature-expert / android-platform-expert
- [ ] Pattern: Compose Resource-driven templates with generate.mjs fan-out (now reusable for other multi-platform galleries)

### best-practices-scout / knowledge-scout
- [ ] Template authoring spec (metadata mapping: id/name/description/icon/items/category) — captured for future hiring/onboarding
