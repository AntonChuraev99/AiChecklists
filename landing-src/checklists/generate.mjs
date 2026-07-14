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

const MONTHS = {
  en: ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"],
  hi: ["जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्तूबर", "नवंबर", "दिसंबर"],
};
// "2026-07-14" -> "July 2026" / "जुलाई 2026" — honest, low-precision visible freshness label.
const humanMonth = (iso, loc = "en") => { const [y, m] = iso.split("-").map(Number); return `${(MONTHS[loc] || MONTHS.en)[m - 1]} ${y}`; };
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
    hi: {
      name: "यात्रा और पैकिंग",
      h1: "यात्रा और पैकिंग के लिए AI चेकलिस्ट",
      tileDesc: "सिटी ब्रेक, उड़ानों, बीच छुट्टियों, और बिज़नेस यात्रा के लिए पैकिंग और तैयारी की सूचियाँ।",
      metaTitle: "यात्रा और पैकिंग चेकलिस्ट (मुफ़्त, AI-तैयार) | Gisti",
      metaDescription: "मुफ़्त, तैयार यात्रा और पैकिंग चेकलिस्ट — सिटी ब्रेक, अंतरराष्ट्रीय उड़ानें, बीच यात्राएँ, और बिज़नेस यात्रा। जैसे हैं वैसे इस्तेमाल करें या Gisti में AI से किसी भी सूची को ढालें।",
      hubIntro: 'किसी भी यात्रा का आधा तनाव पैकिंग का होता है। ये मुफ़्त यात्रा और पैकिंग चेकलिस्ट छोटे सिटी ब्रेक, लंबी उड़ानों, बीच छुट्टियों, और बिज़नेस यात्राओं को कवर करती हैं — हर एक हाथ से लिखी गई उन चीज़ों के साथ जो लोग सचमुच भूल जाते हैं। किसी भी सूची को जैसे है वैसे इस्तेमाल करें, या Gisti में खोलकर आइटम टिक करें, अपने जोड़ें, और AI से इसे अपने गंतव्य और तारीख़ों के मुताबिक ढालें। और देखें पूरी <a href="/hi/checklists/" class="text-primary hover:underline">चेकलिस्ट गैलरी</a>।',
    },
  },
  moving: {
    name: "Moving & Home",
    h1: "AI Moving & Home Checklists",
    icon: "local_shipping", bg: "#E3F2FD", color: "#2196F3",
    tileDesc: "Step-by-step lists for moving house, setting up a new place, and getting settled.",
    metaTitle: "Moving & New Home Checklists (Free, Step-by-Step) | Gisti",
    metaDescription: "Free moving and new-home checklists — moving house, first apartment essentials, change of address, and new-home setup. Follow the steps or tailor them with AI in Gisti.",
    hubIntro: 'Moving is a hundred small tasks stacked on top of a big one. These free moving and home checklists break it down — from the weeks-out moving-house timeline to first-apartment essentials, change-of-address admin, and settling into a new home. Work through any list as-is, or open it in Gisti to track your progress and let AI adapt it to your move. Browse the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.',
    hi: {
      name: "घर बदलना और नया घर",
      h1: "घर बदलने और नए घर के लिए AI चेकलिस्ट",
      tileDesc: "घर बदलने, नई जगह सेट करने, और बसने के लिए कदम-दर-कदम सूचियाँ।",
      metaTitle: "मूविंग और नए घर की चेकलिस्ट (मुफ़्त, कदम-दर-कदम) | Gisti",
      metaDescription: "मुफ़्त मूविंग और नए-घर की चेकलिस्ट — घर बदलना, पहले अपार्टमेंट की ज़रूरी चीज़ें, पता बदलना, और नए-घर की सेटिंग। कदमों का पालन करें या Gisti में AI से ढालें।",
      hubIntro: 'घर बदलना एक बड़े काम के ऊपर सौ छोटे कामों का ढेर है। ये मुफ़्त मूविंग और होम चेकलिस्ट इसे तोड़ती हैं — हफ़्तों-पहले की मूविंग-हाउस टाइमलाइन से लेकर पहले अपार्टमेंट की ज़रूरी चीज़ें, पता-बदलने का काम, और नए घर में बसने तक। किसी भी सूची पर जैसे है वैसे काम करें, या Gisti में खोलकर अपनी प्रगति ट्रैक करें और AI से इसे अपनी मूविंग के मुताबिक ढालें। और देखें पूरी <a href="/hi/checklists/" class="text-primary hover:underline">चेकलिस्ट गैलरी</a>।',
    },
  },
  baby: {
    name: "Baby & Parenting",
    h1: "AI Baby & Parenting Checklists",
    icon: "child_friendly", bg: "#F0EAFB", color: "#7E5BD0",
    tileDesc: "Newborn, hospital-bag, registry, and baby-proofing lists for new and expecting parents.",
    metaTitle: "Baby & Parenting Checklists (Free, New-Parent Ready) | Gisti",
    metaDescription: "Free baby and parenting checklists — newborn first month, hospital bag, baby registry, diaper bag, and baby-proofing. Use as-is or tailor any list with AI in Gisti.",
    hubIntro: 'Preparing for a baby comes with a lot of lists — and not much spare time to write them. These free baby and parenting checklists cover the essentials new and expecting parents ask about most: what to pack in a hospital bag, what a newborn really needs in the first month, what to put on a registry, and how to baby-proof a home. Use any list as-is, or open it in Gisti to check things off and let AI tailor it to you. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a>.',
    hi: {
      name: "बच्चे और परवरिश",
      h1: "बच्चे और परवरिश के लिए AI चेकलिस्ट",
      tileDesc: "नए और होने वाले माता-पिता के लिए नवजात, हॉस्पिटल-बैग, रजिस्ट्री, और बेबी-प्रूफ़िंग सूचियाँ।",
      metaTitle: "बच्चे और परवरिश की चेकलिस्ट (मुफ़्त, नए-माता-पिता के लिए) | Gisti",
      metaDescription: "मुफ़्त बच्चे और परवरिश की चेकलिस्ट — नवजात का पहला महीना, हॉस्पिटल बैग, बेबी रजिस्ट्री, डायपर बैग, और बेबी-प्रूफ़िंग। जैसे हैं वैसे इस्तेमाल करें या Gisti में AI से ढालें।",
      hubIntro: 'बच्चे की तैयारी बहुत सारी सूचियों के साथ आती है — और उन्हें लिखने का ज़्यादा वक़्त नहीं होता। ये मुफ़्त बच्चे और परवरिश की चेकलिस्ट उन ज़रूरी चीज़ों को कवर करती हैं जो नए और होने वाले माता-पिता सबसे ज़्यादा पूछते हैं: हॉस्पिटल बैग में क्या पैक करें, नवजात को पहले महीने में सचमुच क्या चाहिए, रजिस्ट्री में क्या रखें, और घर को बेबी-प्रूफ़ कैसे करें। किसी भी सूची को जैसे है वैसे इस्तेमाल करें, या Gisti में खोलकर चीज़ें टिक करें और AI से इसे अपने मुताबिक ढालें। और देखें पूरी <a href="/hi/checklists/" class="text-primary hover:underline">चेकलिस्ट गैलरी</a>।',
    },
  },
  // ── Remaining §5 categories: registry complete (H1/icon/tint) so future data files
  //    render with no code change. Copy fields filled when their content is authored.
  wedding:   { name: "Wedding & Event",  h1: "AI Wedding & Event Checklists",   icon: "celebration",        bg: "#FFF8E1", color: "#D9A21E", tileDesc: "Planning checklists for weddings, parties, and big events.", metaTitle: "Wedding & Event Checklists (Free) | Gisti", metaDescription: "Free wedding and event planning checklists. Use as-is or tailor with AI in Gisti.", hubIntro: 'From a full 12-month wedding plan down to a Saturday-night birthday party, celebrations run on the same thing: a good list, started early. These free wedding and event checklists cover the big-day timeline, the vendor-booking countdown, and the everyday parties and events in between — each written by hand with the tasks people actually forget. Use any list as-is, or open it in Gisti to tick items off, assign owners, and let AI tailor it to your date and guest count. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.',
    hi: {
      name: "शादी और आयोजन",
      h1: "शादी और आयोजन के लिए AI चेकलिस्ट",
      tileDesc: "शादियों, पार्टियों, और बड़े आयोजनों के लिए प्लानिंग चेकलिस्ट।",
      metaTitle: "शादी और आयोजन की चेकलिस्ट (मुफ़्त) | Gisti",
      metaDescription: "मुफ़्त शादी और आयोजन प्लानिंग चेकलिस्ट। जैसे हैं वैसे इस्तेमाल करें या Gisti में AI से ढालें।",
      hubIntro: 'पूरी 12-महीने की शादी की योजना से लेकर शनिवार-रात की बर्थडे पार्टी तक, आयोजन एक ही चीज़ पर चलते हैं: एक अच्छी सूची, जल्दी शुरू की गई। ये मुफ़्त शादी और आयोजन चेकलिस्ट बड़े-दिन की टाइमलाइन, वेंडर-बुकिंग की उलटी गिनती, और बीच की रोज़मर्रा की पार्टियों व आयोजनों को कवर करती हैं — हर एक हाथ से लिखी उन कामों के साथ जो लोग सचमुच भूल जाते हैं। किसी भी सूची को जैसे है वैसे इस्तेमाल करें, या Gisti में खोलकर आइटम टिक करें, ज़िम्मेदारियाँ बाँटें, और AI से इसे अपनी तारीख़ और मेहमानों की संख्या के मुताबिक ढालें। और देखें पूरी <a href="/hi/checklists/" class="text-primary hover:underline">चेकलिस्ट गैलरी</a>।',
    } },
  fitness:   { name: "Health & Fitness", h1: "AI Health & Fitness Checklists",  icon: "fitness_center",     bg: "#E7F4EC", color: "#2E9E5B", tileDesc: "Workout, gym-bag, and healthy-habit checklists.", metaTitle: "Health & Fitness Checklists (Free) | Gisti", metaDescription: "Free health and fitness checklists. Use as-is or tailor with AI in Gisti.", hubIntro: 'Staying healthy is mostly small habits done consistently — and a checklist is what keeps them from slipping. These free health and fitness checklists cover the daily routines and the bigger goals alike: a morning routine that sets your energy, a week of meal prep, a packed gym bag, a self-care reset, and a full marathon build. Use any list as-is, or open it in Gisti to track your streak and let AI tailor it to your goals. Browse the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.',
    hi: {
      name: "सेहत और फ़िटनेस",
      h1: "सेहत और फ़िटनेस के लिए AI चेकलिस्ट",
      tileDesc: "वर्कआउट, जिम-बैग, और स्वस्थ-आदत चेकलिस्ट।",
      metaTitle: "सेहत और फ़िटनेस चेकलिस्ट (मुफ़्त) | Gisti",
      metaDescription: "मुफ़्त सेहत और फ़िटनेस चेकलिस्ट। जैसे हैं वैसे इस्तेमाल करें या Gisti में AI से ढालें।",
      hubIntro: 'स्वस्थ रहना ज़्यादातर छोटी आदतों को लगातार करने से बनता है — और एक चेकलिस्ट ही उन्हें फिसलने से रोकती है। ये मुफ़्त सेहत और फ़िटनेस चेकलिस्ट रोज़ की दिनचर्या और बड़े लक्ष्य दोनों को कवर करती हैं: एक मॉर्निंग रूटीन जो आपकी ऊर्जा तय करती है, एक हफ़्ते की मील-प्रेप, एक पैक किया जिम बैग, एक सेल्फ़-केयर रीसेट, और एक पूरी मैराथन तैयारी। किसी भी सूची को जैसे है वैसे इस्तेमाल करें, या Gisti में खोलकर अपनी स्ट्रीक ट्रैक करें और AI से इसे अपने लक्ष्यों के मुताबिक ढालें। और देखें पूरी <a href="/hi/checklists/" class="text-primary hover:underline">चेकलिस्ट गैलरी</a>।',
    } },
  work:      { name: "Work & Onboarding", h1: "AI Work & Onboarding Checklists", icon: "work",              bg: "#E8EAFD", color: "#6366F1", tileDesc: "Onboarding, project, and workday checklists.", metaTitle: "Work & Onboarding Checklists (Free) | Gisti", metaDescription: "Free work and onboarding checklists. Use as-is or tailor with AI in Gisti.", hubIntro: 'The difference between a smooth week at work and a chaotic one is usually preparation — the onboarding done before day one, the kickoff that aligns everyone, the meeting with an actual agenda. These free work and onboarding checklists cover the moments that set the tone: welcoming a new hire, launching a project, running a tight standup, and setting up to work from home. Use any list as-is, or open it in Gisti to assign owners and let AI adapt it to your team. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.',
    hi: {
      name: "काम और ऑनबोर्डिंग",
      h1: "काम और ऑनबोर्डिंग के लिए AI चेकलिस्ट",
      tileDesc: "ऑनबोर्डिंग, प्रोजेक्ट, और कार्यदिवस चेकलिस्ट।",
      metaTitle: "काम और ऑनबोर्डिंग चेकलिस्ट (मुफ़्त) | Gisti",
      metaDescription: "मुफ़्त काम और ऑनबोर्डिंग चेकलिस्ट। जैसे हैं वैसे इस्तेमाल करें या Gisti में AI से ढालें।",
      hubIntro: 'काम पर एक सहज हफ़्ते और एक अफ़रा-तफ़री वाले हफ़्ते के बीच का फ़र्क आमतौर पर तैयारी होता है — पहले दिन से पहले की गई ऑनबोर्डिंग, वह किकऑफ़ जो सबको एक पेज पर लाता है, वह मीटिंग जिसका सचमुच एजेंडा हो। ये मुफ़्त काम और ऑनबोर्डिंग चेकलिस्ट उन पलों को कवर करती हैं जो माहौल तय करते हैं: नए कर्मचारी का स्वागत, प्रोजेक्ट की शुरुआत, एक कसा हुआ स्टैंडअप, और घर से काम की सेटिंग। किसी भी सूची को जैसे है वैसे इस्तेमाल करें, या Gisti में खोलकर ज़िम्मेदारियाँ बाँटें और AI से इसे अपनी टीम के मुताबिक ढालें। और देखें पूरी <a href="/hi/checklists/" class="text-primary hover:underline">चेकलिस्ट गैलरी</a>।',
    } },
  study:     { name: "Study & Exam",     h1: "AI Study & Exam Checklists",      icon: "school",             bg: "#E3F2FD", color: "#2196F3", tileDesc: "Study, revision, and exam-prep checklists.", metaTitle: "Study & Exam Checklists (Free) | Gisti", metaDescription: "Free study and exam checklists. Use as-is or tailor with AI in Gisti.", hubIntro: 'Studying is easier when the plan is already made. These free study and exam checklists cover what students search for most — building a realistic study plan, prepping for exams without an all-nighter, working through a thesis stage by stage, and packing for a new term or dorm. Use any list as-is, or open it in Gisti to tick items off, set reminders, and let AI tailor it to your course and deadlines. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.',
    hi: {
      name: "पढ़ाई और परीक्षा",
      h1: "पढ़ाई और परीक्षा के लिए AI चेकलिस्ट",
      tileDesc: "पढ़ाई, रिवीज़न, और परीक्षा-तैयारी चेकलिस्ट।",
      metaTitle: "पढ़ाई और परीक्षा चेकलिस्ट (मुफ़्त) | Gisti",
      metaDescription: "मुफ़्त पढ़ाई और परीक्षा चेकलिस्ट। जैसे हैं वैसे इस्तेमाल करें या Gisti में AI से ढालें।",
      hubIntro: 'पढ़ाई तब आसान होती है जब योजना पहले से बनी हो। ये मुफ़्त पढ़ाई और परीक्षा चेकलिस्ट वह कवर करती हैं जो छात्र सबसे ज़्यादा खोजते हैं — एक व्यावहारिक स्टडी प्लान बनाना, बिना रात-भर जागे परीक्षा की तैयारी, चरण-दर-चरण थीसिस पर काम, और नए सत्र या हॉस्टल के लिए पैकिंग। किसी भी सूची को जैसे है वैसे इस्तेमाल करें, या Gisti में खोलकर चीज़ें टिक करें, रिमाइंडर सेट करें, और AI से इसे अपने कोर्स और डेडलाइन के मुताबिक ढालें। और देखें पूरी <a href="/hi/checklists/" class="text-primary hover:underline">चेकलिस्ट गैलरी</a>।',
    } },
  groceries: { name: "Grocery & Shopping", h1: "AI Grocery & Shopping Lists",   icon: "shopping_cart",      bg: "#E7F4EC", color: "#2E9E5B", tileDesc: "Grocery, meal-prep, and shopping lists.", metaTitle: "Grocery & Shopping Lists (Free) | Gisti", metaDescription: "Free grocery and shopping lists. Use as-is or tailor with AI in Gisti.", hubIntro: 'A good list is the difference between one calm shop and three trips back to the store. These free grocery and shopping lists cover a balanced weekly shop, healthy whole-food staples, a meal-plan template, party supplies, and the pantry basics every kitchen keeps on hand — each grouped by aisle so you shop in one loop. Use any list as-is, or open it in Gisti to check items off, add your own, and let AI tailor it to your household and budget. Browse the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.',
    hi: {
      name: "किराना और खरीदारी",
      h1: "किराना और खरीदारी की AI सूचियाँ",
      tileDesc: "किराना, मील-प्रेप, और खरीदारी की सूचियाँ।",
      metaTitle: "किराना और खरीदारी की सूचियाँ (मुफ़्त) | Gisti",
      metaDescription: "मुफ़्त किराना और खरीदारी की सूचियाँ। जैसे हैं वैसे इस्तेमाल करें या Gisti में AI से ढालें।",
      hubIntro: 'एक अच्छी सूची एक शांत खरीदारी और स्टोर के तीन चक्कर के बीच का फ़र्क है। ये मुफ़्त किराना और खरीदारी की सूचियाँ एक संतुलित साप्ताहिक खरीदारी, सेहतमंद साबुत-भोजन की बुनियादी चीज़ें, एक मील-प्लान टेम्पलेट, पार्टी का सामान, और हर रसोई में रखी जाने वाली पैंट्री की बुनियादी चीज़ें कवर करती हैं — हर एक गलियारे के हिसाब से समूहित ताकि आप एक ही चक्कर में खरीदें। किसी भी सूची को जैसे है वैसे इस्तेमाल करें, या Gisti में खोलकर चीज़ें टिक करें, अपने जोड़ें, और AI से इसे अपने घर और बजट के मुताबिक ढालें। और देखें पूरी <a href="/hi/checklists/" class="text-primary hover:underline">चेकलिस्ट गैलरी</a>।',
    } },
  cleaning:  { name: "Cleaning & Chores", h1: "AI Cleaning & Chore Checklists", icon: "cleaning_services", bg: "#DDF3F5", color: "#006874", tileDesc: "Cleaning routines and chore checklists.", metaTitle: "Cleaning & Chore Checklists (Free) | Gisti", metaDescription: "Free cleaning and chore checklists. Use as-is or tailor with AI in Gisti.", hubIntro: 'Keeping a home clean is less about scrubbing harder and more about knowing what to do, and when. These free cleaning and chore checklists cover the whole range — a fast daily reset, a fair split of weekly and monthly chores, the twice-a-year deep clean, a spring refresh, and the thorough move-out clean that gets your deposit back. Use any list as-is, or open it in Gisti to tick jobs off, split them with your household, and let AI tailor a routine to your home. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.',
    hi: {
      name: "सफ़ाई और घरेलू काम",
      h1: "सफ़ाई और घरेलू काम की AI चेकलिस्ट",
      tileDesc: "सफ़ाई की दिनचर्या और घरेलू-काम चेकलिस्ट।",
      metaTitle: "सफ़ाई और घरेलू-काम चेकलिस्ट (मुफ़्त) | Gisti",
      metaDescription: "मुफ़्त सफ़ाई और घरेलू-काम चेकलिस्ट। जैसे हैं वैसे इस्तेमाल करें या Gisti में AI से ढालें।",
      hubIntro: 'घर को साफ़ रखना ज़्यादा रगड़ने के बारे में कम और यह जानने के बारे में ज़्यादा है कि क्या करना है, और कब। ये मुफ़्त सफ़ाई और घरेलू-काम चेकलिस्ट पूरी रेंज कवर करती हैं — एक तेज़ रोज़ाना रीसेट, साप्ताहिक और मासिक कामों का सही बँटवारा, साल में दो बार की गहरी सफ़ाई, एक वसंत की ताज़गी, और वह पूरी मूव-आउट सफ़ाई जो आपकी जमा-राशि वापस दिलाती है। किसी भी सूची को जैसे है वैसे इस्तेमाल करें, या Gisti में खोलकर काम टिक करें, उन्हें अपने घर वालों के साथ बाँटें, और AI से अपने घर के मुताबिक एक दिनचर्या ढालें। और देखें पूरी <a href="/hi/checklists/" class="text-primary hover:underline">चेकलिस्ट गैलरी</a>।',
    } },
  business:  { name: "Business & Launch", h1: "AI Business & Launch Checklists", icon: "checklist",          bg: "#E8EAFD", color: "#6366F1", tileDesc: "Launch and setup checklists for products, websites, startups, and small businesses.", metaTitle: "Business & Launch Checklists (Free) | Gisti", metaDescription: "Free business and launch checklists — product launch, website go-live, startup, and small-business setup. Use as-is or tailor with AI in Gisti.", hubIntro: 'Every launch and business milestone rides on not forgetting the small stuff. These free business checklists cover the big moments founders and teams sweat over — launching a product, taking a website live, starting up from an idea, setting up a small business, and keeping social media content on track. Use any list as-is, or open it in Gisti to work through the steps, assign them across your team, and let AI adapt each one to your situation. See the full <a href="/checklists/" class="text-primary hover:underline">checklist gallery</a> for more.',
    hi: {
      name: "बिज़नेस और लॉन्च",
      h1: "बिज़नेस और लॉन्च के लिए AI चेकलिस्ट",
      tileDesc: "प्रोडक्ट, वेबसाइट, स्टार्टअप, और छोटे बिज़नेस के लिए लॉन्च और सेटअप चेकलिस्ट।",
      metaTitle: "बिज़नेस और लॉन्च चेकलिस्ट (मुफ़्त) | Gisti",
      metaDescription: "मुफ़्त बिज़नेस और लॉन्च चेकलिस्ट — प्रोडक्ट लॉन्च, वेबसाइट गो-लाइव, स्टार्टअप, और छोटे-बिज़नेस सेटअप। जैसे हैं वैसे इस्तेमाल करें या Gisti में AI से ढालें।",
      hubIntro: 'हर लॉन्च और बिज़नेस पड़ाव छोटी चीज़ों को न भूलने पर टिका होता है। ये मुफ़्त बिज़नेस चेकलिस्ट उन बड़े पलों को कवर करती हैं जिनकी संस्थापक और टीमें चिंता करती हैं — एक प्रोडक्ट लॉन्च करना, एक वेबसाइट लाइव करना, एक आइडिया से स्टार्टअप शुरू करना, एक छोटा बिज़नेस सेट करना, और सोशल मीडिया कंटेंट को पटरी पर रखना। किसी भी सूची को जैसे है वैसे इस्तेमाल करें, या Gisti में खोलकर कदमों पर काम करें, उन्हें अपनी टीम में बाँटें, और AI से हर एक को अपनी स्थिति के मुताबिक ढालें। और देखें पूरी <a href="/hi/checklists/" class="text-primary hover:underline">चेकलिस्ट गैलरी</a>।',
    } },
};

