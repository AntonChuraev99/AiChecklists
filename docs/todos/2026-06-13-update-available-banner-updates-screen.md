---
title: "Update-available banner on the Updates screen (prompt to update when a new version is out)"
date: 2026-06-13
status: deferred
parent_task: "release 1.16.5 bump session (2026-06-13)"
blocking_reason: user-deferred ("потом сделаем" — заведено как напоминание при бампе версии до 1.16.5)
resume_trigger: "User says «делаем баннер обновления на экране Updates / update-available banner / баннер при новой версии»"
estimated_complexity: Standard/Medium
keywords: [update-banner, updates-feed, UpdateFeedScreen, AppUpdateController, in-app-update, version-available, android-only, play-app-update]
---

# Update-available banner на экране Updates — Deferred

## Что нужно

Баннер на экране **Updates** (Update Feed), который показывается, когда вышла новая версия приложения,
и побуждает пользователя обновиться. Появляется **до** обновления (в отличие от «What's New», который
показывается *после*). CTA запускает уже реализованный Google Play in-app update flow.

Сформулировано пользователем дословно: «баннер при поднятии версии на экран updates».

## Контекст

Заведено в сессии бампа версии до **1.16.5 (versionCode 48)** 2026-06-13. Реализацию отложили («потом сделаем»).
Инфраструктура для CTA **уже есть** — в этой же линейке закоммичен Google Play in-app update flow
(`AppUpdateController`, commit `d1058cd0 feat(app-update): add Google Play in-app update flow`).
Этот баннер — дополнительный, всегда-видимый канал на экране Updates для пользователей, которые
закрыли/пропустили системный диалог обновления Play.

## Scope (что реализовать)

1. **Новый компонент** `UpdateAvailableBanner.kt` в
   `feature/updatefeed/src/commonMain/.../presentation/components/` — визуал по образцу
   `ReleaseCard.kt` (Material 3, та же палитра/spacing). Текст «Доступна новая версия» + CTA «Обновить».
   Опционально dismissible (см. открытые вопросы).
2. **MVI-провод** в `UpdateFeedScreenContract.kt`: поле состояния
   `updateAvailable: Boolean` (или `availableVersionCode: Int?`) + интент `OnUpdateBannerClick`
   (и `OnUpdateBannerDismiss`, если dismissible).
3. **Размещение** в `UpdateFeedScreen.kt` — баннер вверху списка, над `VersionReleaseGroup`-карточками
   (sticky или первым item в `LazyColumn`).
4. **Источник «доступно ли обновление»** — мост из commonMain в androidMain (см. ниже про KMP).
5. **CTA-действие** — переиспользовать существующий `AppUpdateController.startUpdate()`
   (IMMEDIATE→FLEXIBLE). Не дублировать flow, дёргать готовый.
6. **Строки** — новые ключи в `core/designsystem/.../strings.xml` (EN-only, без хардкода литералов в Kotlin —
   см. rule `compose-resources-kmp`).
7. **Аналитика** — события `update_banner_shown` / `update_banner_click` / `update_banner_dismiss`
   через `AnalyticsTracker` (в одном стиле с уже существующими `app_update_*`).
8. **Тесты** — `UpdateFeedViewModelTest`: баннер виден когда `updateAvailable=true`, клик эмитит
   правильный SideEffect/интент.

## Ключевая KMP-сложность (НЕ забыть)

`feature/updatefeed` — **commonMain** (есть android/ios/wasmJs targets), а `AppUpdateController` живёт в
`composeApp/androidMain` (Google Play API нет на web/iOS). Нужен мост:

- Объявить узкий интерфейс в commonMain, например `UpdateAvailabilityChecker` (метод
  `suspend fun isUpdateAvailable(): Boolean` или `Flow<Boolean>`), инжектируемый через Koin.
- `actual`/androidMain-реализация делегирует `AppUpdateManager.appUpdateInfo` →
  `updateAvailability() == UPDATE_AVAILABLE` (через существующий `AppUpdateController`).
- iOS/wasmJs-реализация → всегда `false` (нет Play) ⇒ баннер на этих платформах не показывается.
  **Баннер = Android-only по факту**, но без `expect/actual` UI — управляется флагом состояния из DI.

## Открытые вопросы (решить при возобновлении — через AskUserQuestion)

1. **Dismissible?** Можно ли закрыть баннер, и если да — на сессию или навсегда (persist в datastore)?
   По умолчанию предлагаю: dismiss на сессию (in-memory), снова показать при следующем cold start если
   обновление всё ещё доступно.
2. **Не дублирует ли системный Play-диалог?** Системный in-app update уже всплывает на cold start.
   Баннер — это запасной канал. Убедиться, что они не конфликтуют (если системный показан и принят —
   баннер скрыть).
3. **Что показывать в тексте** — просто «Доступно обновление» или версию/что нового?
   (версия требует чтения available versionCode из `appUpdateInfo`).

## Точки интеграции (файлы)

- `feature/updatefeed/src/commonMain/.../presentation/UpdateFeedScreen.kt` — место для баннера.
- `feature/updatefeed/src/commonMain/.../presentation/UpdateFeedScreenContract.kt` — State/Intent.
- `feature/updatefeed/src/commonMain/.../presentation/UpdateFeedViewModel.kt` — логика показа + клик.
- `feature/updatefeed/src/commonMain/.../presentation/components/ReleaseCard.kt` — референс визуала.
- `composeApp/src/androidMain/.../appupdate/AppUpdateController.kt` — готовый flow для CTA + источник availability.
- `core/designsystem/.../composeResources/values/strings.xml` — строки.

## Связанные задачи

- `docs/todos/2026-05-24-google-play-in-app-updates.md` — **родственная** задача (системный Play in-app
  update flow + «What's New» modal *после* апдейта + drawer badge). Делит `AppUpdateController` и
  version-tracking. Этот баннер — отдельная UI-поверхность (*до* апдейта, на экране Updates), но при
  реализации свериться, чтобы переиспользовать общий мост availability и не дублировать аналитику.
- `docs/completed/in-app-updates-2026-06-13.md` — solution-журнал реализованного core flow.

## Как возобновить

1. Прочитай этот файл + `docs/completed/in-app-updates-2026-06-13.md` (как устроен `AppUpdateController`).
2. Реши открытые вопросы выше через `AskUserQuestion`.
3. Цепочка: `@mobile-design-expert` (дизайн баннера по `ReleaseCard`) → `@kmp-expert` (мост
   `UpdateAvailabilityChecker` commonMain + Koin) → `@android-expert` (androidMain actual + провод
   в ViewModel/Screen + строки + тесты).
4. Build: `:androidApp:compileDebugKotlin` + `:composeApp:compileKotlinWasmJs` + `:feature:updatefeed:testAndroidHostTest`.
