---
title: "EEA-определение в ConsentManager: binder-ANR и пустой ISO-код"
summary: "TelephonyManager.simCountryIso — binder-вызов (ANR на main) и отдаёт \"\" без SIM; резолв ушёл на IO с кешем, до резолва consent DENIED, fallback через takeIf(isNotEmpty)."
date: 2026-10-26
type: bug-fix
modules: [composeApp]
keywords: [consent, gdpr, anr, TelephonyManager, simCountryIso, networkCountryIso, ConsentManager, firebase-analytics, setConsent, binder]
project: Checklists
---

# EEA-определение в ConsentManager: binder-ANR и пустой ISO-код

**Суть:** геттеры `TelephonyManager` блокируют на binder и без SIM отдают `""` — не звать на main и
не склеивать через `?:`. `ConsentManager.startRegionResolution()` резолвит регион на IO на каждом
старте; prefs-кеш `is_eea` — только для синхронного решения на старте; сигнал — `resolveConsentCountryCode`.

## Проблема / Контекст

1. ANR (Crashlytics `0d39cde7`, Play vitals: binder в
   `ISub.getDefaultSubIdAsUser`): `isDeviceInEea()` звался на main из `GistiApplication.initConsent()`
   и из композиции `MainActivity`.
2. GDPR: `simCountryIso` без SIM = `""` (источник: AOSP `TelephonyManager.java`, SDK android-36),
   цепочка `?:` не доходила до locale → EEA-планшет получал GRANTED без диалога.

## Решение

- `EeaCountryResolution.kt`: `resolveConsentCountryCode` (sim → network → locale, пустые пропускаются),
  `isEeaCountry`, `startupConsentGranted`, `shouldGrantAfterRegionResolved`.
- `ConsentManager(context, scope)`: `applyConsentDefaults()` синхронно ставит выбор или регион из
  кеша, без кеша — DENIED. `startRegionResolution()` (из `onCreate` после `startKoinIfNeeded()`)
  свежо резолвит регион и применяет `consentAfterFreshRegion`: EEA → DENIED, не-EEA → GRANTED.
- `resolveIsEea()`: `withContext(Dispatchers.IO)` + `Mutex`, мемо только в памяти процесса.
- `MainActivity`: `showConsentDialog` стартует `false`, проверка в `LaunchedEffect(consentManager)`.
- Тест: `ConsentEeaCountryResolutionTest` (red до фикса на «пустой SIM»).

## Почему именно так

Без кеша — DENIED до резолва; цена — окно DENIED у не-EEA на первом запуске. Кеш на установку
навсегда (первая версия) ломал диалог при переезде в EEA — поймано ревью; поэтому резолв на каждом
старте. Кешированный GRANTED на старте у переехавшего допустим: Firebase и так хранит прошлый consent.

## Связанные файлы

- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/consent/ConsentManager.kt`
- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/consent/EeaCountryResolution.kt`
- `composeApp/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/MainActivity.kt`
