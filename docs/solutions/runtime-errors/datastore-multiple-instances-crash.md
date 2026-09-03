---
date: 2026-01-29
category: runtime-errors
module: core/datastore, feature/user, widget
tags: [datastore, singleton, crash, koin, dependency-injection]
symptoms:
  - IllegalStateException: There are multiple DataStores active for the same file
  - Crash when opening app after widget interaction
  - Channel is unrecoverably broken and will be disposed
status: solved
---

# DataStore Multiple Instances Crash

## Problem

После взаимодействия с виджетом (toggle элемента) и последующего открытия приложения происходит краш с ошибкой:

```
FATAL EXCEPTION: DefaultDispatcher-worker-9
java.lang.IllegalStateException: There are multiple DataStores active for the same file:
/data/data/com.antonchuraev.aichecklists/files/user/datastore.preferences_pb.
You should either maintain your DataStore as a singleton or confirm that there is
no two DataStore's active on the same file (by confirming that the scope is cancelled).
```

## Root Cause

`UserDataRepositoryImpl` создавал новый `AppDatastore` напрямую в конструкторе:

```kotlin
// ПРОБЛЕМА: Каждый инстанс создаёт новый DataStore
class UserDataRepositoryImpl(...) {
    private val appDatastore: AppDatastore = AppDatastore("user/datastore")
}
```

Когда виджет и приложение работали параллельно:
1. Виджет через Koin получал `UserDataRepositoryImpl` → создавался `DataStore`
2. Приложение через Koin получало `UserDataRepositoryImpl` → создавался **ещё один** `DataStore`
3. Два `DataStore` для одного файла → **crash**

## Solution

### Подход: Singleton Provider + DI

**1. Создан `UserAppDatastoreProvider`** — singleton object с lazy инициализацией:

```kotlin
// core/datastore/api/AppDatastore.kt

/**
 * Singleton provider for user preferences DataStore.
 * Ensures only one DataStore instance exists for user/datastore file.
 */
object UserAppDatastoreProvider {
    private const val USER_DATASTORE_NAME = "user/datastore"

    val instance: AppDatastore by lazy {
        AppDatastore(createDataStore(USER_DATASTORE_NAME))
    }
}
```

**2. Изменён конструктор `AppDatastore`** — принимает `DataStore` через параметр:

```kotlin
// Было:
class AppDatastore(name: String, ...) {
    private val dataStore = createDataStore(name)  // Создавал новый каждый раз!
}

// Стало:
class AppDatastore(
    private val dataStore: DataStore<Preferences>,  // Получает готовый singleton
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
)
```

**3. Обновлён `UserDataRepositoryImpl`** — получает `AppDatastore` через конструктор:

```kotlin
class UserDataRepositoryImpl(
    private val appScope: CoroutineScope,
    private val deviceIdProvider: DeviceIdProvider,
    private val userApiService: UserApiService,
    private val logger: AppLogger,
    private val appDatastore: AppDatastore  // Инжектится через DI
) : UserDataRepository {
    // Убрано: private val appDatastore = AppDatastore("user/datastore")
}
```

**4. Обновлён Koin модуль**:

```kotlin
// feature/user/di/UserFeatureModule.kt

val userFeatureModule = module {
    single {
        UserDataRepositoryImpl(
            appScope = get(),
            deviceIdProvider = get(),
            userApiService = get(),
            logger = get(),
            appDatastore = UserAppDatastoreProvider.instance  // Singleton!
        )
    } bind UserDataRepository::class
}
```

## Why This Works

| Механизм | Гарантия |
|----------|----------|
| `object` | Kotlin гарантирует единственный инстанс |
| `by lazy` | Создаётся только при первом обращении |
| Koin `single` | Repository создаётся один раз |
| DI injection | Все получают один и тот же `AppDatastore` |

## Prevention

### Best Practices для DataStore

1. **Никогда не создавать DataStore напрямую в классе** — только через DI или singleton provider
2. **Один DataStore = один файл** — не создавать несколько инстансов для одного файла
3. **Использовать `by lazy`** — для отложенной инициализации singleton
4. **Для Android: `preferencesDataStore` delegate** — официальный паттерн для single-process apps

### Альтернативные подходы

**Подход A: `preferencesDataStore` delegate (Android-only)**
```kotlin
// Top-level в файле
val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user/datastore"
)
```

**Подход B: Кэш на уровне модуля**
```kotlin
private object DataStoreCache {
    private val cache = mutableMapOf<String, DataStore<Preferences>>()
    fun getOrCreate(name: String) = synchronized(cache) {
        cache.getOrPut(name) { createDataStore(name) }
    }
}
```

## Related Files

- `core/datastore/api/src/commonMain/kotlin/.../AppDatastore.kt` — singleton provider
- `feature/user/src/commonMain/kotlin/.../UserDataRepositoryImpl.kt` — использует DI
- `feature/user/src/commonMain/kotlin/.../UserFeatureModule.kt` — Koin конфигурация

## Related Documentation

- [DataStore and dependency injection | Android Developers](https://medium.com/androiddevelopers/datastore-and-dependency-injection-ea32b95704e3)
- [All about Preferences DataStore | Android Developers](https://medium.com/androiddevelopers/all-about-preferences-datastore-cc7995679334)
