---
title: Sync Account Banner v1 — Dismissal Gates & Persistence
date: 2026-06-22
status: Done
type: feature
complexity: Standard
impact: Medium
modules: [core:datastore (api+impl), core:designsystem, feature:home]
keywords: [sync-banner, SyncAccountBanner, dismiss, datastore-persistence, dismiss-count, activation-gate, showSyncBanner, HintsRepository, headerItemCount, reorder-offset]
start_sha: 3ffdd82e
---

# Sync Account Banner v1 — Dismissal Gates & Persistence

**Статус:** Done

## Проблема / Контекст

`SyncAccountBanner` на главном экране (`MainScreen`, Compact/phone путь) раздражал новых пользователей:

1. **Показывался всегда при `!isGoogleLinked`** — в том числе на пустом экране, где он рендерился ПОВЕРХ activation-hero (центральная AI-кнопка + чат из A/B-теста `activation_bundle_v1`). Синхронизировать ещё нечего — баннер бессмысленный шум.
2. **Не закрывался.** У баннера был только trailing-chevron (переход на Sign-In), не было дисмисса вообще.

Запрос пользователя: (а) не показывать пока ничего синхронизировать (пустой экран с activation-hero / единственный авто-созданный чеклист); (б) добавить закрытие — 1 закрытие скрывает до перезапуска, 3 закрытия скрывают навсегда.

## Решение

Видимость сведена в одно derived-поле `MainScreenState.Success.showSyncBanner`, вычисляемое в `MainScreenViewModel`:

```kotlin
val showSyncBanner = !userData.isGoogleLinked &&
    checklists.size > SYNC_BANNER_MIN_CHECKLISTS &&        // = 1  → нужно >1
    flags.syncBannerDismissCount < SYNC_BANNER_MAX_DISMISSALS &&  // = 3
    !flags.syncBannerDismissedThisSession
```

Четыре оси:
- **`!isGoogleLinked`** — баннер вообще про вход; залогиненному не показываем (было исходным условием).
- **`size > 1`** — порог «есть что синхронизировать». Activation-hero показывается только при пустом списке, поэтому отдельная проверка флага `activation_bundle_v1` НЕ нужна: один порог покрывает оба случая из запроса (пусто + 1 авто-созданный).
- **`dismissCount < 3`** — персистентное затухание (DataStore, переживает рестарт → «навсегда после 3»).
- **`!dismissedThisSession`** — in-memory флаг в ViewModel (живёт одну сессию процесса, сам сбрасывается при пересоздании VM → «скрыть до перезапуска»).

### DataStore слой (`HintsRepository`)
- `syncBannerDismissCount: Flow<Int>` — `AppDatastore.observeInt("sync_banner_dismiss_count", 0)`.
- `suspend incrementSyncBannerDismissCount()` — read-modify-write через `.first()` + `saveInt`. Гонки нет: дисмисс сразу убирает баннер (сессионный флаг), повторный тап по тому же баннеру невозможен.

### Presentation (`MainScreenViewModel`)
- Сессионный `_syncBannerDismissedThisSession = MutableStateFlow(false)`.
- `combine` второй группы расширен с 3 до 5 потоков; т.к. в Kotlin нет 5-арного tuple, потоки свёрнуты в приватный `data class HomeFlags(...)`.
- Intent `OnDismissSyncBanner` → `handleDismissSyncBanner()`: ставит сессионный флаг + `viewModelScope.launch { hintsRepository.incrementSyncBannerDismissCount() }`.
- Пороги в `private companion object`: `SYNC_BANNER_MIN_CHECKLISTS = 1`, `SYNC_BANNER_MAX_DISMISSALS = 3`.

### UI
- `SyncAccountBanner` — добавлен параметр `onDismiss: () -> Unit`; trailing chevron (`KeyboardArrowRight`) заменён на `IconButton(Icons.Filled.Close)`. `IconButton` поглощает свой тап и НЕ пробрасывает его в баннер-широкий `clickable(onSignInClick)` — закрытие и вход не конфликтуют.
- `MainScreenContent` — баннер гейтится на `screenState.showSyncBanner` (было `!screenState.isGoogleLinked`); `headerItemCount` синхронизирован: `(if (showSyncBanner) 1 else 0) + 1`. **Важно:** offset используется для индексов drag-reorder — рассинхрон с реальным наличием item'а сдвинул бы перестановку после дисмисса.
- `MainScreen` — проводка `onDismissSyncBanner = { viewModel.sendIntent(OnDismissSyncBanner) }`.
- `strings.xml` (values + values-ru) — `sync_banner_dismiss` (EN "Dismiss sync banner" / RU "Скрыть баннер синхронизации"), content description крестика.

## Почему именно так

**Двухосевой дисмисс.** Только сессионный флаг → требует повторного закрытия после каждого рестарта (раздражает). Только персистентный счётчик → закрытие не действует мгновенно/на сессию. Комбо: 1 рестарт = скрыть до следующего запуска; накопление до 3 = скрыть навсегда. Это ровно поведение из запроса.

**Сессионный флаг не сбрасывается вручную** — пересоздание ViewModel при старте процесса делает это бесплатно.

## Сценарий (новичок, постоянно закрывает)
```
[Запуск 1] 2 чеклиста → баннер виден; тап × → session=true, count→1 → скрыт
[Рестарт]  session=false (новый VM), count=1<3 → виден снова; тап × → count→2 → скрыт
[Рестарт]  count=2<3 → виден; тап × → count→3 → скрыт
[Рестарт]  count=3 → 3<3 ложно → скрыт НАВСЕГДА
```

## Изменённые файлы
- `core/datastore/api/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/core/datastore/api/HintsRepository.kt` — интерфейс (+2 члена).
- `core/datastore/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/core/datastore/impl/HintsRepositoryImpl.kt` — impl (`observeInt`/`saveInt`).
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/MainScreenContract.kt` — `showSyncBanner` + `OnDismissSyncBanner`.
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/MainScreenViewModel.kt` — derive + dismiss handler + `HomeFlags` + пороги.
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/MainScreenContent.kt` — gate + offset.
- `feature/home/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/home/presentation/MainScreen.kt` — проводка intent.
- `core/designsystem/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/desingsystem/components/SyncAccountBanner.kt` — `onDismiss` + `IconButton(Close)`.
- `core/designsystem/src/commonMain/composeResources/values/strings.xml` + `values-ru/strings.xml` — `sync_banner_dismiss`.
- `feature/home/src/androidMain/.../MainScreenContentPreviews.kt` — превью отражает `showSyncBanner = size>1`.
- `feature/home/src/commonTest/.../MainScreenViewModelTest.kt` — обновлён `FakeHintsRepository` (+count), параметризован `FakeChecklistRepository`/`makeViewModel`, +5 тестов.

## Validation
- `:feature:home:testAndroidHostTest` PASS (вкл. 5 новых тестов: hidden@empty, hidden@single, shown@>1, hidden@count=3, dismiss→hide+count++).
- wasmJs compile (feature:home + core:designsystem + core:datastore:impl) PASS; androidMain compile PASS.
- Screenshot-тестов баннера нет — смена layout (chevron→×) golden не ломает.

## Заметки
- Grid/tablet путь (`MainScreenContentLazyGrid`) исторически баннер не показывает — поведение не менялось.
- `start_sha` 3ffdd82e — изменения на момент написания доки НЕ закоммичены.
