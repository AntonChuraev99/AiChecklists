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
    "/lists — your checklists\n/credits — AI credit balance\n/premium — subscription\n/app — mobile app\n" +
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
    "/lists — ваши чек-листы\n/credits — баланс AI-кредитов\n/premium — подписка\n/app — мобильное приложение\n" +
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
    "/lists — आपकी चेकलिस्ट\n/credits — AI क्रेडिट बैलेंस\n/premium — सब्सक्रिप्शन\n/app — मोबाइल ऐप\n" +
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
    { command: "credits", description: "AI credit balance" },
    { command: "premium", description: "Subscription" },
    { command: "app", description: "Mobile app" },
    { command: "help", description: "How it works" },
    { command: "paysupport", description: "Payment help" },
    { command: "start", description: "Connect your account" },
    { command: "unlink", description: "Disconnect Telegram" },
  ],
  ru: [
    { command: "lists", description: "Мои чек-листы" },
    { command: "credits", description: "Баланс AI-кредитов" },
    { command: "premium", description: "Подписка" },
    { command: "app", description: "Мобильное приложение" },
    { command: "help", description: "Как это работает" },
    { command: "paysupport", description: "Помощь с оплатой" },
    { command: "start", description: "Подключить аккаунт" },
    { command: "unlink", description: "Отключить Telegram" },
  ],
  hi: [
    { command: "lists", description: "मेरी चेकलिस्ट" },
    { command: "credits", description: "AI क्रेडिट बैलेंस" },
    { command: "premium", description: "सब्सक्रिप्शन" },
    { command: "app", description: "मोबाइल ऐप" },
    { command: "help", description: "यह कैसे काम करता है" },
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
