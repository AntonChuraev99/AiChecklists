---
title: "Analyze: бессмысленный текст из слов становится чек-листом и списывает кредиты"
date: 2026-11-21
type: backlog
status: ready
severity: low
effort: S
why_not_now: не регрессия (было до задачи analyze-reliability); любой фильтр мусора рискует отказать настоящим коротким спискам, нужна выборка реальных вводов
modules: [firebase-functions, feature/analyze]
keywords: [analyze, nothing_found, generate_checklist, gibberish, prompts_private, credits]
project: Checklists
---

# Мусорный текст из слов → чек-лист с теми же словами

На эмуляторе 2026-11-21: ввод `asdf qwer zxcv` в Analyze → Text дал чек-лист «asdf qwer zxcv» с пунктами
asdf / qwer / zxcv и списал кредиты за одно действие. Чистая пунктуация (`....`) и тишина в голосе
корректно уходят в `nothing_found` без списания.

Причина: сервер отвечает `nothing_found` (422 + `refund_credits`) только на пустой ответ модели <!-- docs-leak-scan: reviewed — HTTP status code, no metric -->
(`firebase-functions/main.py`), а модель разбивает любые слова в пункты.

Варианты, когда возьмём:
- Инструкция в промпте `generate_checklist` (`firebase-functions/prompts_private.py` — не проверено проверкой фактов: файл под gitignore, в git его нет): вернуть пустой список, если вход не содержит осмысленных действий или предметов.
- Сначала замерить по логам Cloud Run, как часто реальные вводы похожи на мусор. Без этого фильтр может отказывать `milk eggs bread`.

Отчёт прогона: `docs/archive/analyze-reliability-background-2026-11-20.md`, итерация 18.
