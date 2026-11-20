/**
 * Every user-facing text of the bot, EN / RU / HI, in one module.
 *
 * The language comes from the Telegram user's `language_code` (fallback EN). This file has NO
 * imports and uses only erasable TypeScript, so `scripts/setup.mjs` can import it directly with
 * Node's built-in type stripping — the command menu and bot descriptions live here too, not in a
 * second copy inside the script.
 */

export type Lang = "en" | "ru" | "hi";

export const LANGS: readonly Lang[] = ["en", "ru", "hi"];

/** `language_code` ("ru", "ru-RU", "hi", "pt-br", undefined) → a supported language, else EN. */
export function pickLang(code: string | undefined | null): Lang {
  const base = (code ?? "").trim().toLowerCase().split(/[-_]/)[0];
  if (base === "ru") return "ru";
  if (base === "hi") return "hi";
  return "en";
}

/** What a draft holds, for the one-line summary above the buttons. */
export interface DraftCounts {
  texts: number;
  photos: number;
  audios: number;
  files: number;
  links: number;
}

export interface Strings {
  notLinked: string;
  signInButton: string;
  orInApp: string;
  sendAgainAfterLink: string;
  linked: string;
  welcome: string;
  help: string;
  tokenExpired: string;
  noAccount: string;
  genericError: string;

  draftSummary: (c: DraftCounts) => string;
  draftQuestion: string;
  draftFreeHint: (items: number) => string;
  onlyFirstFile: string;
  tooManyParts: (max: number) => string;
  btnFree: string;
  btnAi: (cost: number) => string;
  btnCancel: string;
  creating: string;
  created: (name: string, items: number) => string;
  creditsLeft: (credits: number) => string;
  truncatedLines: (max: number) => string;
  seeLists: string;

  outOfCredits: string;
  dailyLimit: string;
  aiDisabled: string;
  aiFailed: string;
  tooLongForAi: string;
  fileTooBig: (maxMb: number) => string;
  unsupported: string;
  pdfUnsupported: string;
  imageAsPhoto: string;
  draftExpired: string;
  cancelled: string;

  listsHeader: (page: number, pages: number) => string;
  noLists: string;
  itemsHeader: (name: string, done: number, total: number, page: number, pages: number) => string;
  noItems: (name: string) => string;
  btnBack: string;
  viewOutdated: string;
  checklistGone: string;

  credits: (balance: number, premium: boolean, cost: number) => string;
  unlinked: string;
  notConnected: string;
  defaultChecklistName: string;

  // Phase 3 — Stars subscription, mobile app
  premiumOffer: (priceStars: number, dailyCap: number) => string;
  premiumStore: string;
  premiumTelegram: (until: string, state: "active" | "canceled" | "failed" | null) => string;
  btnSubscribe: (priceStars: number) => string;
  invoiceTitle: string;
  invoiceDescription: string;
  invoiceLabel: string;
  paymentConfirmed: (until: string) => string;
  paymentRenewed: (until: string) => string;
  paymentPending: string;
  /** apply_payment answered `status: "unassigned"`: paid, but this Telegram account has no Gisti link. */
  paymentUnlinked: string;
  preCheckoutNotLinked: string;
  preCheckoutPriceChanged: string;
  preCheckoutInvalid: string;
  preCheckoutUnavailable: string;
  paySupport: (email: string, termsUrl: string) => string;
  appText: string;
  btnOpenPlay: string;

  // Phase 2 — AI chat, time zone
  btnAskAi: (cost: number) => string;
  chatIntro: (cost: number) => string;
  btnEndChat: string;
  chatEnded: string;
  /** A tap on a chat button after the chat closed (End chat, or 30 min idle). */
  chatExpired: string;
  /** A tap on a chat button of an older turn (a card or options the chat has moved past). */
  chatOutdated: string;
  confirmTitle: string;
  btnConfirmYes: string;
  confirmApproved: string;
  confirmDeclined: string;
  /** A pending confirmation card declined because the user wrote a new message instead. */
  confirmSuperseded: string;
  chatNoCredits: string;
  chatTooLong: string;
  chatRejected: string;
  chatFailed: string;
  chatRoundLimit: string;
  tzUnknownHint: string;
  timezoneQuestion: (current: string | null) => string;
  timezoneSaved: (label: string, time: string) => string;
  /** One line per agent action on the confirmation card. */
  act: ActionTexts;
}

/**
 * Confirmation-card lines. A line for a call that will write names the RESOLVED row and list; a call
 * that will change nothing shows the model's arguments (`list` null = none named) + one of the suffixes.
 */
export interface ActionTexts {
  add: (text: string, list: string | null) => string;
  addMany: (n: number, list: string | null) => string;
  create: (name: string, items: number) => string;
  complete: (text: string, list: string | null) => string;
  delete: (text: string, list: string | null) => string;
  /** `count` null = unknown (the model's arguments only). */
  clear: (list: string | null, count: number | null) => string;
  willNotFind: string;
  willAsk: (names: string) => string;
  noChange: string;
  blocked: string;
  cannot: string;
  reminder: (text: string, when: string) => string;
  rename: (from: string, to: string) => string;
  move: (text: string, from: string | null, to: string) => string;
  find: (query: string) => string;
  read: (name: string) => string;
}

const DATE_LOCALE: Record<Lang, string> = { en: "en-GB", ru: "ru-RU", hi: "hi-IN" };

/** A calendar date in the user's language ("19 December 2026"), UTC. */
export function formatDate(ms: number, lang: Lang): string {
  try {
    return new Intl.DateTimeFormat(DATE_LOCALE[lang], {
      day: "numeric",
      month: "long",
      year: "numeric",
      timeZone: "UTC",
    }).format(new Date(ms));
  } catch {
    return new Date(ms).toISOString().slice(0, 10);
  }
}

// ── plural helpers ──────────────────────────────────────────────────────────────

function en(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}

/** Russian plural: 1 пункт · 2 пункта · 5 пунктов · 11 пунктов · 21 пункт. */
function ru(n: number, one: string, few: string, many: string): string {
  const m10 = n % 10;
  const m100 = n % 100;
  let w = many;
  if (m10 === 1 && m100 !== 11) w = one;
  else if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) w = few;
  return `${n} ${w}`;
}

