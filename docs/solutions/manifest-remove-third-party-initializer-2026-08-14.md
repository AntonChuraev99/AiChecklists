---
title: Manifest — Remove Third-Party Initializer from Startup Path
date: 2026-08-14
category: Android manifest, startup performance, androidx.startup
keywords: tools:node="remove", InitializationProvider, ContentProvider.onCreate, WorkManager, lazy initialization, Configuration.Provider
---

# Manifest — Remove Third-Party Initializer from Startup Path

> Объём: 557 слов — пошаговое руководство с примерами кода и валидацией на трёх уровнях (artifact, runtime, functional); материал не влезает в потолок из-за обязательности каждого шага и важности полноты для следующего использования

## Суть

Сторонний AAR (WorkManager, Firebase, OkHttp, etc.) может автоматически регистрировать свой инициализатор в merged manifest через `androidx.startup.InitializationProvider`. Инициализация запускается в `ContentProvider.onCreate()` — **перед `Application.onCreate()`** — поэтому любой throw там фатален и не перехватывается приложением. Если компонент не используется или используется только транзитивно, удалить его со старт-пути через `tools:node="remove"` в манифесте.

## Проблема

Инициализаторы в merged manifest под `<provider android:name="androidx.startup.InitializationProvider">` запускаются в критическом `ActivityThread.installProvider` → `ContentProvider.onCreate()`. На этом пути:

- Throw убивает процесс **до** запуска Application.onCreate()
- Приложение не может перехватить исключение через try-catch
- На конкретных ROM (версия, OEM-модификация) либо версиях framework'а метод может отсутствовать, несмотря на объявленный API уровень

**Пример (прецедент 2026-08-13):** WorkManager 2.11.2 на Pixel 8 Pro (Android 14, API 34). Библиотека вызывает `JobScheduler.forNamespace()` под защитой `SDK_INT >= 34`, но ROM рапортует API 34 с `framework.jar` без этого метода → `NoSuchMethodError` в ContentProvider.onCreate() → crash 100% до любого кода приложения.

## Решение

### Шаг 1: Найти инициализатор в merged manifest

Откройте APK (или прогоните `processDebugMainManifest` для debug build):

```bash
# Распакуйте APK
unzip -q app-debug.apk -d apk_extracted

# Посмотрите merged manifest
cat apk_extracted/AndroidManifest.xml | grep -A 20 "InitializationProvider"
```

Или через тест (Robolectric):

```kotlin
val context = RuntimeEnvironment.getApplication()
val component = ComponentName(context.packageName, "androidx.startup.InitializationProvider")
val metaData = context.packageManager
    .getProviderInfo(component, PackageManager.GET_META_DATA)
    .metaData
    ?: return emptySet()
val initializers = metaData.keySet()
    .filter { metaData.getString(it) == "androidx.startup" }
    .toSortedSet()
println("Declared initializers: $initializers")
```

Список выглядит примерно так:

```
androidx.work.WorkManagerInitializer
androidx.emoji2.text.EmojiCompatInitializer
androidx.lifecycle.ProcessLifecycleInitializer
androidx.profileinstaller.ProfileInstallerInitializer
com.revenuecat.purchases.common.offlineentitlements.firebase.FirebaseOfflineEntitlementsInitializer
okhttp3.internal.platform.android.AndroidInitializer
```

### Шаг 2: Определить, используется ли компонент

Проверьте, есть ли в коде вызовы компонента:

```bash
# Поиск в исходниках
rg "WorkManager\.getInstance|WorkRequest|enqueue" --type kotlin

# Если не нашли прямых вызовов — проверьте транзитивные
# (Glance, другие зависимости, которые могут внутри себя использовать)
```

**Признак транзитивного использования:**
- Код не вызывает API явно, но фреймворк вызывает его внутри себя
- Пример: Glance вызывает `WorkManager.getInstance()` → `enqueueUniqueWork()` когда обновляется виджет

### Шаг 3: Удалить из начального старта

Если компонент не используется **вообще** или используется только **по требованию**, удалите его из InitializationProvider.

