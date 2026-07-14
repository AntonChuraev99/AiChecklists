#!/usr/bin/env node
// ---------------------------------------------------------------------------
// Gisti — Tier-1 programmatic-SEO checklist gallery generator.
//
// Reads   data/checklists/*.json  (one file per curated checklist)
// Uses    landing-src/checklists/partials/*.html + templates/*.html
// Emits   landing/checklists/index.html                          (gallery index)
//         landing/checklists/{category}/index.html               (hub per category)
//         landing/checklists/{category}/{slug}/index.html        (detail — money page)
//         landing/sitemap.xml                                    (regenerated, all URLs)
//
// Node 18+ (uses only node:fs / node:path / node:url — no deps, no fetch needed).
// Run from the repo root:  node landing-src/checklists/generate.mjs
//
// CORE RULE (anti-drift): the visible HTML and the JSON-LD for breadcrumbs,
// checklist items, and FAQ are built from the SAME data array in the SAME
// function — they can never drift. See buildBreadcrumb / buildItems / buildFaq.
// ---------------------------------------------------------------------------

import { readFileSync, writeFileSync, readdirSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO = join(__dirname, "..", "..");                 // repo root
const PARTIALS = join(__dirname, "partials");
const TEMPLATES = join(__dirname, "templates");
const DATA_DIR = join(REPO, "data", "checklists");
const OUT_DIR = join(REPO, "landing", "checklists");
const SITEMAP = join(REPO, "landing", "sitemap.xml");

const BASE = "https://gisti-ai.com";
const BUILD_VERSION = "20260714";      // CSS cache-bust ?v= (bump on asset change — NOT a content date)
const PLAY_URL = "https://play.google.com/store/apps/details?id=com.antonchuraev.aichecklists";
const APP_URL = "https://app.gisti-ai.com/";
const LOGO_URL = `${BASE}/apple-touch-icon.png`;
const OG_IMAGE = `${BASE}/og-image.png`;

// ── GEO / freshness dates ─────────────────────────────────────────────────────
// datePublished = when this dataset was authored & human-reviewed (FIXED — never
// auto-bumped). dateModified is per-file `updated` (see checklistDates) and defaults
// to this — so a re-deploy that changes NOTHING does NOT fake a fresher date.
// (best-practices-scout 2026: only bump dateModified on genuine content edits, and
//  keep the visible "Updated" label matching the schema value.)
const DATASET_PUBLISHED = "2026-07-14";

// ── Shared entity graph (GEO: entity consolidation) ───────────────────────────
// One Gisti Organization + WebSite repeated (by @id) across every gallery page so an
// AI engine building a knowledge graph sees a single, consistent publisher entity.
// sameAs lists only REAL Gisti properties (no fabricated social profiles).
const ORG_ID = `${BASE}/#organization`;
const WEBSITE_ID = `${BASE}/#website`;
const ORG_NODE = {
  "@type": "Organization",
  "@id": ORG_ID,
  name: "Gisti",
  url: `${BASE}/`,
  logo: LOGO_URL,
  sameAs: [PLAY_URL, APP_URL],
};
const WEBSITE_NODE = {
  "@type": "WebSite",
  "@id": WEBSITE_ID,
  name: "Gisti",
  url: `${BASE}/`,
  inLanguage: "en",
  publisher: { "@id": ORG_ID },
};

const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
// "2026-07-14" -> "July 2026" — honest, low-precision visible freshness label.
const humanMonth = (iso) => { const [y, m] = iso.split("-").map(Number); return `${MONTHS[m - 1]} ${y}`; };
// datePublished fixed; dateModified only advances when a file declares `updated`.
const checklistDates = (cl) => {
  const published = cl.datePublished || DATASET_PUBLISHED;
  const modified = cl.updated || published;
  return { published, modified };
};

// ── Category registry (spec §5). H1 / icon / tint are the deterministic §5 map;
//    hubIntro / tileDesc / meta* are authored copy. All 10 categories are present so
//    that adding data/checklists/*.json for a new category "just works" — only
//    categories that actually have ≥1 checklist file are rendered (no thin/empty hubs).
const CATEGORIES = {
  travel: {
    name: "Travel & Packing",
    h1: "AI Checklists for Travel & Packing",
    icon: "luggage", bg: "#DDF3F5", color: "#006874",
    tileDesc: "Packing and prep lists for city breaks, flights, beach holidays, and business travel.",
    metaTitle: "Travel & Packing Checklists (Free, AI-Ready) | Gisti",
    metaDescription: "Free, ready-to-use travel and packing checklists — city breaks, international flights, beach trips, and business travel. Use as-is or tailor any list with AI in Gisti.",
    hubIntro: 'Half the stress of any trip is the packing. These free travel and packing checklists cover short city breaks, long-haul flights, beach holidays, and business trips — each one written by hand with the things people actually forget. Use any list as-is, or open it in Gisti to tick items off, add your own, and let AI tailor it to your exact destination and dates. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.',
  },
  moving: {
    name: "Moving & Home",
    h1: "AI Moving & Home Checklists",
    icon: "local_shipping", bg: "#E3F2FD", color: "#2196F3",
    tileDesc: "Step-by-step lists for moving house, setting up a new place, and getting settled.",
    metaTitle: "Moving & New Home Checklists (Free, Step-by-Step) | Gisti",
    metaDescription: "Free moving and new-home checklists — moving house, first apartment essentials, change of address, and new-home setup. Follow the steps or tailor them with AI in Gisti.",
    hubIntro: 'Moving is a hundred small tasks stacked on top of a big one. These free moving and home checklists break it down — from the weeks-out moving-house timeline to first-apartment essentials, change-of-address admin, and settling into a new home. Work through any list as-is, or open it in Gisti to track your progress and let AI adapt it to your move. Browse the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.',
  },
  baby: {
    name: "Baby & Parenting",
    h1: "AI Baby & Parenting Checklists",
    icon: "child_friendly", bg: "#F0EAFB", color: "#7E5BD0",
    tileDesc: "Newborn, hospital-bag, registry, and baby-proofing lists for new and expecting parents.",
    metaTitle: "Baby & Parenting Checklists (Free, New-Parent Ready) | Gisti",
    metaDescription: "Free baby and parenting checklists — newborn first month, hospital bag, baby registry, diaper bag, and baby-proofing. Use as-is or tailor any list with AI in Gisti.",
    hubIntro: 'Preparing for a baby comes with a lot of lists — and not much spare time to write them. These free baby and parenting checklists cover the essentials new and expecting parents ask about most: what to pack in a hospital bag, what a newborn really needs in the first month, what to put on a registry, and how to baby-proof a home. Use any list as-is, or open it in Gisti to check things off and let AI tailor it to you. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a>.',
  },
  // ── Remaining §5 categories: registry complete (H1/icon/tint) so future data files
  //    render with no code change. Copy fields filled when their content is authored.
  wedding:   { name: "Wedding & Event",  h1: "AI Wedding & Event Checklists",   icon: "celebration",        bg: "#FFF8E1", color: "#D9A21E", tileDesc: "Planning checklists for weddings, parties, and big events.", metaTitle: "Wedding & Event Checklists (Free) | Gisti", metaDescription: "Free wedding and event planning checklists. Use as-is or tailor with AI in Gisti.", hubIntro: 'From a full 12-month wedding plan down to a Saturday-night birthday party, celebrations run on the same thing: a good list, started early. These free wedding and event checklists cover the big-day timeline, the vendor-booking countdown, and the everyday parties and events in between — each written by hand with the tasks people actually forget. Use any list as-is, or open it in Gisti to tick items off, assign owners, and let AI tailor it to your date and guest count. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.' },
  fitness:   { name: "Health & Fitness", h1: "AI Health & Fitness Checklists",  icon: "fitness_center",     bg: "#E7F4EC", color: "#2E9E5B", tileDesc: "Workout, gym-bag, and healthy-habit checklists.", metaTitle: "Health & Fitness Checklists (Free) | Gisti", metaDescription: "Free health and fitness checklists. Use as-is or tailor with AI in Gisti.", hubIntro: 'Staying healthy is mostly small habits done consistently — and a checklist is what keeps them from slipping. These free health and fitness checklists cover the daily routines and the bigger goals alike: a morning routine that sets your energy, a week of meal prep, a packed gym bag, a self-care reset, and a full marathon build. Use any list as-is, or open it in Gisti to track your streak and let AI tailor it to your goals. Browse the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.' },
  work:      { name: "Work & Onboarding", h1: "AI Work & Onboarding Checklists", icon: "work",              bg: "#E8EAFD", color: "#6366F1", tileDesc: "Onboarding, project, and workday checklists.", metaTitle: "Work & Onboarding Checklists (Free) | Gisti", metaDescription: "Free work and onboarding checklists. Use as-is or tailor with AI in Gisti.", hubIntro: 'The difference between a smooth week at work and a chaotic one is usually preparation — the onboarding done before day one, the kickoff that aligns everyone, the meeting with an actual agenda. These free work and onboarding checklists cover the moments that set the tone: welcoming a new hire, launching a project, running a tight standup, and setting up to work from home. Use any list as-is, or open it in Gisti to assign owners and let AI adapt it to your team. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.' },
  study:     { name: "Study & Exam",     h1: "AI Study & Exam Checklists",      icon: "school",             bg: "#E3F2FD", color: "#2196F3", tileDesc: "Study, revision, and exam-prep checklists.", metaTitle: "Study & Exam Checklists (Free) | Gisti", metaDescription: "Free study and exam checklists. Use as-is or tailor with AI in Gisti.", hubIntro: 'Studying is easier when the plan is already made. These free study and exam checklists cover what students search for most — building a realistic study plan, prepping for exams without an all-nighter, working through a thesis stage by stage, and packing for a new term or dorm. Use any list as-is, or open it in Gisti to tick items off, set reminders, and let AI tailor it to your course and deadlines. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.' },
  groceries: { name: "Grocery & Shopping", h1: "AI Grocery & Shopping Lists",   icon: "shopping_cart",      bg: "#E7F4EC", color: "#2E9E5B", tileDesc: "Grocery, meal-prep, and shopping lists.", metaTitle: "Grocery & Shopping Lists (Free) | Gisti", metaDescription: "Free grocery and shopping lists. Use as-is or tailor with AI in Gisti.", hubIntro: 'A good list is the difference between one calm shop and three trips back to the store. These free grocery and shopping lists cover a balanced weekly shop, healthy whole-food staples, a meal-plan template, party supplies, and the pantry basics every kitchen keeps on hand — each grouped by aisle so you shop in one loop. Use any list as-is, or open it in Gisti to check items off, add your own, and let AI tailor it to your household and budget. Browse the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.' },
  cleaning:  { name: "Cleaning & Chores", h1: "AI Cleaning & Chore Checklists", icon: "cleaning_services", bg: "#DDF3F5", color: "#006874", tileDesc: "Cleaning routines and chore checklists.", metaTitle: "Cleaning & Chore Checklists (Free) | Gisti", metaDescription: "Free cleaning and chore checklists. Use as-is or tailor with AI in Gisti.", hubIntro: 'Keeping a home clean is less about scrubbing harder and more about knowing what to do, and when. These free cleaning and chore checklists cover the whole range — a fast daily reset, a fair split of weekly and monthly chores, the twice-a-year deep clean, a spring refresh, and the thorough move-out clean that gets your deposit back. Use any list as-is, or open it in Gisti to tick jobs off, split them with your household, and let AI tailor a routine to your home. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.' },
  business:  { name: "Business & Launch", h1: "AI Business & Launch Checklists", icon: "checklist",          bg: "#E8EAFD", color: "#6366F1", tileDesc: "Launch and setup checklists for products, websites, startups, and small businesses.", metaTitle: "Business & Launch Checklists (Free) | Gisti", metaDescription: "Free business and launch checklists — product launch, website go-live, startup, and small-business setup. Use as-is or tailor with AI in Gisti.", hubIntro: 'Every launch and business milestone rides on not forgetting the small stuff. These free business checklists cover the big moments founders and teams sweat over — launching a product, taking a website live, starting up from an idea, setting up a small business, and keeping social media content on track. Use any list as-is, or open it in Gisti to work through the steps, assign them across your team, and let AI adapt each one to your situation. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.' },
};

const INDEX_INTRO =
  'Browse hand-written, ready-to-use checklists for travel, moving, new babies, and more — free, and crawlable so you can read the whole list right here. Every one opens in Gisti (the AI checklist app, unrelated to GitHub Gist) where you can tick items off, add your own, set reminders, and let AI tailor it to your exact situation on Android and the web.';

// ── Tiny helpers ────────────────────────────────────────────────────────────
const readPartial = (n) => readFileSync(join(PARTIALS, `${n}.html`), "utf8");
const readTemplate = (n) => readFileSync(join(TEMPLATES, `${n}.html`), "utf8");

// Replace {{key}} for provided keys; leave unknown tokens untouched (so we notice them).
const fill = (tpl, vars) => tpl.replace(/\{\{(\w+)\}\}/g, (m, k) => (k in vars ? String(vars[k]) : m));

// HTML-escape for TEXT nodes (apostrophes stay literal — valid in HTML text).
const escHtml = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
// HTML-escape for ATTRIBUTE values (also the double quote).
const escAttr = (s) => escHtml(s).replace(/"/g, "&quot;");

// JSON-LD → <script> string. Escape < as < so a stray "</script>" can't break out.
function jsonLdScript(obj) {
  const json = JSON.stringify(obj, null, 2).replace(/</g, "\\u003c");
  return `  <script type="application/ld+json">\n${json}\n  </script>`;
}

const detailUrl = (cat, slug) => `${BASE}/checklists/${cat}/${slug}/`;
const hubUrl = (cat) => `${BASE}/checklists/${cat}/`;
const indexUrl = `${BASE}/checklists/`;
const homeUrl = `${BASE}/`;

// ── Load partials/templates once ────────────────────────────────────────────
const P = {
  head: readPartial("head"),
  header: readPartial("header"),
  footer: readPartial("footer"),
  style: readPartial("style"),
  breadcrumb: readPartial("breadcrumb"),
  item: readPartial("checklist-item"),
  card: readPartial("checklist-card"),
  cta: readPartial("cta-block"),
  faq: readPartial("faq"),
};
const T = {
  page: readTemplate("page"),
  detail: readTemplate("detail"),
  hub: readTemplate("hub"),
  index: readTemplate("index"),
};

// ── Builders: each returns { html, ...jsonLd } from ONE array (no drift) ─────

// crumbs: [{ name, url? }]  (last = current page, no url) ; id → @id for @graph cross-ref
function buildBreadcrumb(crumbs, id) {
  const lis = crumbs
    .map((c, i) => {
      const last = i === crumbs.length - 1;
      const label = last
        ? `<span aria-current="page">${escHtml(c.name)}</span>`
        : `<a href="${escAttr(c.url)}" class="hover:text-primary">${escHtml(c.name)}</a>`;
      const sep = last ? "" : ` <span class="sep" aria-hidden="true">›</span>`;
      return `      <li>${label}${sep}</li>`;
    })
    .join("\n");
  const html = fill(P.breadcrumb, { items: lis });
  const ld = {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    ...(id ? { "@id": id } : {}),
    itemListElement: crumbs.map((c, i) => ({
      "@type": "ListItem",
      position: i + 1,
      name: c.name,
      ...(c.url ? { item: c.url } : {}),
    })),
  };
  return { html, ld };
}

// items: [{ text, note? }] ; ordered: boolean ; title used for ItemList/HowTo name ; id → @id
function buildItems(items, ordered, title, id) {
  const rows = items
    .map((it, i) => {
      const marker = ordered
        ? `<span aria-hidden="true" class="ai-fill w-8 h-8 rounded-full grid place-items-center text-white shrink-0" style="font-size:14px;font-weight:700">${i + 1}</span>`
        : `<span aria-hidden="true" class="material-symbols-outlined text-outline" style="font-size:22px">check_box_outline_blank</span>`;
      const note = it.note
        ? `          <p class="t-body-m text-on-surface-variant mt-0.5">${escHtml(it.note)}</p>`
        : "";
      return fill(P.item, { marker, text: escHtml(it.text), note });
    })
    .join("");
  const listOpen = ordered
    ? `<ol class="checklist space-y-3 mt-4">`
    : `<ul class="checklist space-y-2 mt-4">`;
  const listClose = ordered ? `</ol>` : `</ul>`;

  const itemListLd = {
    "@context": "https://schema.org",
    "@type": "ItemList",
    ...(id ? { "@id": id } : {}),
    name: title,
    numberOfItems: items.length,
    itemListElement: items.map((it, i) => ({
      "@type": "ListItem",
      position: i + 1,
      name: it.text,
      ...(it.note ? { description: it.note } : {}),
    })),
  };
  const howToLd = ordered
    ? {
        "@context": "https://schema.org",
        "@type": "HowTo",
        name: title,
        step: items.map((it, i) => ({
          "@type": "HowToStep",
          position: i + 1,
          name: it.text,
          text: it.note || it.text,
        })),
      }
    : null;
  return { rows, listOpen, listClose, itemListLd, howToLd };
}

// qa: [{ q, a }]
function buildFaq(qa) {
  const html = qa
    .map((x, i) => fill(P.faq, { open: i === 0 ? " open" : "", q: escHtml(x.q), a: escHtml(x.a) }))
    .join("");
  const ld = {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: qa.map((x) => ({
      "@type": "Question",
      name: x.q,
      acceptedAnswer: { "@type": "Answer", text: x.a },
    })),
  };
  return { html, ld };
}

// card: { href, icon, bg, color, title, desc, meta?, htag }
function buildCard(card) {
  const meta = card.meta
    ? `          <p class="t-body-m text-on-surface-variant mt-3 flex items-center gap-1"><span aria-hidden="true" class="material-symbols-outlined" style="font-size:16px">checklist</span>${escHtml(card.meta)}</p>`
    : "";
  return fill(P.card, {
    href: escAttr(card.href),
    icon: card.icon,
    bg: card.bg,
    color: card.color,
    htag: card.htag,
    title: escHtml(card.title),
    desc: escHtml(card.desc),
    meta,
  });
}

// ── Assemble a full page from a body-<main> + head vars ──────────────────────
function renderPage({ metaTitle, metaDescription, canonical, jsonLdBlocks, main }) {
  // GEO: emit ONE @graph (nodes cross-referenced by @id) instead of disconnected
  // <script> blocks — cleaner entity graph for AI crawlers. Strip each node's own
  // @context (the graph carries it once at the top).
  const graphNodes = jsonLdBlocks.map((n) => {
    const { ["@context"]: _ctx, ...rest } = n;
    return rest;
  });
  const graph = { "@context": "https://schema.org", "@graph": graphNodes };
  const head = fill(P.head, {
    title: escAttr(metaTitle),
    description: escAttr(metaDescription),
    canonical: escAttr(canonical),
    v: BUILD_VERSION,
    jsonld: jsonLdScript(graph),
  });
  return fill(T.page, { head, style: P.style, header: P.header, footer: P.footer, main });
}

function writePage(relDir, html) {
  const dir = join(OUT_DIR, relDir);
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, "index.html"), html, "utf8");
}