function joinParts(parts: string[]): string {
  return parts.filter((p) => p.length > 0).join(", ");
}

// ── EN ──────────────────────────────────────────────────────────────────────────

const EN: Strings = {
  notLinked:
    "Connect your Google account to use Gisti here — the same account as in the Gisti app. " +
    "Your checklists and AI credits are shared between the app and this bot.",
  signInButton: "Sign in with Google",
  orInApp: "Or in the app: menu → Telegram.",
  sendAgainAfterLink: "Once connected, send your message again.",
  linked: "Done, your account is connected ✅",
  welcome: "Hi! I'm Gisti — I turn anything into a checklist.",
  help:
    "Send me anything — a list, notes, a photo, a voice message or a link — and I'll turn it into a checklist.\n\n" +
    "/lists — your checklists\n/chat — chat with the AI assistant\n/credits — AI credit balance\n" +
    "/premium — subscription\n/app — mobile app\n/timezone — your time zone\n" +
    "/paysupport — payment help\n/unlink — disconnect Telegram\n/help — this help",
  tokenExpired: "This link has expired or was already used. Create a new one in the app: menu → Telegram.",
  noAccount: "Sign in to the Gisti app with Google first, then create a new link: menu → Telegram.",
  genericError: "Something went wrong. Please try again.",

  draftSummary: (c) =>
    "Got it: " +
    joinParts([
      c.texts ? en(c.texts, "message", "messages") : "",
      c.photos ? en(c.photos, "photo", "photos") : "",
      c.audios ? en(c.audios, "voice/audio", "voice/audio") : "",
      c.files ? en(c.files, "file", "files") : "",
      c.links ? en(c.links, "link", "links") : "",
    ]) +
    ".",
  draftQuestion: "What should I create?",
  draftFreeHint: (n) => `From lines: ${en(n, "item", "items")}, free, no AI.`,
  onlyFirstFile: "AI reads only the first file.",
  tooManyParts: (max) => `Only the first ${max} messages are included.`,
  btnFree: "📝 Checklist from lines",
  btnAi: (cost) => `✨ Checklist with AI (${en(cost, "credit", "credits")})`,
  btnCancel: "✖ Cancel",
  creating: "⏳ Creating a checklist with AI…",
  created: (name, n) => `✅ Created «${name}» — ${en(n, "item", "items")}.`,
  creditsLeft: (x) => `AI credits left: ${x}`,
  truncatedLines: (max) => `Only the first ${max} lines were added.`,
  seeLists: "It's already in the Gisti app. Browse your checklists: /lists",

  outOfCredits: "You're out of AI credits — the balance is shared with the Gisti app.",
  dailyLimit: "You've reached today's AI limit. Try again tomorrow.",
  aiDisabled: "AI is temporarily unavailable. Please try again later.",
  aiFailed: "AI couldn't process this. Please try again.",
  tooLongForAi: "This is too long for AI — try a shorter text.",
  fileTooBig: (mb) => `This file is larger than ${mb} MB — too big for me to read. Please send a smaller one.`,
  unsupported:
    "I can't read this kind of message yet. Send text, a photo, a voice message, an audio file, a .txt file or a link.",
  pdfUnsupported: "PDFs aren't supported yet. Send a photo of the page or paste the text.",
  imageAsPhoto: "Send the image as a photo, not as a file — then I can read it.",
  draftExpired: "This request has expired. Please send the content again.",
  cancelled: "Cancelled.",

  listsHeader: (page, pages) => `Your checklists (${page}/${pages}):`,
  noLists: "You don't have any checklists yet. Send me a list, a photo or a voice message to create one.",
  itemsHeader: (name, done, total, page, pages) => `${name}\n${done}/${total} done · page ${page}/${pages}`,
  noItems: (name) => `«${name}» has no items yet.`,
  btnBack: "⬅ All checklists",
  viewOutdated: "This view is outdated — open /lists again.",
  checklistGone: "This checklist no longer exists — open /lists again.",

  credits: (balance, premium, cost) =>
    `AI credits: ${balance}\nPremium: ${premium ? "yes" : "no"}\n\n` +
    `A checklist with AI costs ${en(cost, "credit", "credits")}; a checklist from lines is free. ` +
    "Credits are shared with the Gisti app.",
  unlinked: "Telegram is disconnected from your Gisti account. Send /start to connect again.",
  notConnected: "This Telegram account isn't connected to Gisti.",
  defaultChecklistName: "Checklist from Telegram",

  premiumOffer: (price, cap) =>
    `⭐ Gisti Premium — ${price} ⭐ for 30 days\n\n` +
    "One subscription for every platform: it works here in Telegram, in the Gisti Android app and on the web.\n\n" +
    "What you get:\n" +
    "• Unlimited checklists and fills\n" +
    `• AI credits refilled every day, up to ${cap}\n` +
    "• Unlimited reminders, weekly checklists and attachments\n" +
    "• Calendar\n" +
    "• Priority support\n\n" +
    "Renews every 30 days; cancel anytime in Telegram. Payment help and terms: /paysupport",
  premiumStore:
    "⭐ Premium is already active on your account through the app subscription — it works here in Telegram too. " +
    "There's nothing else to buy.",
  premiumTelegram: (until, state) =>
    `⭐ Premium is active until ${until}. It works in Telegram, the Android app and on the web.` +
    (state === "active"
      ? "\nThe subscription renews automatically."
      : state === "canceled"
        ? "\nAuto-renewal is off — it won't renew after that date."
        : state === "failed"
          ? "\nThe last renewal payment failed — check your Stars balance."
          : ""),
  btnSubscribe: (price) => `Subscribe for ${price} ⭐`,
  invoiceTitle: "Gisti Premium · 30 days",
  invoiceDescription:
    "Unlimited checklists, daily AI credit refill and more. One subscription for Telegram, the Android app and the web.",
  invoiceLabel: "Premium, 30 days",
  paymentConfirmed: (until) =>
    `✅ Payment received — Premium is active until ${until}. It works in Telegram, the Android app and on the web.`,
  paymentRenewed: (until) => `✅ Subscription renewed — Premium is active until ${until}.`,
  paymentPending:
    "✅ Payment received. Premium will appear on your account shortly — if it doesn't, write to /paysupport.",
  paymentUnlinked:
    "✅ Payment received, but this Telegram account isn't connected to a Gisti account, so Premium hasn't been added yet. " +
    "Connect it — send /start — and Premium will be added automatically. Need a refund? Write to /paysupport.",
  preCheckoutNotLinked: "Connect your Google account first: send /start to the bot.",
  preCheckoutPriceChanged: "The price has changed. Open /premium again.",
  preCheckoutInvalid: "This invoice is no longer valid. Open /premium again.",
  preCheckoutUnavailable: "Payment is temporarily unavailable. Please try again in a minute.",
  paySupport: (email, terms) =>
    `Help with payments and refunds: write to ${email}. Please include your Telegram username and the payment date.\n\n` +
    "You can cancel the subscription at any time in Telegram; Premium stays active until the end of the paid period.\n\n" +
    `Terms of use: ${terms}`,
  appText:
    "📱 Gisti for Android — your checklists, reminders and home-screen widgets on your phone. " +
    "Sign in with the same Google account and everything syncs with this bot.",
  btnOpenPlay: "Open in Google Play",

  btnAskAi: (cost) => `💬 Ask AI (${en(cost, "credit", "credits")})`,
  chatIntro: (cost) =>
    "💬 AI chat is on. Ask anything about your checklists — I can add, tick off, find, move and delete items " +
    "and set reminders.\n\n" +
    `Each message costs ${en(cost, "credit", "credits")} (shared with the Gisti app). Photos, voice messages and ` +
    "links still become checklists. Tap «End chat» when you're done.",
  btnEndChat: "✖ End chat",
  chatEnded: "Chat ended. Your messages turn into checklists again. Send /chat to talk to the AI.",
  chatExpired: "This chat has ended — send /chat to start a new one.",
  chatOutdated: "This button is outdated — the chat has moved on.",
  confirmTitle: "Do this?",
  btnConfirmYes: "✅ Yes",
  confirmApproved: "✅ Confirmed.",
  confirmDeclined: "Cancelled.",
  confirmSuperseded: "Cancelled — you sent a new message.",
  chatNoCredits:
    "You're out of AI credits — the balance is shared with the Gisti app. Premium refills credits every day: /premium\n\n" +
    "The chat is closed: your messages turn into checklists again (a checklist from lines is free).",
  chatTooLong: "This message is too long for the AI chat — please send a shorter one.",
  chatRejected: "The AI couldn't take this message. Please rephrase it or make it shorter.",
  chatFailed: "The AI didn't respond. Please send your message again.",
  chatRoundLimit: "I did several steps but reached the limit for one message. Tell me what else to do.",
  tzUnknownHint: "To set reminders at your local time, tell me your time zone: /timezone",
  timezoneQuestion: (current) =>
    "What time is it for you now? Tap your current time — reminders from the AI chat will use this time zone." +
    (current ? `\n\nCurrent: ${current}` : ""),
  timezoneSaved: (label, time) => `✅ Time zone saved: ${label} (it's ${time} for you now).`,
  act: {
    add: (t, l) => `Add «${t}»${l ? ` to «${l}»` : ""}`,
    addMany: (n, l) => `Add ${en(n, "item", "items")}${l ? ` to «${l}»` : ""}`,
    create: (name, n) => `Create checklist «${name}»${n ? ` with ${en(n, "item", "items")}` : ""}`,
    complete: (t, l) => `Mark «${t}» as done${l ? ` in «${l}»` : ""}`,
    delete: (t, l) => `🗑 Delete «${t}»${l ? ` from «${l}»` : ""}`,
    clear: (l, n) =>
      `🗑 Remove ${n === null ? "all completed items" : en(n, "completed item", "completed items")}${l ? ` from «${l}»` : ""}`,
    willNotFind: "not found — nothing will change",
    willAsk: (names) => `several lists match (${names}) — nothing will change`,
    noChange: "already done — nothing will change",
    blocked: "it has a reminder, repeat or attachment — it stays where it is",
    cannot: "can't be done — nothing will change",
    reminder: (t, w) => `Remind about «${t}» at ${w}`,
    rename: (f, to) => `Rename «${f}» to «${to}»`,
    move: (t, f, to) => `↪ Move «${t}»${f ? ` from «${f}»` : ""} to «${to}»`,
    find: (q) => `Search for «${q}»`,
    read: (n) => `Read «${n}»`,
  },
};

