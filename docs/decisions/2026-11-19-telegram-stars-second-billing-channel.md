---
title: "Премиум за Telegram Stars — второй канал оплаты, общий премиум на все платформы"
summary: "Подписку продаёт и бот (@gisti_app_bot) за Stars; премиум = RevenueCat ИЛИ telegram_premium_until > now, один на Android, веб и Telegram; в Android-приложении Stars не упоминаются."
date: 2026-11-19
type: decision
modules: [firebase-functions, telegram-bot, firestore.rules]
keywords: [telegram, stars, XTR, premium, verify_premium, anti-steering, subscription, refund]
project: Checklists
---

# Премиум за Telegram Stars — второй канал оплаты, общий премиум на все платформы

**Суть:** до этой задачи премиум продавался только через Google Play (RevenueCat), а веб получал его через тот же Google-аккаунт. Теперь подписку продаёт и Telegram-бот за Stars, и она общая: премиум = RevenueCat **или** `telegram_premium_until > now` на credit doc `users/{credit_user_id}`. Android-приложение про Stars и оплату в боте молчит.

## Проблема / Контекст

Владелец: подписка за звёзды «общая для приложения и для телеграм бота, вообще все общее». Бот работает с тем же Google-аккаунтом — отдельный премиум означал бы двойную оплату. Цифровые товары в Telegram продаются только за Stars (`XTR`), подписка — только на 30 дней (`subscription_period = 2592000`), без триала. <!-- docs-leak-scan: reviewed — Bot API constraint, not a metric -->


## Решение

- Цена — `remote_config/current.telegram_premium_price_stars`, по умолчанию 100 в коде (выбор владельца). Воркер и Python принимают только целое положительное число.
- `verify_premium` = старое тело (`verify_store_premium`) **или** активный Telegram-премиум; ветка RC-revoke не снимает `is_premium`, пока Telegram-премиум активен. Иначе ночной `refill_premium_credits` отбирал бы премиум у Stars-покупателей.
- Каждая оплата — документ `telegram_payments/{charge_id}`, создаётся `transaction.create` (идемпотентность по charge id). Нет привязки → `unassigned`, премиум применяется при следующей привязке этого Telegram-аккаунта.
- Возврат — вручную: `telegram-bot/scripts/refund.mjs <telegram_user_id> <charge_id>` → `refundStarPayment` + `revoke_payment`. `/paysupport` обязателен по правилам Telegram.
- В боте меню «Подписка» (`/premium`) с текстом «одна подписка для Telegram, приложения и веба» и «Мобильное приложение» (`/app`) со ссылкой на Google Play.

## Почему именно так

- **Один credit doc на аккаунт** — тот же источник правды, что у RevenueCat-пути.
- **ИЛИ, а не «последний побеждает»:** отмена одной подписки не снимает премиум, оплаченный другой.
- **Anti-steering Google Play:** приложение из Play не может направлять к оплате мимо Play Billing, поэтому экран Telegram в приложении говорит только о чек-листах, чате и синхронизации. Бот — вне Play, там цена и ссылка на Play допустимы.

## Последствия

- Деплой `verify_premium` обязан включать все функции, которые его зовут (`refill_premium_credits`, `restore_credits_after_purchase`, `on_rc_event_created`) — иначе старая ревизия снимет премиум.
- Поля выдачи премиума продублированы в `telegram_link.py` и `main._handle_rc_event_payload` — правка одного пути требует сверки второго (открытое замечание ревью).
- Продление после отвязки Telegram списывает звёзды, но премиум ждёт повторной привязки; бот говорит об этом прямо, возврат — через `/paysupport`.
- Политика конфиденциальности (`hosting/public/privacy-policy.html`) описывает данные Telegram и записи об оплате; Play Data Safety («User IDs») — до Android-релиза с экраном Telegram.

## Ссылки

- Документ задачи: [telegram-bot-phase1-2026-11-19](../archive/telegram-bot-phase1-2026-11-19.md)
- Bot API, платежи в Stars: https://core.telegram.org/bots/payments-stars
