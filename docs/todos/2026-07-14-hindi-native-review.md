# Deferred: native-speaker review of Hindi landing copy

**Created:** 2026-07-14
**Status:** deferred
**Why deferred:** Hindi localization shipped as high-quality LLM translation (structural parity 50/50, verified in-browser). Machine translation is acceptable for Google indexing in 2026, but a native pass sharpens the highest-value SEO fields before heavy organic promotion.

## Scope
Native Hindi speaker reviews, per page:
- `metaTitle` / `metaDescription` (search-snippet copy — biggest CTR lever)
- `H1` / `title`
- optionally intro + FAQ answers

Source of truth: the `hi: {...}` blocks in `data/checklists/*.json` and `CATEGORIES[x].hi` in `landing-src/checklists/generate.mjs`. After edits: `node landing-src/checklists/generate.mjs`, then deploy (`wrangler whoami` = gmail → `npx wrangler@4 deploy -c wrangler.landing.jsonc`, then `node scripts/indexnow-ping.mjs`).

## Resume trigger
User says «вычитка хинди / native review хинди / поправь хинди перевод / hindi copy review», OR ~1-2 weeks post-launch once GSC shows /hi/ pages indexing and India organic impressions appear.

Related: [[hindi-localization-landing-gallery]] (project memory), docs/solutions/hindi-landing-localization-2026-07-14.md