// ── RU ──────────────────────────────────────────────────────────────────────────

const RU: Strings = {
  notLinked:
    "Чтобы пользоваться Gisti здесь, подключите Google-аккаунт — тот же, что в приложении Gisti. " +
    "Чек-листы и AI-кредиты у приложения и бота общие.",
  signInButton: "Войти через Google",
  orInApp: "Или в приложении: меню → Telegram.",
  sendAgainAfterLink: "После подключения отправьте сообщение ещё раз.",
  linked: "Готово, аккаунт подключён ✅",
  welcome: "Привет! Я Gisti — превращаю что угодно в чек-лист.",
  help:
    "Пришлите мне что угодно — список, заметки, фото, голосовое или ссылку — и я сделаю из этого чек-лист.\n\n" +
    "/lists — ваши чек-листы\n/chat — чат с AI-ассистентом\n/credits — баланс AI-кредитов\n" +
    "/premium — подписка\n/app — мобильное приложение\n/timezone — ваш часовой пояс\n" +
    "/paysupport — помощь с оплатой\n/unlink — отключить Telegram\n/help — эта справка",
  tokenExpired: "Ссылка устарела или уже использована. Создайте новую в приложении: меню → Telegram.",
  noAccount: "Сначала войдите в приложение Gisti через Google, затем создайте новую ссылку: меню → Telegram.",
  genericError: "Что-то пошло не так, попробуйте ещё раз.",

  draftSummary: (c) =>
    "Принято: " +
    joinParts([
      c.texts ? ru(c.texts, "сообщение", "сообщения", "сообщений") : "",
      c.photos ? `${c.photos} фото` : "",
      c.audios ? `${c.audios} аудио` : "",
      c.files ? ru(c.files, "файл", "файла", "файлов") : "",
      c.links ? ru(c.links, "ссылка", "ссылки", "ссылок") : "",
    ]) +
    ".",
  draftQuestion: "Что создать?",
  draftFreeHint: (n) => `Из пунктов: ${ru(n, "пункт", "пункта", "пунктов")}, бесплатно, без AI.`,
  onlyFirstFile: "AI прочитает только первый файл.",
  tooManyParts: (max) => `Взяты только первые ${max} сообщений.`,
  btnFree: "📝 Чек-лист из пунктов",
  btnAi: (cost) => `✨ Чек-лист с AI (${ru(cost, "кредит", "кредита", "кредитов")})`,
  btnCancel: "✖ Отмена",
  creating: "⏳ Создаю чек-лист с AI…",
  created: (name, n) => `✅ Создан «${name}» — ${ru(n, "пункт", "пункта", "пунктов")}.`,
  creditsLeft: (x) => `Осталось AI-кредитов: ${x}`,
  truncatedLines: (max) => `Добавлены только первые ${max} строк.`,
  seeLists: "Он уже в приложении Gisti. Ваши чек-листы: /lists",

  outOfCredits: "AI-кредиты закончились — баланс общий с приложением Gisti.",
  dailyLimit: "Дневной лимит AI исчерпан. Попробуйте завтра.",
  aiDisabled: "AI временно недоступен. Попробуйте позже.",
  aiFailed: "AI не смог это обработать. Попробуйте ещё раз.",
  tooLongForAi: "Слишком длинно для AI — попробуйте текст покороче.",
  fileTooBig: (mb) => `Файл больше ${mb} МБ — такой я прочитать не могу. Пришлите файл поменьше.`,
  unsupported: "Такие сообщения я пока не понимаю. Пришлите текст, фото, голосовое, аудио, файл .txt или ссылку.",
  pdfUnsupported: "PDF пока не поддерживается. Пришлите фото страницы или вставьте текст.",
  imageAsPhoto: "Отправьте картинку как фото, а не файлом — тогда я её прочитаю.",
  draftExpired: "Этот запрос устарел. Отправьте содержимое ещё раз.",
  cancelled: "Отменено.",

  listsHeader: (page, pages) => `Ваши чек-листы (${page}/${pages}):`,
  noLists: "Чек-листов пока нет. Пришлите список, фото или голосовое — и я создам первый.",
  itemsHeader: (name, done, total, page, pages) => `${name}\nГотово ${done}/${total} · стр. ${page}/${pages}`,
  noItems: (name) => `В «${name}» пока нет пунктов.`,
  btnBack: "⬅ Все чек-листы",
  viewOutdated: "Этот список устарел — откройте /lists заново.",
  checklistGone: "Этого чек-листа больше нет — откройте /lists заново.",

  credits: (balance, premium, cost) =>
    `AI-кредиты: ${balance}\nPremium: ${premium ? "да" : "нет"}\n\n` +
    `Чек-лист с AI стоит ${ru(cost, "кредит", "кредита", "кредитов")}, чек-лист из пунктов — бесплатно. ` +
    "Кредиты общие с приложением Gisti.",
  unlinked: "Telegram отключён от аккаунта Gisti. Чтобы подключить снова, отправьте /start.",
  notConnected: "Этот Telegram-аккаунт не подключён к Gisti.",
  defaultChecklistName: "Чек-лист из Telegram",

  premiumOffer: (price, cap) =>
    `⭐ Gisti Premium — ${price} ⭐ за 30 дней\n\n` +
    "Одна подписка на все платформы: она работает здесь, в Telegram, в Android-приложении Gisti и в веб-версии.\n\n" +
    "Что даёт Premium:\n" +
    "• Безлимитные чек-листы и заполнения\n" +
    `• Ежедневное пополнение AI-кредитов — до ${cap}\n` +
    "• Безлимитные напоминания, недельные чек-листы и вложения\n" +
    "• Календарь\n" +
    "• Приоритетная поддержка\n\n" +
    "Продлевается каждые 30 дней, отменить можно в любой момент в Telegram. Помощь с оплатой и условия: /paysupport",
  premiumStore:
    "⭐ Premium уже активен по подписке из приложения — он работает и здесь, в Telegram. Ничего докупать не нужно.",
  premiumTelegram: (until, state) =>
    `⭐ Premium активен до ${until}. Он работает в Telegram, Android-приложении и веб-версии.` +
    (state === "active"
      ? "\nПодписка продлится автоматически."
      : state === "canceled"
        ? "\nАвтопродление выключено — после этой даты подписка не продлится."
        : state === "failed"
          ? "\nПоследнее списание за продление не прошло — проверьте баланс звёзд."
          : ""),
  btnSubscribe: (price) => `Подписаться за ${price} ⭐`,
  invoiceTitle: "Gisti Premium · 30 дней",
  invoiceDescription:
    "Безлимитные чек-листы, ежедневное пополнение AI-кредитов и не только. Одна подписка для Telegram, Android-приложения и веба.",
  invoiceLabel: "Premium, 30 дней",
  paymentConfirmed: (until) =>
    `✅ Оплата прошла — Premium активен до ${until}. Он работает в Telegram, Android-приложении и веб-версии.`,
  paymentRenewed: (until) => `✅ Подписка продлена — Premium активен до ${until}.`,
  paymentPending:
    "✅ Оплата получена. Premium появится на аккаунте в ближайшее время — если этого не произошло, напишите в /paysupport.",
  paymentUnlinked:
    "✅ Оплата получена, но этот Telegram-аккаунт не подключён к аккаунту Gisti, поэтому Premium пока не начислен. " +
    "Подключите его командой /start — и Premium добавится автоматически. Если нужен возврат, напишите в /paysupport.",
  preCheckoutNotLinked: "Сначала подключите Google-аккаунт: отправьте боту /start.",
  preCheckoutPriceChanged: "Цена изменилась. Откройте /premium заново.",
  preCheckoutInvalid: "Этот счёт больше не действует. Откройте /premium заново.",
  preCheckoutUnavailable: "Оплата временно недоступна. Попробуйте через минуту.",
  paySupport: (email, terms) =>
    `Помощь с оплатой и возвратами: напишите на ${email}, укажите ваш username в Telegram и дату оплаты.\n\n` +
    "Отменить подписку можно в любой момент в Telegram — Premium сохранится до конца оплаченного периода.\n\n" +
    `Условия использования: ${terms}`,
  appText:
    "📱 Gisti для Android — чек-листы, напоминания и виджеты на главном экране телефона. " +
    "Войдите тем же Google-аккаунтом — всё синхронизируется с этим ботом.",
  btnOpenPlay: "Открыть в Google Play",

  btnAskAi: (cost) => `💬 Спросить AI (${ru(cost, "кредит", "кредита", "кредитов")})`,
  chatIntro: (cost) =>
    "💬 AI-чат включён. Спрашивайте что угодно о ваших чек-листах — я умею добавлять, отмечать, искать, " +
    "переносить и удалять пункты и ставить напоминания.\n\n" +
    `Каждое сообщение стоит ${ru(cost, "кредит", "кредита", "кредитов")} (баланс общий с приложением Gisti). ` +
    "Фото, голосовые и ссылки по-прежнему превращаются в чек-листы. Когда закончите, нажмите «Завершить чат».",
  btnEndChat: "✖ Завершить чат",
  chatEnded: "Чат завершён. Сообщения снова превращаются в чек-листы. Чтобы поговорить с AI, отправьте /chat.",
  chatExpired: "Этот чат уже завершён — отправьте /chat, чтобы начать новый.",
  chatOutdated: "Эта кнопка устарела — разговор уже ушёл дальше.",
  confirmTitle: "Выполнить?",
  btnConfirmYes: "✅ Да",
  confirmApproved: "✅ Подтверждено.",
  confirmDeclined: "Отменено.",
  confirmSuperseded: "Отменено — вы отправили новое сообщение.",
  chatNoCredits:
    "AI-кредиты закончились — баланс общий с приложением Gisti. С Premium кредиты пополняются каждый день: /premium\n\n" +
    "Чат закрыт: сообщения снова превращаются в чек-листы (чек-лист из пунктов — бесплатно).",
  chatTooLong: "Сообщение слишком длинное для AI-чата — отправьте покороче.",
  chatRejected: "AI не смог принять это сообщение. Переформулируйте или сократите его.",
  chatFailed: "AI не ответил. Отправьте сообщение ещё раз.",
  chatRoundLimit: "Я выполнил несколько шагов, но достиг лимита на одно сообщение. Напишите, что ещё сделать.",
  tzUnknownHint: "Чтобы ставить напоминания по вашему времени, укажите часовой пояс: /timezone",
  timezoneQuestion: (current) =>
    "Сколько у вас сейчас времени? Нажмите на ваше текущее время — напоминания из AI-чата будут ставиться по этому поясу." +
    (current ? `\n\nСейчас выбран: ${current}` : ""),
  timezoneSaved: (label, time) => `✅ Часовой пояс сохранён: ${label} (у вас сейчас ${time}).`,
  act: {
    add: (t, l) => `Добавить «${t}»${l ? ` в «${l}»` : ""}`,
    addMany: (n, l) => `Добавить ${ru(n, "пункт", "пункта", "пунктов")}${l ? ` в «${l}»` : ""}`,
    create: (name, n) => `Создать чек-лист «${name}»${n ? ` (${ru(n, "пункт", "пункта", "пунктов")})` : ""}`,
    complete: (t, l) => `Отметить «${t}» выполненным${l ? ` в «${l}»` : ""}`,
    delete: (t, l) => `🗑 Удалить «${t}»${l ? ` из «${l}»` : ""}`,
    clear: (l, n) =>
      `🗑 Удалить ${n === null ? "все выполненные пункты" : `выполненные: ${ru(n, "пункт", "пункта", "пунктов")}`}${l ? ` из «${l}»` : ""}`,
    willNotFind: "не найдено — ничего не изменится",
    willAsk: (names) => `подходит несколько списков (${names}) — ничего не изменится`,
    noChange: "уже сделано — ничего не изменится",
    blocked: "у пункта есть напоминание, повтор или вложение — он останется на месте",
    cannot: "так нельзя — ничего не изменится",
    reminder: (t, w) => `Напомнить о «${t}» — ${w}`,
    rename: (f, to) => `Переименовать «${f}» в «${to}»`,
    move: (t, f, to) => `↪ Перенести «${t}»${f ? ` из «${f}»` : ""} в «${to}»`,
    find: (q) => `Найти «${q}»`,
    read: (n) => `Прочитать «${n}»`,
  },
};

