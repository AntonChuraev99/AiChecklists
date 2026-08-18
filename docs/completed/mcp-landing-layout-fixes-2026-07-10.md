# MCP Landing Page Layout Fixes & Surfacing

**Статус:** Done
**Дата старта:** 2026-07-10
**Start SHA:** unknown (no INIT phase)
**Project:** gisti-ai-checklists
**Тип:** bug-fix
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** [landing, mcp]

## Цель (продуктовая)
Fix visual layout bugs on the public MCP landing page (`gisti-ai.com/mcp`) and surface MCP prominently on the main landing site to drive discoverability for Claude users.

## Технический план
1. Fix gradient-border pill rendering at fractional DPR via border-box double-gradient technique (landing/tokens.css)
2. Fix nested `overflow-x-auto` clipping table corners by collapsing to single clip container (landing/mcp/index.html)
3. Add cache-bust query to stylesheet link to force browser refresh on CSS changes
4. Surface MCP on main landing: nav link ("Use in Claude"), promo section, footer Product link (landing/index.html)
5. Update Tailwind config and compile new utilities (landing-src/tailwind.config.js, landing/tailwind.css)

## Лог итераций

### Итерация 1 — 2026-07-10 — [frontend CSS fix]
**Что сделано:** Diagnosed gradient-border `.ai-border` rendering bug at fractional DPR (1.5 on Pixel 6 Pro). The `mask-composite: exclude` with fractional padding snapped unevenly per side, making bottom edge visibly thicker. Rewrote to use `border: 1.5px solid transparent + background: linear-gradient(surface) padding-box + var(--ai-gradient) border-box` — native border snapping handles any DPR uniformly.

**Почему так:** Mask-composite with fractional values snaps differently per edge at DPR!=1.0. Browser's native border rendering is DPR-agnostic and uniform across all sides.

**Баги/проблемы:** CSS cache trap — after fix, computed styles showed old rule even after fetch returned new file. The `<link>` stylesheet was disk-cached by browser.

**Решение:** Added version query string to `<link href="...tokens.css?v=20260710">` to cache-bust. Cleared Chrome disk cache and verified computed style updated.

### Итерация 2 — 2026-07-10 — [frontend layout fix]
**Что сделано:** Fixed table corner-clip bug. Table sat in `<div rounded-xl overflow-hidden><div overflow-x-auto><table>` — nested clip contexts. Outer div sets radius 24px, but inner scroll container is a NEW clip context with radius 0, pokes past corner, creates visible notch.

**Решение:** Collapsed to single clip container: `<div rounded-xl overflow-x-auto><table>`. Browser applies radius to the scrolling viewport, preserving corner. Tested at DPR 1.5 and 2.0 in Chrome DevTools.

### Итерация 3 — 2026-07-10 — [MCP surfacing + Tailwind]
**Что сделано:** Added MCP to main landing site: (1) Nav link "Use in Claude" (landing/index.html); (2) Gradient-framed promo section below hero with value prop + CTA + code snippet (reuses fixed `.ai-border`); (3) Footer "MCP" link in Products menu. Updated Tailwind config (landing-src/tailwind.config.js) for new utility classes (`.prose-gradient` for code styling).

**Решение:** Ran `npx tailwindcss@3 -c landing-src/tailwind.config.js -o landing/tailwind.css` to compile. Verified promo renders clean at multiple DPRs in Chrome.

## Выводы
Two CSS layout bugs fixed on public landing (`gisti-ai.com/mcp`). Gradient-border pattern is now reusable via token in `landing/tokens.css` (benefit: all future pills/badges/gradient frames inherit the DPR-safe fix). MCP is now discoverable on main site via nav, promo section, and footer link. All changes verified in Chrome at DPR 1.5, 2.0 on Pixel 6 Pro and MacBook Pro screens.

## Предложения по улучшению агентов
- [ ] Frontend/wasmJs agents: document that `mask-composite + fractional padding` fails at DPR!=1.0; recommend border-box double-gradient for gradient borders at any radius.
- [ ] Tailwind + static-site builds: document that precompiled CSS changes require either rebuild OR cache-bust `<link>` href (not just XHR fetch) in the HTML — separate concerns.
