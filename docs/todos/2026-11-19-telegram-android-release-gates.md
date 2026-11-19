---
status: deferred
blocking_reason: ждёт Android-релиза, в котором впервые есть экран Telegram (ветка telegram-bot-phase1 влита после 1.24.0)
resume_trigger: готовится релиз (bump versionCode после 1.24.0 / `/create-release` / `/build-release-aab`) из транка, где есть `composeApp/.../telegram/TelegramScreen.kt`
keywords: [telegram, data safety, play console, privacy policy, botfather, release gate]
---

# Telegram — гейты перед Android-релизом с экраном Telegram

Экран Telegram и привязка аккаунта влиты в транк, на вебе живут сразу после merge. В Android они
попадут со следующим релизом — до его выкладки в Play закрыть:

1. **Play Data Safety** — объявить «User IDs» (Telegram user id, хранится вместе с аккаунтом) →
   назначение «App functionality», не продаётся, не для рекламы. Прецедент: релиз 1.18.7 отклонён
   за расхождение декларации с фактом. Делает `@google-play-console-expert` или владелец в консоли.
2. ~~**Политика конфиденциальности**~~ — ✅ 2026-11-19: раздел «Telegram Bot (optional)» в
   `hosting/public/privacy-policy.html` задеплоен на Firebase Hosting (живая страница содержит его).
3. **BotFather** `/setjoingroups` → Disable (действие владельца; код и так игнорирует не-private чаты).

Источник требований — документ задачи `docs/archive/telegram-bot-phase1-2026-11-19.md`, раздел
«Release gates (Android)».