// ── Detail page ──────────────────────────────────────────────────────────────
function renderDetail(cl, byKey) {
  const cat = CATEGORIES[cl.category];
  const url = detailUrl(cl.category, cl.slug);
  const { published, modified } = checklistDates(cl);
  const crumbs = buildBreadcrumb(
    [
      { name: "Home", url: homeUrl },
      { name: "Checklists", url: indexUrl },
      { name: cat.name, url: hubUrl(cl.category) },
      { name: cl.title },
    ],
    `${url}#breadcrumb`
  );
  const list = buildItems(cl.items, !!cl.ordered, cl.title, `${url}#checklist`);
  const faq = buildFaq(cl.faq);

  // Related cards — resolve slugs (same-cat "slug" or cross-cat "cat/slug").
  const relatedCards = (cl.related || [])
    .map((ref) => {
      const key = ref.includes("/") ? ref : `${cl.category}/${ref}`;
      const t = byKey[key];
      if (!t) {
        console.warn(`  ! related target not found: "${ref}" (from ${cl.category}/${cl.slug})`);
        return null;
      }
      const tcat = CATEGORIES[t.category];
      return buildCard({
        href: detailUrl(t.category, t.slug),
        icon: tcat.icon, bg: tcat.bg, color: tcat.color, htag: "h3",
        title: t.title, desc: t.cardDesc, meta: `${t.items.length} items`,
      });
    })
    .filter(Boolean)
    .join("\n");

  const cta = fill(P.cta, { slug: escAttr(cl.slug), utmCampaign: "checklist_detail" });

  const main = fill(T.detail, {
    breadcrumb: crumbs.html,
    h1: escHtml(cl.title),
    intro: cl.intro, // authored raw HTML (may contain <a> to hub)
    reviewedIso: modified,
    reviewedLabel: humanMonth(modified),
    title: escHtml(cl.title),
    count: cl.items.length,
    listOpen: list.listOpen,
    listClose: list.listClose,
    items: list.rows,
    cta,
    related: relatedCards,
    faq: faq.html,
  });

  // WebPage wrapper — ties the page to the Gisti WebSite/Organization entities and
  // carries the freshness dates + author/publisher (GEO E-E-A-T + entity graph).
  const webPageLd = {
    "@type": "WebPage",
    "@id": `${url}#webpage`,
    url,
    name: cl.title,
    description: cl.metaDescription,
    inLanguage: "en",
    isPartOf: { "@id": WEBSITE_ID },
    breadcrumb: { "@id": `${url}#breadcrumb` },
    mainEntity: { "@id": `${url}#checklist` },
    about: { "@type": "Thing", name: cl.title },
    datePublished: published,
    dateModified: modified,
    author: { "@id": ORG_ID },
    publisher: { "@id": ORG_ID },
    primaryImageOfPage: { "@type": "ImageObject", url: OG_IMAGE },
  };

  const jsonLdBlocks = [
    webPageLd,
    crumbs.ld,
    list.itemListLd,
    ...(list.howToLd ? [list.howToLd] : []),
    faq.ld,
    WEBSITE_NODE,
    ORG_NODE,
  ];
  const html = renderPage({
    metaTitle: cl.metaTitle,
    metaDescription: cl.metaDescription,
    canonical: url,
    jsonLdBlocks,
    main,
  });
  writePage(join(cl.category, cl.slug), html);
  return { url, lastmod: modified };
}