// ── i18n / locales ────────────────────────────────────────────────────────────
// Locale-aware output on ONE domain (Google-preferred subdirectory pattern):
//   en → /checklists/...            (root, unchanged — the default/x-default)
//   hi → /hi/checklists/...         (Hindi, Devanagari)
// A hi page is emitted ONLY when its source declares a `hi` block (checklist.hi /
// category.hi). Pages with no translation stay en-only and carry NO hreflang — an
// untranslated page needs no annotations. When a hi twin exists, BOTH pages get
// reciprocal hreflang (en + hi) plus exactly one x-default → the English URL.
// Nav/footer chrome links stay English on purpose: they point at English-only
// sections (gisti-ai.com/#features, app.gisti-ai.com) with no hi target.
const LOCALES = ["en", "hi"];
const isDefaultLocale = (loc) => loc === "en";
const localeSeg = (loc) => (isDefaultLocale(loc) ? "" : `/${loc}`);

// Devanagari webfont — loaded ONLY on hi pages (unicode-range gates the file, but
// we also skip the CSS request entirely on en). Noto Sans Devanagari + font-display swap.
const DEVANAGARI_FONT =
  '  <link href="https://fonts.googleapis.com/css2?family=Noto+Sans+Devanagari:wght@400;500;600;700&display=swap" rel="stylesheet" />';