**В `androidApp/src/main/AndroidManifest.xml`:**

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    
    <!-- Drop WorkManager from the startup path — see docs/solutions/manifest-remove-third-party-initializer-2026-08-14.md -->
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        tools:node="remove" />
</provider>
```

**Обязательные атрибуты:**

| Атрибут | Смысл |
|---------|-------|
| `tools:node="merge"` | на `<provider>` — сохранить других инициализаторов, не заменять весь блок |
| `tools:node="remove"` | на `<meta-data>` целевого инициализатора — удалить только эту запись |
| `android:name="<инициализатор>"` | точное имя класса инициализатора |

**Что НЕ делать:** `tools:node="remove"` на самом `<provider>` удалит ВЕСЬ InitializationProvider, в том числе остальные инициализаторы (emoji2, lifecycle, etc.) — это破 состояние.

### Шаг 4: Восстановить поздний запуск (если компонент нужен)

Если компонент используется:
- **Явно (прямые вызовы)** — больше ничего не нужно, компонент инициализируется на первом использовании
- **Транзитивно (сторонний фреймворк внутри себя)** — компонент ТРЕБУЕТ инициализации перед first use

Если транзитивное использование существует, добавьте ленивую инициализацию на Application:

```kotlin
// androidApp/src/main/kotlin/.../GistiAndroidApplication.kt

class GistiAndroidApplication : Application(), Configuration.Provider {
    // WorkManager требуется Glance при первом обновлении виджета
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
```

Контракт `Configuration.Provider` — это стандартный способ, которым все фреймворки в merged manifest (lifecycle, ProfileInstaller, etc.) уже выполняют позднюю инициализацию.

## Валидация

### На уровне артефакта (merged manifest)

```bash
./gradlew :androidApp:assembleDebug
unzip -q build/outputs/apk/debug/app-debug.apk -d apk_extracted
grep -c "androidx.work.WorkManagerInitializer" apk_extracted/AndroidManifest.xml
# Ожидаемый результат: 0 (не найдено)
```

### На уровне runtime (unit test)

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppStartupTest {
    @Test
    fun mergedManifest_doesNotDeclareWorkManagerInitializer() {
        val context = RuntimeEnvironment.getApplication()
        val component = ComponentName(context.packageName, "androidx.startup.InitializationProvider")
        val metaData = context.packageManager
            .getProviderInfo(component, PackageManager.GET_META_DATA)
            .metaData ?: emptyBundle()
        val initializers = metaData.keySet()
            .filter { metaData.getString(it) == "androidx.startup" }
            .toSet()
        
        assertFalse(
            "androidx.work.WorkManagerInitializer" in initializers,
            "WorkManagerInitializer must not run on startup path; found: $initializers"
        )
    }
    
    @Test
    fun startupProviderOnCreate_doesNotBuildWorkManager() {
        Robolectric.buildContentProvider(InitializationProvider::class.java).create()
        assertFalse(WorkManager.isInitialized())
    }
}
```

### На уровне код (функциональность)

- APK собирается без ошибок: `./gradlew :androidApp:assembleRelease`
- Все модульные тесты pass: `:androidApp:testDebugUnitTest`
- Интеграционные тесты видимого кода pass: `:composeApp:testAndroidHostTest`
- На устройстве / эмуляторе холодный старт не крашится

## Граница и ограничения

**Граница:** Фикс касается **только процесс-старта**. Если компонент требуется для работы остальной функциональности (виджет, камера, реклама), он **все равно инициализируется** на первое использование. Код, который вызывает его, должен быть готов к этому (обернуть в try-catch или добавить Configuration.Provider).

**Пример:** WorkManager был удален из процесс-старта, но Glance все еще вызывает `WorkManager.getInstance()` → `enqueueUniqueWork()` при обновлении виджета. На сломанной ROM эта операция все еще потенциально упадет, но теперь это может быть перехвачено в Broadcast-пути или обрамлено в try-catch — **после Application.onCreate()**, где catch работает.

**Не покрыто:** Добавление try-catch в места, которые вызывают компонент транзитивно, — это отдельная задача. Документируйте их в KDoc и трекуйте отдельно.

## Связанные файлы

- `androidApp/src/main/AndroidManifest.xml` — place-holder для tools:node="remove"
- `androidApp/src/main/kotlin/GistiAndroidApplication.kt` — Configuration.Provider если требуется ленивая инициализация
- `androidApp/src/test/kotlin/AppStartupTest.kt` — unit-тесты валидации

## История

- **2026-08-13:** Pixel 8 Pro crash на 1.19.1 — `JobScheduler.forNamespace NoSuchMethodError` в WorkManagerInitializer на процесс-старте
- **2026-08-14:** Диагностирован корень (manifest-инициализация вместо ленивой), реализован фикс (tools:node="remove" + Configuration.Provider), покрыто 3-слойной валидацией