// ── HI ──────────────────────────────────────────────────────────────────────────

const HI: Strings = {
  notLinked:
    "यहाँ Gisti इस्तेमाल करने के लिए अपना Google अकाउंट जोड़ें — वही जो Gisti ऐप में है। " +
    "आपकी चेकलिस्ट और AI क्रेडिट ऐप और इस बॉट में एक जैसे रहेंगे।",
  signInButton: "Google से साइन इन करें",
  orInApp: "या ऐप में: मेनू → Telegram।",
  sendAgainAfterLink: "जुड़ने के बाद अपना मैसेज फिर से भेजें।",
  linked: "हो गया, आपका अकाउंट जुड़ गया ✅",
  welcome: "नमस्ते! मैं Gisti हूँ — किसी भी चीज़ को चेकलिस्ट में बदल देता हूँ।",
  help:
    "मुझे कुछ भी भेजें — लिस्ट, नोट्स, फ़ोटो, वॉइस मैसेज या लिंक — मैं उसे चेकलिस्ट बना दूँगा।\n\n" +
    "/lists — आपकी चेकलिस्ट\n/chat — AI असिस्टेंट से चैट\n/credits — AI क्रेडिट बैलेंस\n" +
    "/premium — सब्सक्रिप्शन\n/app — मोबाइल ऐप\n/timezone — आपका टाइम ज़ोन\n" +
    "/paysupport — भुगतान सहायता\n/unlink — Telegram को अलग करें\n/help — यह मदद",
  tokenExpired: "यह लिंक पुराना हो गया है या पहले ही इस्तेमाल हो चुका है। ऐप में नया बनाएँ: मेनू → Telegram।",
  noAccount: "पहले Gisti ऐप में Google से साइन इन करें, फिर नया लिंक बनाएँ: मेनू → Telegram।",
  genericError: "कुछ गड़बड़ हो गई। कृपया फिर से कोशिश करें।",

  draftSummary: (c) =>
    "मिल गया: " +
    joinParts([
      c.texts ? `${c.texts} मैसेज` : "",
      c.photos ? `${c.photos} फ़ोटो` : "",
      c.audios ? `${c.audios} ऑडियो` : "",
      c.files ? `${c.files} फ़ाइल` : "",
      c.links ? `${c.links} लिंक` : "",
    ]) +
    "।",
  draftQuestion: "क्या बनाऊँ?",
  draftFreeHint: (n) => `लाइनों से: ${n} आइटम, मुफ़्त, बिना AI के।`,
  onlyFirstFile: "AI सिर्फ़ पहली फ़ाइल पढ़ेगा।",
  tooManyParts: (max) => `सिर्फ़ पहले ${max} मैसेज लिए गए हैं।`,
  btnFree: "📝 लाइनों से चेकलिस्ट",
  btnAi: (cost) => `✨ AI से चेकलिस्ट (${cost} क्रेडिट)`,
  btnCancel: "✖ रद्द करें",
  creating: "⏳ AI से चेकलिस्ट बना रहा हूँ…",
  created: (name, n) => `✅ «${name}» बन गई — ${n} आइटम।`,
  creditsLeft: (x) => `बचे हुए AI क्रेडिट: ${x}`,
  truncatedLines: (max) => `सिर्फ़ पहली ${max} लाइनें जोड़ी गईं।`,
  seeLists: "यह Gisti ऐप में भी दिखेगी। अपनी चेकलिस्ट देखें: /lists",

  outOfCredits: "AI क्रेडिट खत्म हो गए — बैलेंस Gisti ऐप के साथ साझा है।",
  dailyLimit: "आज की AI सीमा पूरी हो गई। कल फिर कोशिश करें।",
  aiDisabled: "AI अभी उपलब्ध नहीं है। थोड़ी देर बाद कोशिश करें।",
  aiFailed: "AI इसे प्रोसेस नहीं कर पाया। कृपया फिर से कोशिश करें।",
  tooLongForAi: "यह AI के लिए बहुत लंबा है — छोटा टेक्स्ट भेजकर देखें।",
  fileTooBig: (mb) => `यह फ़ाइल ${mb} MB से बड़ी है, इसलिए मैं इसे पढ़ नहीं सकता। कृपया छोटी फ़ाइल भेजें।`,
  unsupported: "ऐसे मैसेज मैं अभी नहीं पढ़ पाता। टेक्स्ट, फ़ोटो, वॉइस मैसेज, ऑडियो, .txt फ़ाइल या लिंक भेजें।",
  pdfUnsupported: "PDF अभी सपोर्ट नहीं है। पेज की फ़ोटो भेजें या टेक्स्ट पेस्ट करें।",
  imageAsPhoto: "तस्वीर को फ़ाइल नहीं, फ़ोटो की तरह भेजें — तब मैं उसे पढ़ पाऊँगा।",
  draftExpired: "यह अनुरोध पुराना हो गया है। कृपया कंटेंट फिर से भेजें।",
  cancelled: "रद्द कर दिया।",

  listsHeader: (page, pages) => `आपकी चेकलिस्ट (${page}/${pages}):`,
  noLists: "अभी कोई चेकलिस्ट नहीं है। पहली बनाने के लिए मुझे कोई लिस्ट, फ़ोटो या वॉइस मैसेज भेजें।",
  itemsHeader: (name, done, total, page, pages) => `${name}\n${done}/${total} पूरे · पेज ${page}/${pages}`,
  noItems: (name) => `«${name}» में अभी कोई आइटम नहीं है।`,
  btnBack: "⬅ सभी चेकलिस्ट",
  viewOutdated: "यह व्यू पुराना हो गया है — /lists फिर से खोलें।",
  checklistGone: "यह चेकलिस्ट अब मौजूद नहीं है — /lists फिर से खोलें।",

  credits: (balance, premium, cost) =>
    `AI क्रेडिट: ${balance}\nPremium: ${premium ? "हाँ" : "नहीं"}\n\n` +
    `AI से चेकलिस्ट बनाने में ${cost} क्रेडिट लगते हैं, लाइनों से चेकलिस्ट मुफ़्त है। ` +
    "क्रेडिट Gisti ऐप के साथ साझा हैं।",
  unlinked: "Telegram आपके Gisti अकाउंट से अलग कर दिया गया। फिर से जोड़ने के लिए /start भेजें।",
  notConnected: "यह Telegram अकाउंट Gisti से जुड़ा नहीं है।",
  defaultChecklistName: "Telegram से चेकलिस्ट",

  premiumOffer: (price, cap) =>
    `⭐ Gisti Premium — 30 दिनों के लिए ${price} ⭐\n\n` +
    "एक सब्सक्रिप्शन, हर प्लेटफ़ॉर्म पर: यह यहाँ Telegram में, Gisti के Android ऐप में और वेब पर काम करता है।\n\n" +
    "आपको मिलेगा:\n" +
    "• अनलिमिटेड चेकलिस्ट और फ़िल\n" +
    `• हर दिन AI क्रेडिट रिफ़िल — ${cap} तक\n` +
    "• अनलिमिटेड रिमाइंडर, साप्ताहिक चेकलिस्ट और अटैचमेंट\n" +
    "• कैलेंडर\n" +
    "• प्राथमिकता वाला सपोर्ट\n\n" +
    "हर 30 दिन में अपने-आप रिन्यू होता है; Telegram में कभी भी रद्द कर सकते हैं। भुगतान सहायता और शर्तें: /paysupport",
  premiumStore:
    "⭐ ऐप सब्सक्रिप्शन से आपके अकाउंट पर Premium पहले से चालू है — यह यहाँ Telegram में भी काम करता है। " +
    "और कुछ खरीदने की ज़रूरत नहीं।",
  premiumTelegram: (until, state) =>
    `⭐ Premium ${until} तक चालू है। यह Telegram, Android ऐप और वेब पर काम करता है।` +
    (state === "active"
      ? "\nसब्सक्रिप्शन अपने-आप रिन्यू होगा।"
      : state === "canceled"
        ? "\nऑटो-रिन्यू बंद है — इस तारीख के बाद रिन्यू नहीं होगा।"
        : state === "failed"
          ? "\nपिछला रिन्यूअल भुगतान नहीं हो पाया — अपना Stars बैलेंस देखें।"
          : ""),
  btnSubscribe: (price) => `${price} ⭐ में सब्सक्राइब करें`,
  invoiceTitle: "Gisti Premium · 30 दिन",
  invoiceDescription:
    "अनलिमिटेड चेकलिस्ट, रोज़ AI क्रेडिट रिफ़िल और भी बहुत कुछ। Telegram, Android ऐप और वेब के लिए एक सब्सक्रिप्शन।",
  invoiceLabel: "Premium, 30 दिन",
  paymentConfirmed: (until) =>
    `✅ भुगतान हो गया — Premium ${until} तक चालू है। यह Telegram, Android ऐप और वेब पर काम करता है।`,
  paymentRenewed: (until) => `✅ सब्सक्रिप्शन रिन्यू हो गया — Premium ${until} तक चालू है।`,
  paymentPending: "✅ भुगतान मिल गया। Premium जल्द ही आपके अकाउंट पर दिखेगा — अगर न दिखे, तो /paysupport पर लिखें।",
  paymentUnlinked:
    "✅ भुगतान मिल गया, लेकिन यह Telegram अकाउंट किसी Gisti अकाउंट से जुड़ा नहीं है, इसलिए Premium अभी नहीं जोड़ा गया। " +
    "इसे जोड़ें — /start भेजें — और Premium अपने-आप जुड़ जाएगा। रिफ़ंड के लिए /paysupport पर लिखें।",
  preCheckoutNotLinked: "पहले अपना Google अकाउंट जोड़ें: बॉट को /start भेजें।",
  preCheckoutPriceChanged: "कीमत बदल गई है। /premium फिर से खोलें।",
  preCheckoutInvalid: "यह इनवॉइस अब मान्य नहीं है। /premium फिर से खोलें।",
  preCheckoutUnavailable: "भुगतान अभी उपलब्ध नहीं है। एक मिनट बाद फिर कोशिश करें।",
  paySupport: (email, terms) =>
    `भुगतान और रिफ़ंड में मदद के लिए ${email} पर लिखें। अपना Telegram username और भुगतान की तारीख ज़रूर बताएँ।\n\n` +
    "आप Telegram में कभी भी सब्सक्रिप्शन रद्द कर सकते हैं; Premium भुगतान की गई अवधि के अंत तक चालू रहेगा।\n\n" +
    `उपयोग की शर्तें: ${terms}`,
  appText:
    "📱 Android के लिए Gisti — आपकी चेकलिस्ट, रिमाइंडर और होम-स्क्रीन विजेट आपके फ़ोन पर। " +
    "उसी Google अकाउंट से साइन इन करें — सब कुछ इस बॉट के साथ सिंक रहेगा।",
  btnOpenPlay: "Google Play में खोलें",

  btnAskAi: (cost) => `💬 AI से पूछें (${cost} क्रेडिट)`,
  chatIntro: (cost) =>
    "💬 AI चैट चालू है। अपनी चेकलिस्ट के बारे में कुछ भी पूछें — मैं आइटम जोड़, पूरे मार्क कर, ढूँढ, " +
    "दूसरी लिस्ट में ले जा और हटा सकता हूँ, और रिमाइंडर लगा सकता हूँ।\n\n" +
    `हर मैसेज पर ${cost} क्रेडिट लगते हैं (Gisti ऐप के साथ साझा बैलेंस)। फ़ोटो, वॉइस मैसेज और लिंक पहले की तरह ` +
    "चेकलिस्ट बनेंगे। काम पूरा होने पर «चैट खत्म करें» दबाएँ।",
  btnEndChat: "✖ चैट खत्म करें",
  chatEnded: "चैट खत्म हो गई। अब आपके मैसेज फिर से चेकलिस्ट बनेंगे। AI से बात करने के लिए /chat भेजें।",
  chatExpired: "यह चैट खत्म हो चुकी है — नई शुरू करने के लिए /chat भेजें।",
  chatOutdated: "यह बटन पुराना हो गया है — बातचीत आगे बढ़ चुकी है।",
  confirmTitle: "क्या यह करूँ?",
  btnConfirmYes: "✅ हाँ",
  confirmApproved: "✅ पुष्टि हो गई।",
  confirmDeclined: "रद्द कर दिया।",
  confirmSuperseded: "रद्द कर दिया — आपने नया मैसेज भेजा।",
  chatNoCredits:
    "AI क्रेडिट खत्म हो गए — बैलेंस Gisti ऐप के साथ साझा है। Premium में क्रेडिट हर दिन भरते हैं: /premium\n\n" +
    "चैट बंद हो गई: आपके मैसेज फिर से चेकलिस्ट बनेंगे (लाइनों से चेकलिस्ट मुफ़्त है)।",
  chatTooLong: "यह मैसेज AI चैट के लिए बहुत लंबा है — कृपया छोटा मैसेज भेजें।",
  chatRejected: "AI यह मैसेज नहीं ले पाया। कृपया इसे दूसरे शब्दों में या छोटा करके भेजें।",
  chatFailed: "AI ने जवाब नहीं दिया। कृपया अपना मैसेज फिर से भेजें।",
  chatRoundLimit: "मैंने कई कदम पूरे किए, लेकिन एक मैसेज की सीमा तक पहुँच गया। बताइए और क्या करना है।",
  tzUnknownHint: "अपने स्थानीय समय पर रिमाइंडर के लिए अपना टाइम ज़ोन बताएँ: /timezone",
  timezoneQuestion: (current) =>
    "आपके यहाँ अभी कितने बजे हैं? अपना मौजूदा समय दबाएँ — AI चैट के रिमाइंडर इसी टाइम ज़ोन में लगेंगे।" +
    (current ? `\n\nअभी चुना हुआ: ${current}` : ""),
  timezoneSaved: (label, time) => `✅ टाइम ज़ोन सेव हो गया: ${label} (आपके यहाँ अभी ${time} बजे हैं)।`,
  act: {
    add: (t, l) => `«${t}» जोड़ें${l ? ` («${l}» में)` : ""}`,
    addMany: (n, l) => `${n} आइटम जोड़ें${l ? ` («${l}» में)` : ""}`,
    create: (name, n) => `चेकलिस्ट «${name}» बनाएँ${n ? ` (${n} आइटम)` : ""}`,
    complete: (t, l) => `«${t}» को पूरा मार्क करें${l ? ` («${l}» में)` : ""}`,
    delete: (t, l) => `🗑 «${t}» हटाएँ${l ? ` («${l}» से)` : ""}`,
    clear: (l, n) => `🗑 ${n === null ? "सभी पूरे आइटम" : `${n} पूरे आइटम`} हटाएँ${l ? ` («${l}» से)` : ""}`,
    willNotFind: "नहीं मिला — कुछ नहीं बदलेगा",
    willAsk: (names) => `कई लिस्ट मेल खाती हैं (${names}) — कुछ नहीं बदलेगा`,
    noChange: "पहले से हो चुका है — कुछ नहीं बदलेगा",
    blocked: "इस आइटम में रिमाइंडर, रिपीट या अटैचमेंट है — यह अपनी जगह रहेगा",
    cannot: "यह नहीं हो सकता — कुछ नहीं बदलेगा",
    reminder: (t, w) => `«${t}» के लिए रिमाइंडर — ${w}`,
    rename: (f, to) => `«${f}» का नाम बदलकर «${to}» करें`,
    move: (t, f, to) => `↪ «${t}» को${f ? ` «${f}» से` : ""} «${to}» में ले जाएँ`,
    find: (q) => `«${q}» खोजें`,
    read: (n) => `«${n}» पढ़ें`,
  },
};