const fontsExtra = (loc) => (loc === "hi" ? DEVANAGARI_FONT : "");

// Reciprocal hreflang tags + single x-default→en. alt = { en, hi } absolute URLs;
// hi may be null (no twin) → emit nothing (a lone page needs no hreflang).
function hreflangTags(alt) {
  if (!alt || !alt.hi) return "";
  return [
    `  <link rel="alternate" hreflang="en" href="${escAttr(alt.en)}" />`,
    `  <link rel="alternate" hreflang="hi" href="${escAttr(alt.hi)}" />`,
    `  <link rel="alternate" hreflang="x-default" href="${escAttr(alt.en)}" />`,
  ].join("\n");
}

// UI shell strings per locale (the crawlable, indexable chrome around the content).
const UI = {
  en: {
    crumbHome: "Home",
    crumbChecklists: "Checklists",
    itemsWord: "items",
    checklistsWord: "checklists",
    curated: "Curated by the Gisti team · Updated",
    related: "Related checklists",
    faqHeading: "Frequently asked questions",
    makeTitle: "Make it yours in Gisti",
    makeBody:
      "Open this checklist in Gisti to tick items off, add your own, set reminders, and keep it in sync across Android and the web. AI can tailor every item to your exact situation.",
    openGisti: "Open Gisti",
    ctaTitle: "Open this checklist in Gisti",
    ctaUse: "Use this checklist in Gisti",
    ctaOpenWeb: "Open the web app",
    ctaTrust: "Free to start · Works on Android &amp; the web · Not affiliated with GitHub Gist.",
    indexH1: "AI Checklist Gallery — Ready-Made Lists for Anything",
    indexMetaTitle: "AI Checklist Gallery — Free Ready-Made Checklists | Gisti",
    indexMetaDescription:
      "Free, ready-to-use AI checklists for travel, moving, new babies, and more. Read the full list on the page, then open it in Gisti to track and tailor it on Android and the web.",
    indexIntro:
      "Browse hand-written, ready-to-use checklists for travel, moving, new babies, and more — free, and crawlable so you can read the whole list right here. Every one opens in Gisti (the AI checklist app, unrelated to GitHub Gist) where you can tick items off, add your own, set reminders, and let AI tailor it to your exact situation on Android and the web.",
    indexBottomTitle: "Turn your next idea into a checklist",
    indexBottomBody:
      "Free on Android and the web — snap a photo, paste a link, or just speak, and let AI do the rest. No signup wall to try it.",
    hubBottomTitle: "Start your own checklist with AI",
    hubBottomBody:
      "Free on Android and the web — snap a photo, paste a link, or just speak, and let AI build and fill your checklist in seconds.",
    bottomOpenWeb: "Open the web app",
  },
  hi: {
    crumbHome: "होम",
    crumbChecklists: "चेकलिस्ट",
    itemsWord: "आइटम",
    checklistsWord: "चेकलिस्ट",
    curated: "Gisti टीम द्वारा तैयार · अपडेटेड",
    related: "संबंधित चेकलिस्ट",
    faqHeading: "अक्सर पूछे जाने वाले सवाल",
    makeTitle: "इसे Gisti में अपना बनाएं",
    makeBody:
      "इस चेकलिस्ट को Gisti में खोलें — आइटम टिक करें, अपने जोड़ें, रिमाइंडर सेट करें, और इसे Android व वेब पर सिंक रखें। AI हर आइटम को आपकी ज़रूरत के मुताबिक ढाल सकता है।",
    openGisti: "Gisti खोलें",
    ctaTitle: "इस चेकलिस्ट को Gisti में खोलें",
    ctaUse: "इस चेकलिस्ट को Gisti में इस्तेमाल करें",
    ctaOpenWeb: "वेब ऐप खोलें",
    ctaTrust: "शुरू करना मुफ़्त · Android और वेब पर उपलब्ध · GitHub Gist से संबद्ध नहीं।",
    indexH1: "AI चेकलिस्ट गैलरी — हर काम के लिए तैयार सूचियाँ",
    indexMetaTitle: "AI चेकलिस्ट गैलरी — मुफ़्त तैयार चेकलिस्ट | Gisti",
    indexMetaDescription:
      "यात्रा, घर बदलने, नवजात शिशु, और बहुत कुछ के लिए मुफ़्त, तैयार AI चेकलिस्ट। पूरी सूची पेज पर पढ़ें, फिर Gisti में खोलकर इसे Android व वेब पर ट्रैक और अपने मुताबिक ढालें।",
    indexIntro:
      "यात्रा, घर बदलने, नवजात शिशु, और बहुत कुछ के लिए हाथ से लिखी, तैयार चेकलिस्ट ब्राउज़ करें — मुफ़्त, और क्रॉल-योग्य ताकि आप पूरी सूची यहीं पढ़ सकें। हर एक Gisti (AI चेकलिस्ट ऐप, GitHub Gist से असंबंधित) में खुलती है, जहाँ आप आइटम टिक कर सकते हैं, अपने जोड़ सकते हैं, रिमाइंडर सेट कर सकते हैं, और Android व वेब पर AI से इसे अपनी ज़रूरत के मुताबिक ढाल सकते हैं।",
    indexBottomTitle: "अपने अगले आइडिया को चेकलिस्ट बनाएं",
    indexBottomBody:
      "Android और वेब पर मुफ़्त — फ़ोटो खींचें, लिंक पेस्ट करें, या बस बोलें, और बाकी AI पर छोड़ दें। आज़माने के लिए कोई साइनअप ज़रूरी नहीं।",
    hubBottomTitle: "AI के साथ अपनी चेकलिस्ट बनाएं",
    hubBottomBody:
      "Android और वेब पर मुफ़्त — फ़ोटो खींचें, लिंक पेस्ट करें, या बस बोलें, और AI सेकंडों में आपकी चेकलिस्ट बना और भर देगा।",
    bottomOpenWeb: "वेब ऐप खोलें",
  },
};