// ── Hub page ─────────────────────────────────────────────────────────────────
function renderHub(catKey, children) {
  const cat = CATEGORIES[catKey];
  const url = hubUrl(catKey);
  // Hub freshness = the newest child's dateModified (honest: the hub changed when its
  // most-recently-edited checklist did). Falls back to the dataset date.
  const modified = children.map((c) => checklistDates(c).modified).sort().pop() || DATASET_PUBLISHED;
  const crumbs = buildBreadcrumb(
    [
      { name: "Home", url: homeUrl },
      { name: "Checklists", url: indexUrl },
      { name: cat.name },
    ],
    `${url}#breadcrumb`
  );
  const cards = children
    .map((c) =>
      buildCard({
        href: detailUrl(c.category, c.slug),
        icon: cat.icon, bg: cat.bg, color: cat.color, htag: "h3",
        title: c.title, desc: c.cardDesc, meta: `${c.items.length} items`,
      })
    )
    .join("\n");

  const main = fill(T.hub, {
    breadcrumb: crumbs.html,
    h1: escHtml(cat.h1),
    intro: cat.hubIntro,
    categoryName: escAttr(cat.name),
    cards,
  });

  const collectionLd = {
    "@context": "https://schema.org",
    "@type": "CollectionPage",
    "@id": `${url}#webpage`,
    name: cat.h1,
    description: cat.metaDescription,
    url,
    inLanguage: "en",
    isPartOf: { "@id": WEBSITE_ID },
    breadcrumb: { "@id": `${url}#breadcrumb` },
    datePublished: DATASET_PUBLISHED,
    dateModified: modified,
    publisher: { "@id": ORG_ID },
    mainEntity: {
      "@type": "ItemList",
      numberOfItems: children.length,
      itemListElement: children.map((c, i) => ({
        "@type": "ListItem",
        position: i + 1,
        url: detailUrl(c.category, c.slug),
        name: c.title,
      })),
    },
  };
  const html = renderPage({
    metaTitle: cat.metaTitle,
    metaDescription: cat.metaDescription,
    canonical: url,
    jsonLdBlocks: [collectionLd, crumbs.ld, WEBSITE_NODE, ORG_NODE],
    main,
  });
  writePage(catKey, html);
  return { url, lastmod: modified };
}