const STRINGS: Record<Lang, Strings> = { en: EN, ru: RU, hi: HI };

export function t(lang: Lang): Strings {
  return STRINGS[lang];
}

// ── Bot profile (used by scripts/setup.mjs) ─────────────────────────────────────

export interface BotCommand {
  command: string;
  description: string;
}

/** The command menu per language (`setMyCommands`). EN doubles as the default (no language_code). */
export const BOT_COMMANDS: Record<Lang, BotCommand[]> = {
  en: [
    { command: "lists", description: "My checklists" },
    { command: "chat", description: "AI chat" },
    { command: "credits", description: "AI credit balance" },
    { command: "premium", description: "Subscription" },
    { command: "app", description: "Mobile app" },
    { command: "help", description: "How it works" },
    { command: "timezone", description: "Time zone" },
    { command: "paysupport", description: "Payment help" },
    { command: "start", description: "Connect your account" },
    { command: "unlink", description: "Disconnect Telegram" },
  ],
  ru: [
    { command: "lists", description: "Мои чек-листы" },
    { command: "chat", description: "AI-чат" },
    { command: "credits", description: "Баланс AI-кредитов" },
    { command: "premium", description: "Подписка" },
    { command: "app", description: "Мобильное приложение" },
    { command: "help", description: "Как это работает" },
    { command: "timezone", description: "Часовой пояс" },
    { command: "paysupport", description: "Помощь с оплатой" },
    { command: "start", description: "Подключить аккаунт" },
    { command: "unlink", description: "Отключить Telegram" },
  ],
  hi: [
    { command: "lists", description: "मेरी चेकलिस्ट" },
    { command: "chat", description: "AI चैट" },
    { command: "credits", description: "AI क्रेडिट बैलेंस" },
    { command: "premium", description: "सब्सक्रिप्शन" },
    { command: "app", description: "मोबाइल ऐप" },
    { command: "help", description: "यह कैसे काम करता है" },
    { command: "timezone", description: "टाइम ज़ोन" },
    { command: "paysupport", description: "भुगतान सहायता" },
    { command: "start", description: "अकाउंट जोड़ें" },
    { command: "unlink", description: "Telegram अलग करें" },
  ],
};