// Overlay a checklist's `hi` block onto its structure (structural fields — category,
// slug, ordered, related — always come from the base). en → base unchanged.
function locData(cl, loc) {
  if (loc === "en" || !cl.hi) return cl;
  const t = cl.hi;
  return {
    ...cl,
    title: t.title ?? cl.title,
    metaTitle: t.metaTitle ?? cl.metaTitle,
    metaDescription: t.metaDescription ?? cl.metaDescription,
    cardDesc: t.cardDesc ?? cl.cardDesc,
    intro: t.intro ?? cl.intro,
    items: t.items ?? cl.items,
    faq: t.faq ?? cl.faq,
  };
}
const clHasLoc = (cl, loc) => loc === "en" || !!cl.hi;

// Overlay a category's `hi` block (name/h1/tileDesc/metaTitle/metaDescription/hubIntro).
function catLoc(cat, loc) {
  if (loc === "en" || !cat.hi) return cat;
  return { ...cat, ...cat.hi };
}
const catHasLoc = (cat, loc) => loc === "en" || !!cat.hi;

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

const detailUrl = (cat, slug, loc = "en") => `${BASE}${localeSeg(loc)}/checklists/${cat}/${slug}/`;
const hubUrl = (cat, loc = "en") => `${BASE}${localeSeg(loc)}/checklists/${cat}/`;
const indexUrl = (loc = "en") => `${BASE}${localeSeg(loc)}/checklists/`;
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
function renderPage({ metaTitle, metaDescription, canonical, jsonLdBlocks, main, locale = "en", alt = null }) {
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
    hreflang: hreflangTags(alt),   // reciprocal en/hi + x-default, or "" when no twin
    fontsExtra: fontsExtra(locale), // Devanagari webfont on hi only
    v: BUILD_VERSION,
    jsonld: jsonLdScript(graph),
  });
  return fill(T.page, { head, style: P.style, header: P.header, footer: P.footer, main, lang: locale });
}

