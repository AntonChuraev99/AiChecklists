---
title: "play-services-auth 22 удаляет GoogleSignIn — legacy-fallback входа держит библиотеку на 21.x"
date: 2026-10-23
type: backlog
status: ready
severity: low
effort: M
why_not_now: снятие удержания означает удалить или заменить видимый пользователю путь входа; это решение, а не механический бамп, поэтому оно не вошло в PR обновления зависимостей
modules: [core/auth/impl, gradle/libs.versions.toml]
keywords: [play-services-auth, GoogleSignIn, GoogleSignInClient, legacy-fallback, Credential Manager, AndroidGoogleAuthProvider]
project: gisti-ai-checklists
---

# play-services-auth 22 и legacy-вход через GoogleSignIn

Найдено 2026-10-23 при обновлении всех зависимостей (`docs/archive/deps-update-revenuecat-3-15-2026-10-23.md`). <!-- docs-leak-scan: reviewed — имя файла с версией SDK RevenueCat, не выручка -->

## Что упирается

`playServicesAuth` оставлен на 21.6.0, последней версии 21.x. С 22.0.0 `:core:auth:impl` перестаёт компилироваться: класса `GoogleSignIn` больше нет (источник: компиляция в этой задаче).

Legacy-путь — `tryLegacyGoogleSignIn` в `core/auth/impl/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/core/auth/impl/AndroidGoogleAuthProvider.kt`. В нём два вызова API, которого в 22.x нет:
- `GoogleSignIn.getClient(activity, gso)`;
- `GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)`.

Этот путь — запасной, на случай когда вход через Credential Manager не сработал.

## Что решить, прежде чем брать

- Нужен ли fallback сейчас. Сколько входов реально проходит через него, видно только по логам. Без замера удалять его нельзя: это молчаливое удаление пути входа (`~/.claude/rules/user-feedback.md`, не проверено — глобальное правило харнесса вне репозитория).
- Если нужен — чем его заменить в 22.x. Google ведёт миграцию с legacy GSI на Credential Manager и Authorization API: <https://developer.android.com/identity/sign-in/legacy-gsi-migration> (не проверено, какой из вариантов закрывает именно наш сценарий).