// ── Gallery index ────────────────────────────────────────────────────────────
function renderIndex(catKeys, countByCat, modified) {
  const crumbs = buildBreadcrumb([{ name: "Home", url: homeUrl }, { name: "Checklists" }], `${indexUrl}#breadcrumb`);
  const tiles = catKeys
    .map((k) => {
      const cat = CATEGORIES[k];
      return buildCard({
        href: hubUrl(k),
        icon: cat.icon, bg: cat.bg, color: cat.color, htag: "h2",
        title: cat.h1, desc: cat.tileDesc, meta: `${countByCat[k]} checklists`,
      });
    })
    .join("\n");

  const main = fill(T.index, { breadcrumb: crumbs.html, intro: INDEX_INTRO, tiles });

  const collectionLd = {
    "@context": "https://schema.org",
    "@type": "CollectionPage",
    "@id": `${indexUrl}#webpage`,
    name: "AI Checklist Gallery",
    description:
      "Free, ready-to-use AI checklists for travel, moving, new babies, and more — read the full list on the page, then open it in Gisti.",
    url: indexUrl,
    inLanguage: "en",
    isPartOf: { "@id": WEBSITE_ID },
    breadcrumb: { "@id": `${indexUrl}#breadcrumb` },
    datePublished: DATASET_PUBLISHED,
    dateModified: modified,
    publisher: { "@id": ORG_ID },
    mainEntity: {
      "@type": "ItemList",
      name: "AI Checklist Gallery",
      numberOfItems: catKeys.length,
      itemListElement: catKeys.map((k, i) => ({
        "@type": "ListItem",
        position: i + 1,
        url: hubUrl(k),
        name: CATEGORIES[k].h1,
      })),
    },
  };
  const html = renderPage({
    metaTitle: "AI Checklist Gallery — Free Ready-Made Checklists | Gisti",
    metaDescription:
      "Free, ready-to-use AI checklists for travel, moving, new babies, and more. Read the full list on the page, then open it in Gisti to track and tailor it on Android and the web.",
    canonical: indexUrl,
    jsonLdBlocks: [collectionLd, crumbs.ld, WEBSITE_NODE, ORG_NODE],
    main,
  });
  writePage("", html);
  return { url: indexUrl, lastmod: modified };
}