function writePage(relDir, html, loc = "en") {
  // en → landing/checklists/... (root, unchanged) ; hi → landing/hi/checklists/...
  const base = loc === "en" ? OUT_DIR : join(REPO, "landing", loc, "checklists");
  const dir = join(base, relDir);
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, "index.html"), html, "utf8");
}

// ── Detail page ──────────────────────────────────────────────────────────────
function renderDetail(cl, byKey, loc = "en") {
  const ui = UI[loc];
  const d = locData(cl, loc);                       // localized content fields
  const cat = catLoc(CATEGORIES[cl.category], loc); // localized category name (breadcrumb)
  const url = detailUrl(cl.category, cl.slug, loc);
  const { published, modified } = checklistDates(cl);
  const crumbs = buildBreadcrumb(
    [
      { name: ui.crumbHome, url: homeUrl },
      { name: ui.crumbChecklists, url: indexUrl(loc) },
      { name: cat.name, url: hubUrl(cl.category, loc) },
      { name: d.title },
    ],
    `${url}#breadcrumb`
  );
  const list = buildItems(d.items, !!cl.ordered, d.title, `${url}#checklist`);
  const faq = buildFaq(d.faq);

  // Related cards — resolve slugs (same-cat "slug" or cross-cat "cat/slug").
  // A related target links to its OWN locale twin when it has one, else falls back
  // to English (so a hi page never links to a non-existent hi target).
  const relatedCards = (cl.related || [])
    .map((ref) => {
      const key = ref.includes("/") ? ref : `${cl.category}/${ref}`;
      const t = byKey[key];
      if (!t) {
        console.warn(`  ! related target not found: "${ref}" (from ${cl.category}/${cl.slug})`);
        return null;
      }
      const tLoc = clHasLoc(t, loc) ? loc : "en";
      const td = locData(t, tLoc);
      const tcat = CATEGORIES[t.category]; // icon/bg/color are locale-neutral
      return buildCard({
        href: detailUrl(t.category, t.slug, tLoc),
        icon: tcat.icon, bg: tcat.bg, color: tcat.color, htag: "h3",
        title: td.title, desc: td.cardDesc, meta: `${t.items.length} ${ui.itemsWord}`,
      });
    })
    .filter(Boolean)
    .join("\n");

  const cta = fill(P.cta, {
    slug: escAttr(cl.slug),
    utmCampaign: "checklist_detail",
    uiCtaTitle: ui.ctaTitle, uiCtaUse: ui.ctaUse, uiCtaOpenWeb: ui.ctaOpenWeb, uiCtaTrust: ui.ctaTrust,
  });

  const main = fill(T.detail, {
    breadcrumb: crumbs.html,
    h1: escHtml(d.title),
    intro: d.intro, // authored raw HTML (may contain <a> to hub)
    reviewedIso: modified,
    reviewedLabel: humanMonth(modified, loc),
    title: escHtml(d.title),
    count: cl.items.length,
    listOpen: list.listOpen,
    listClose: list.listClose,
    items: list.rows,
    cta,
    related: relatedCards,
    faq: faq.html,
    uiCurated: ui.curated, uiItems: ui.itemsWord, uiMakeTitle: ui.makeTitle,
    uiMakeBody: ui.makeBody, uiOpenGisti: ui.openGisti, uiRelated: ui.related,
    uiFaqHeading: ui.faqHeading,
  });

  // WebPage wrapper — ties the page to the Gisti WebSite/Organization entities and
  // carries the freshness dates + author/publisher (GEO E-E-A-T + entity graph).
  // @id is the locale URL → hi and en nodes never collide.
  const webPageLd = {
    "@type": "WebPage",
    "@id": `${url}#webpage`,
    url,
    name: d.title,
    description: d.metaDescription,
    inLanguage: loc,
    isPartOf: { "@id": WEBSITE_ID },
    breadcrumb: { "@id": `${url}#breadcrumb` },
    mainEntity: { "@id": `${url}#checklist` },
    about: { "@type": "Thing", name: d.title },
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
  // hreflang alternates: en always exists; hi only when this checklist is translated.
  const alt = {
    en: detailUrl(cl.category, cl.slug, "en"),
    hi: clHasLoc(cl, "hi") ? detailUrl(cl.category, cl.slug, "hi") : null,
  };
  const html = renderPage({
    metaTitle: d.metaTitle,
    metaDescription: d.metaDescription,
    canonical: url,
    jsonLdBlocks,
    main,
    locale: loc,
    alt,
  });
  writePage(join(cl.category, cl.slug), html, loc);
  return { url, lastmod: modified };
}

// ── Hub page ─────────────────────────────────────────────────────────────────
function renderHub(catKey, children, loc = "en") {
  const ui = UI[loc];
  const catBase = CATEGORIES[catKey];
  const cat = catLoc(catBase, loc);
  const url = hubUrl(catKey, loc);
  // Hub freshness = the newest child's dateModified (honest: the hub changed when its
  // most-recently-edited checklist did). Falls back to the dataset date.
  const modified = children.map((c) => checklistDates(c).modified).sort().pop() || DATASET_PUBLISHED;
  const crumbs = buildBreadcrumb(
    [
      { name: ui.crumbHome, url: homeUrl },
      { name: ui.crumbChecklists, url: indexUrl(loc) },
      { name: cat.name },
    ],
    `${url}#breadcrumb`
  );
  // A card links to the child's own-locale twin when translated, else to English.
  const childLoc = (c) => (clHasLoc(c, loc) ? loc : "en");
  const cards = children
    .map((c) => {
      const cd = locData(c, childLoc(c));
      return buildCard({
        href: detailUrl(c.category, c.slug, childLoc(c)),
        icon: catBase.icon, bg: catBase.bg, color: catBase.color, htag: "h3",
        title: cd.title, desc: cd.cardDesc, meta: `${c.items.length} ${ui.itemsWord}`,
      });
    })
    .join("\n");

  const main = fill(T.hub, {
    breadcrumb: crumbs.html,
    h1: escHtml(cat.h1),
    intro: cat.hubIntro,
    categoryName: escAttr(cat.name),
    cards,
    uiBottomTitle: ui.hubBottomTitle, uiBottomBody: ui.hubBottomBody, uiBottomOpenWeb: ui.bottomOpenWeb,
  });

  const collectionLd = {
    "@context": "https://schema.org",
    "@type": "CollectionPage",
    "@id": `${url}#webpage`,
    name: cat.h1,
    description: cat.metaDescription,
    url,
    inLanguage: loc,
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
        url: detailUrl(c.category, c.slug, childLoc(c)),
        name: locData(c, childLoc(c)).title,
      })),
    },
  };
  const alt = {
    en: hubUrl(catKey, "en"),
    hi: catHasLoc(catBase, "hi") ? hubUrl(catKey, "hi") : null,
  };
  const html = renderPage({
    metaTitle: cat.metaTitle,
    metaDescription: cat.metaDescription,
    canonical: url,
    jsonLdBlocks: [collectionLd, crumbs.ld, WEBSITE_NODE, ORG_NODE],
    main,
    locale: loc,
    alt,
  });
  writePage(catKey, html, loc);
  return { url, lastmod: modified };
}

