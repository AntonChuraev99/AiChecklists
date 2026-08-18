---
title: "MAX plan: scale to many languages (Crowdin OSS pipeline) + finish RU"
date: 2026-07-21
status: deferred
blocked_by: none — MIN (Hindi) shipped first; scaling is the next phase on demand
resume: "запусти max-план локализации" / "добавь языки X,Y" / "добей RU перевод" / "настрой crowdin"
---

# Deferred: multi-language MAX plan

## Context
Hindi MIN (2026-07-21) proved the infra + a reusable DIY translation pipeline
(extract → parallel-translate → assemble → validate — see project memory
`hindi-multilang-localization-pipeline-2026-07-21`). Adding each new language is now a
**data-only** task: `values-<lang>/strings.xml` + one `AppLanguage` enum entry + one Settings
picker row + `settings_language_<lang>` endonym key + androidMain res `values-<lang>`.

## When resumed — decisions
1. **Target languages** — pick by traffic. India-adjacent + top markets are candidates (best-
   practices scan flagged: es, pt, de, fr, ar, id, etc.). Confirm with owner before translating.
2. **Automation platform for scale** — the DIY Claude pipeline is fine for 1-2 languages but does
   not scale past ~3-4 without a TMS (no reviewer UI / diff history). Best-practices scan (2026):
   - **Crowdin free-for-OSS** (repo is public since 2026-06-16) — native "Compose Multiplatform
     Resources XML" format + CI. Recommended for a solo dev.
   - Weblate self-host — zero budget, native CMP format, translator-oriented UX.
   - Play Console built-in Gemini auto-translate (free, 2026) — good for listing drafts.
   - AVOID: Google AutoML Translation API (deprecated 2025-09-30); old Gradle auto-translate plugins (abandoned).
3. **Finish RU** — `values-ru/strings.xml` is ~172 keys behind EN (1063 vs 1235). Also revisit the
   18 `folder_*` keys deliberately kept English-only (owner decision 2026-07-15,
   `docs/todos/2026-07-15-folder-strings-missing-ru-translation.md`).

## Reusable pipeline (from MIN)
Scripts in the MIN scratchpad: `extract.py` (split EN → chunks, skip `debug_*`+`app_name`),
`assemble.py` (splice translations into EN structure, validate key/placeholder parity),
`GLOSSARY.md`. Re-runnable per language by swapping the glossary target language.

## Related
- Shipped MIN: `docs/completed/hindi-localization-min-2026-07-21.md`
- RU infra precedents: `docs/solutions/features/i18n-ru-translation-2026-05-11.md`,
  `docs/solutions/ui-improvements/language-switching-kmp-2026-05-18.md`