// ── Sitemap ──────────────────────────────────────────────────────────────────
function writeSitemap(indexEntry, hubs, details) {
  // lastmod carries each page's real dateModified (honest freshness — not a build stamp).
  const entry = (loc, lastmod, changefreq, priority) =>
    `  <url>\n    <loc>${loc}</loc>\n    <lastmod>${lastmod}</lastmod>\n    <changefreq>${changefreq}</changefreq>\n    <priority>${priority}</priority>\n  </url>`;
  const rows = [
    entry(`${BASE}/`, DATASET_PUBLISHED, "weekly", "1.0"),
    entry(`${BASE}/mcp/`, DATASET_PUBLISHED, "monthly", "0.8"),
    entry(indexEntry.url, indexEntry.lastmod, "weekly", "0.9"),
    ...hubs.map((h) => entry(h.url, h.lastmod, "weekly", "0.7")),
    ...details.map((d) => entry(d.url, d.lastmod, "monthly", "0.6")),
  ];
  const xml =
    `<?xml version="1.0" encoding="UTF-8"?>\n` +
    `<!-- Static sitemap for the Gisti landing (worker: gisti-landing, apex gisti-ai.com).\n` +
    `     Regenerated by landing-src/checklists/generate.mjs as the Tier-1 gallery grows.\n` +
    `     Segment into a sitemap index once >1k URLs. Ping IndexNow after deploy:\n` +
    `     node scripts/indexnow-ping.mjs -->\n` +
    `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n` +
    rows.join("\n") +
    `\n</urlset>\n`;
  writeFileSync(SITEMAP, xml, "utf8");
  return rows.length;
}