// ── Gallery index ────────────────────────────────────────────────────────────
function renderIndex(catKeys, countByCat, modified, loc = "en", hiIndexExists = false) {
  const ui = UI[loc];
  const url = indexUrl(loc);
  const crumbs = buildBreadcrumb([{ name: ui.crumbHome, url: homeUrl }, { name: ui.crumbChecklists }], `${url}#breadcrumb`);
  const tiles = catKeys
    .map((k) => {
      const catBase = CATEGORIES[k];
      const cat = catLoc(catBase, loc);
      return buildCard({
        href: hubUrl(k, loc),
        icon: catBase.icon, bg: catBase.bg, color: catBase.color, htag: "h2",
        title: cat.h1, desc: cat.tileDesc, meta: `${countByCat[k]} ${ui.checklistsWord}`,
      });
    })
    .join("\n");

  const main = fill(T.index, {
    breadcrumb: crumbs.html,
    h1: escHtml(ui.indexH1),
    intro: ui.indexIntro,
    tiles,
    uiBottomTitle: ui.indexBottomTitle, uiBottomBody: ui.indexBottomBody, uiBottomOpenWeb: ui.bottomOpenWeb,
  });

  const collectionLd = {
    "@context": "https://schema.org",
    "@type": "CollectionPage",
    "@id": `${url}#webpage`,
    name: ui.indexH1,
    description: ui.indexMetaDescription,
    url,
    inLanguage: loc,
    isPartOf: { "@id": WEBSITE_ID },
    breadcrumb: { "@id": `${url}#breadcrumb` },
    datePublished: DATASET_PUBLISHED,
    dateModified: modified,
    publisher: { "@id": ORG_ID },
    mainEntity: {
      "@type": "ItemList",
      name: ui.indexH1,
      numberOfItems: catKeys.length,
      itemListElement: catKeys.map((k, i) => ({
        "@type": "ListItem",
        position: i + 1,
        url: hubUrl(k, loc),
        name: catLoc(CATEGORIES[k], loc).h1,
      })),
    },
  };
  const alt = {
    en: indexUrl("en"),
    hi: hiIndexExists ? indexUrl("hi") : null,
  };
  const html = renderPage({
    metaTitle: ui.indexMetaTitle,
    metaDescription: ui.indexMetaDescription,
    canonical: url,
    jsonLdBlocks: [collectionLd, crumbs.ld, WEBSITE_NODE, ORG_NODE],
    main,
    locale: loc,
    alt,
  });
  writePage("", html, loc);
  return { url, lastmod: modified };
}

