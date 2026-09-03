---
title: "GSC «Duplicate, Google chose a different canonical» на /mcp/ — причина 307, а не разметка"
date: 2026-08-10
type: bug-fix
modules: [landing, landing-worker, landing-src]
keywords: [seo, google-search-console, canonical, trailing-slash, 307, 301, cloudflare-static-assets, robots-txt, nofollow, gisti-landing]
project: checklists
---

# GSC «Duplicate, Google chose a different canonical» на /mcp/ — причина 307, а не разметка

## Проблема / Контекст

5 писем GSC (07.08 robots.txt, 09.08 ×4 duplicate-canonical). Отчёт `sc-domain:gisti-ai.com`: 215 проиндексировано / 21 нет.

## Разбор отчёта по URL

| Причина | N | URL | Вердикт |
|---|---|---|---|
| Переадресация | 3 | www/http варианты apex | норма |
| Заблокировано robots.txt | 2 | `app.gisti-ai.com/?g=create&template=…` | намеренный Disallow |
| Копия, canonical не выбран | 2 | те же app deep-link'и | тот же корень |
| Обнаружена, не проиндексирована | 12 | галерейные en+hi | authority gap, не техбаг |
| **Копия, Google выбрал другой canonical** | **1** | **`/mcp/`** | **баг** |

URL Inspection для `/mcp/`: canonical пользователя `…/mcp/`, canonical Google `…/mcp` (без слэша).

**Корень:** Cloudflare Static Assets нормализует `/mcp` → `/mcp/` статусом **307** (temporary). 307/302 не консолидируют URL — Google держит каноническим исходный, а цель считает копией. Разметка была цела (canonical на всех 196 страницах, hreflang взаимный, hi реально переведён). Выстрелило только на `/mcp`, потому что единственная внутренняя ссылка без слэша — `href="/mcp"` в partials `header.html`/`footer.html` (→ 195 страниц) и ручном `landing/index.html`.

## Решение

**1. `landing-worker.js` — 301 вместо платформенного 307** (воркер первый, `run_worker_first: true`):

```js
function canonicalPathname(pathname) {
  if (pathname.endsWith("/index.html")) {
    return pathname.slice(0, -"index.html".length);
  }
  const lastSegment = pathname.slice(pathname.lastIndexOf("/") + 1);
  if (lastSegment !== "" && !lastSegment.includes(".")) {
    return pathname + "/";
  }
  return pathname;
}
```

Дискриминатор «сегмент с точкой» = ассет, не трогаем. Хост и путь чинятся одним хопом.

**2. `/mcp` → `/mcp/`** в partials + `landing/index.html`, затем `node landing-src/checklists/generate.mjs`. Sitemap и `templates.json` не изменились — генератор идемпотентен.

**3. `rel="nofollow"`** на per-template deep-link в `cta-block.html` — `app.gisti-ai.com` закрыт своим robots.txt, каждая пройденная ссылка возвращалась шумом (4 из 21).

## Верификация (`wrangler dev`, локально)

`/mcp`, `/checklists/…/<slug>`, `…/index.html` → **301** (было 307); query сохраняется; `/robots.txt`, `/sitemap.xml`, `/tailwind.css`, IndexNow-ключ, `/` → 200.

## Не закрыто

- **Прод не задеплоен** — нужно разрешение владельца (`wrangler deploy -c wrangler.landing.jsonc`, gmail-аккаунт, `whoami` первым).
- **12 «обнаружена, не проиндексирована»** — ranking/authority gap, не чинится кодом.
- **`/hi/` = 404** — хаба хинди нет; ссылок и записи в sitemap тоже нет ⇒ не баг, развилка SEO-плана.

## Уроки

- Расхождение canonical бывает в HTTP-статусе, а не в разметке: сверять «canonical пользователя» vs «canonical Google» в URL Inspection до правки тегов.
- Дефолт платформы не нейтрален — 307 у Cloudflare Static Assets невидим для билда, тестов и разметки.
- Письмо GSC не называет URL: цепочка «письмо → отчёт → URL Inspection» обязательна. Гипотезы «hi дублирует en» и «thin programmatic content» обе оказались ложными.
