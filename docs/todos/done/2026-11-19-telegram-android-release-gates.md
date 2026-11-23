---
status: resolved
resolved: 2026-11-23
blocking_reason: ждёт Android-релиза, в котором впервые есть экран Telegram (ветка telegram-bot-phase1 влита после 1.24.0)
resume_trigger: готовится релиз (bump versionCode после 1.24.0 / `/create-release` / `/build-release-aab`) из транка, где есть `composeApp/.../telegram/TelegramScreen.kt`
keywords: [telegram, data safety, play console, privacy policy, botfather, release gate]
---

# Telegram — гейты перед Android-релизом с экраном Telegram

Экран Telegram и привязка аккаунта влиты в транк, на вебе живут сразу после merge. В Android они
попадут со следующим релизом — до его выкладки в Play закрыть:

1. ~~**Play Data Safety**~~ — ✅ 2026-11-23, правка не нужна: экспорт декларации из консоли перед
   релизом 1.25.0 показал, что «User IDs» (`PSL_USER_ACCOUNT`) уже объявлен — собирается, не
   передаётся, цель «App functionality» среди прочих. Telegram user id под него подпадает
   (определение Google: «an account ID, account number, or account name»).
2. ~~**Политика конфиденциальности**~~ — ✅ 2026-11-19: раздел «Telegram Bot (optional)» в
   `hosting/public/privacy-policy.html` задеплоен на Firebase Hosting (живая страница содержит его).
3. ~~**BotFather**~~ — ✅ 2026-11-23: владелец выставил `/setjoingroups` → Disable.
4. ~~**Фаза 4 (напоминания)**~~ — ✅ 2026-11-23: часовой пояс и канал напоминаний не подпадают ни
   под один тип данных Google Play; отправка текста пунктов в Telegram идёт по действию самого
   пользователя (привязал бота, выбрал канал) — исключение из «sharing». Отдельных строк не нужно
   (трактовка политики, подтверждается только прохождением ревью).

Все гейты закрыты перед релизом 1.25.0 (vc94).

Источник требований — документ задачи `docs/archive/telegram-bot-phase1-2026-11-19.md`, раздел
«Release gates (Android)».