// ── Sitemap ──────────────────────────────────────────────────────────────────
// pages: [{ url, lastmod, changefreq, priority, alt? }] where alt = { en, hi|null }.
// Each localized URL carries the SAME reciprocal xhtml:link set (en + hi + x-default→en)
// — generated from one alt map so head-hreflang and sitemap-hreflang can never drift.
function writeSitemap(pages) {
  const altLinks = (alt) =>
    alt && alt.hi
      ? [
          `    <xhtml:link rel="alternate" hreflang="en" href="${alt.en}"/>`,
          `    <xhtml:link rel="alternate" hreflang="hi" href="${alt.hi}"/>`,
          `    <xhtml:link rel="alternate" hreflang="x-default" href="${alt.en}"/>`,
          "",
        ].join("\n")
      : "";
  // lastmod carries each page's real dateModified (honest freshness — not a build stamp).
  const entry = ({ url, lastmod, changefreq, priority, alt }) =>
    `  <url>\n    <loc>${url}</loc>\n    <lastmod>${lastmod}</lastmod>\n    <changefreq>${changefreq}</changefreq>\n    <priority>${priority}</priority>\n${altLinks(alt)}  </url>`;
  const rows = pages.map(entry);
  const xml =
    `<?xml version="1.0" encoding="UTF-8"?>\n` +
    `<!-- Static sitemap for the Gisti landing (worker: gisti-landing, apex gisti-ai.com).\n` +
    `     Regenerated by landing-src/checklists/generate.mjs as the Tier-1 gallery grows.\n` +
    `     Localized (hi) URLs carry reciprocal hreflang via xhtml:link.\n` +
    `     Segment into a sitemap index once >1k URLs. Ping IndexNow after deploy:\n` +
    `     node scripts/indexnow-ping.mjs -->\n` +
    `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"\n        xmlns:xhtml="http://www.w3.org/1999/xhtml">\n` +
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
  // Each surface renders en always, and hi only when its source declares a `hi`
  // block. Sitemap entries pair en↔hi via a shared `alt` map (reciprocal hreflang).
  const catKeys = Object.keys(CATEGORIES).filter((k) => cats[k]?.length);
  const details = [];   // en detail results (drives site freshness)
  const countByCat = {};
  const sitemapPages = [];
  let hiDetailCount = 0, hiHubCount = 0;

  // Static top pages (no locale twins today).
  sitemapPages.push({ url: `${BASE}/`, lastmod: DATASET_PUBLISHED, changefreq: "weekly", priority: "1.0" });
  sitemapPages.push({ url: `${BASE}/mcp/`, lastmod: DATASET_PUBLISHED, changefreq: "monthly", priority: "0.8" });

  for (const k of catKeys) {
    const children = cats[k];
    const catBase = CATEGORIES[k];
    for (const cl of children) {
      const enRes = renderDetail(cl, byKey, "en");
      details.push(enRes);
      const alt = {
        en: detailUrl(cl.category, cl.slug, "en"),
        hi: clHasLoc(cl, "hi") ? detailUrl(cl.category, cl.slug, "hi") : null,
      };
      sitemapPages.push({ url: alt.en, lastmod: enRes.lastmod, changefreq: "monthly", priority: "0.6", alt });
      if (clHasLoc(cl, "hi")) {
        const hiRes = renderDetail(cl, byKey, "hi");
        sitemapPages.push({ url: alt.hi, lastmod: hiRes.lastmod, changefreq: "monthly", priority: "0.6", alt });
        hiDetailCount++;
      }
    }
    const enHub = renderHub(k, children, "en");
    const hubAlt = { en: hubUrl(k, "en"), hi: catHasLoc(catBase, "hi") ? hubUrl(k, "hi") : null };
    sitemapPages.push({ url: hubAlt.en, lastmod: enHub.lastmod, changefreq: "weekly", priority: "0.7", alt: hubAlt });
    if (catHasLoc(catBase, "hi")) {
      const hiHub = renderHub(k, children, "hi");
      sitemapPages.push({ url: hubAlt.hi, lastmod: hiHub.lastmod, changefreq: "weekly", priority: "0.7", alt: hubAlt });
      hiHubCount++;
    }
    countByCat[k] = children.length;
  }

  // Gallery index freshness = newest checklist edit across the whole set.
  const siteModified = details.map((d) => d.lastmod).sort().pop() || DATASET_PUBLISHED;
  const hiCatKeys = catKeys.filter((k) => catHasLoc(CATEGORIES[k], "hi"));
  const hiIndexExists = hiCatKeys.length > 0;

  const enIndex = renderIndex(catKeys, countByCat, siteModified, "en", hiIndexExists);
  const indexAlt = { en: indexUrl("en"), hi: hiIndexExists ? indexUrl("hi") : null };
  sitemapPages.push({ url: indexAlt.en, lastmod: enIndex.lastmod, changefreq: "weekly", priority: "0.9", alt: indexAlt });
  if (hiIndexExists) {
    // hi index lists ONLY categories that have a hi twin (no thin/empty hi hubs).
    const hiIndex = renderIndex(hiCatKeys, countByCat, siteModified, "hi", hiIndexExists);
    sitemapPages.push({ url: indexAlt.hi, lastmod: hiIndex.lastmod, changefreq: "weekly", priority: "0.9", alt: indexAlt });
  }

  const urlCount = writeSitemap(sitemapPages);

  console.log(`Generated:`);
  console.log(`  ${details.length} en detail pages${hiDetailCount ? ` + ${hiDetailCount} hi` : ""}`);
  console.log(`  ${catKeys.length} en hub pages${hiHubCount ? ` + ${hiHubCount} hi` : ""} (${catKeys.join(", ")})`);
  console.log(`  1 en gallery index${hiIndexExists ? " + 1 hi" : ""}`);
  console.log(`  sitemap.xml with ${urlCount} URLs`);
  const total = details.length + hiDetailCount + catKeys.length + hiHubCount + 1 + (hiIndexExists ? 1 : 0);
  console.log(`Total: ${total} pages under landing/`);
}

main();