// ── Main ─────────────────────────────────────────────────────────────────────
function main() {
  const files = readdirSync(DATA_DIR).filter((f) => f.endsWith(".json") && !f.startsWith("_"));
  const checklists = files.map((f) => {
    const cl = JSON.parse(readFileSync(join(DATA_DIR, f), "utf8"));
    // Minimal validation — fail loud on missing structure.
    for (const req of ["category", "slug", "title", "metaTitle", "metaDescription", "intro", "items", "faq"]) {
      if (cl[req] == null) throw new Error(`${f}: missing required field "${req}"`);
    }
    if (!CATEGORIES[cl.category]) throw new Error(`${f}: unknown category "${cl.category}" (add it to CATEGORIES)`);
    if (!Array.isArray(cl.items) || cl.items.length < 1) throw new Error(`${f}: items must be a non-empty array`);
    return cl;
  });

  // Index by "category/slug" for related-lookup.
  const byKey = {};
  for (const cl of checklists) byKey[`${cl.category}/${cl.slug}`] = cl;

  // Group by category, preserving an optional numeric `order` (fallback: file order).
  const cats = {};
  for (const cl of checklists) (cats[cl.category] ||= []).push(cl);
  for (const k of Object.keys(cats)) cats[k].sort((a, b) => (a.order ?? 999) - (b.order ?? 999));

  // Render, following the fixed CATEGORIES order for anything with data.
  const catKeys = Object.keys(CATEGORIES).filter((k) => cats[k]?.length);
  const details = [];   // [{ url, lastmod }]
  const hubs = [];      // [{ url, lastmod }]
  const countByCat = {};

  for (const k of catKeys) {
    for (const cl of cats[k]) details.push(renderDetail(cl, byKey));
    hubs.push(renderHub(k, cats[k]));
    countByCat[k] = cats[k].length;
  }
  // Gallery index freshness = newest checklist edit across the whole set.
  const siteModified = details.map((d) => d.lastmod).sort().pop() || DATASET_PUBLISHED;
  const indexEntry = renderIndex(catKeys, countByCat, siteModified);

  const urlCount = writeSitemap(indexEntry, hubs, details);

  console.log(`Generated:`);
  console.log(`  ${details.length} detail pages`);
  console.log(`  ${hubs.length} hub pages (${catKeys.join(", ")})`);
  console.log(`  1 gallery index`);
  console.log(`  sitemap.xml with ${urlCount} URLs`);
  console.log(`Total: ${details.length + hubs.length + 1} pages under landing/checklists/`);
}

main();