/** `setMyDescription` (≤512 chars) and `setMyShortDescription` (≤120 chars) per language. */
export const BOT_DESCRIPTIONS: Record<Lang, { description: string; short: string }> = {
  en: {
    description:
      "Gisti turns anything into a checklist. Send a list, notes, a photo, a voice message or a link — " +
      "get a checklist that syncs with the Gisti app. Sign in with the same Google account as in the app: " +
      "checklists and AI credits are shared.",
    short: "Turn notes, photos and voice into checklists — synced with the Gisti app.",
  },
  ru: {
    description:
      "Gisti превращает что угодно в чек-лист. Пришлите список, заметки, фото, голосовое или ссылку — " +
      "получите чек-лист, который синхронизируется с приложением Gisti. Войдите тем же Google-аккаунтом, " +
      "что и в приложении: чек-листы и AI-кредиты общие.",
    short: "Заметки, фото и голосовые — в чек-листы. Синхронизация с приложением Gisti.",
  },
  hi: {
    description:
      "Gisti किसी भी चीज़ को चेकलिस्ट में बदल देता है। लिस्ट, नोट्स, फ़ोटो, वॉइस मैसेज या लिंक भेजें — " +
      "चेकलिस्ट Gisti ऐप के साथ सिंक हो जाएगी। ऐप वाले उसी Google अकाउंट से साइन इन करें: " +
      "चेकलिस्ट और AI क्रेडिट साझा रहते हैं।",
    short: "नोट्स, फ़ोटो और वॉइस को चेकलिस्ट में बदलें — Gisti ऐप के साथ सिंक।",
  },
};
