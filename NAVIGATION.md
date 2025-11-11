# Структура навигации приложения

## Библиотека Navigation Compose

Приложение использует **Navigation Compose** для Compose Multiplatform - библиотеку для навигации, работающую на Android и iOS.

### Добавленные зависимости

- `org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10`
- `org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1`
- Material Icons Extended (встроено в Compose Multiplatform)

## Структура экранов

### 1. Онбординг (`OnboardingScreen`)
**Путь**: `NavRoute.Onboarding`

Экран приветствия, который показывается при первом запуске приложения.

**Особенности**:
- Простой UI с описанием приложения
- Кнопка "Начать" для перехода на главный экран
- После завершения удаляется из навигационного стека

**Файл**: `screens/OnboardingScreen.kt`

---

### 2. Главный экран (`MainScreen`)
**Путь**: `NavRoute.Main`

Основной экран приложения с нижней навигацией.

**Компоненты**:
- Top App Bar с кнопкой перехода в дебаг меню
- Bottom Navigation Bar с двумя табами
- Контейнер для отображения содержимого выбранного таба

**Табы нижней навигации**:

#### 2.1. Главная (`HomeTabScreen`)
- Список чек-листов (пока заглушка)
- Кнопка создания нового чек-листа
- Пустой state с информативным сообщением

**Файл**: `screens/tabs/HomeTabScreen.kt`

#### 2.2. Будущее (`FutureTabScreen`)
- Заготовка для будущего функционала
- Информационное сообщение о разработке

**Файл**: `screens/tabs/FutureTabScreen.kt`

**Файл**: `screens/MainScreen.kt`

---

### 3. Дебаг меню (`DebugScreen`)
**Путь**: `NavRoute.Debug`

Экран с инструментами для разработки и тестирования.

**Функции**:
- Информация о приложении (версия, билд)
- Сброс онбординга
- Очистка данных
- Создание тестовых данных

**Особенности**:
- Доступен через иконку настроек в главном экране
- Кнопка "Назад" для возврата

**Файл**: `screens/DebugScreen.kt`

---

## Навигационные маршруты

**Файл**: `navigation/NavRoutes.kt`

```kotlin
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Main : Screen("main")
    data object Debug : Screen("debug")
}

enum class MainTab {
    HOME,
    FUTURE
}
```

### Строковые маршруты

Используются строковые маршруты для навигации, которые работают на всех платформах Compose Multiplatform.

---

## Граф навигации

```
Onboarding (стартовый экран)
    ↓
Main (главный экран)
    ├── Home Tab (главная вкладка)
    ├── Future Tab (будущая вкладка)
    └── → Debug (дебаг меню)
         └── ← Back to Main
```

---

## Настройка в App.kt

```kotlin
NavHost(
    navController = navController,
    startDestination = Screen.Onboarding.route
) {
    composable(Screen.Onboarding.route) { OnboardingScreen(...) }
    composable(Screen.Main.route) { MainScreen(...) }
    composable(Screen.Debug.route) { DebugScreen(...) }
}
```

---

## Особенности реализации

### 1. Управление back stack
- Онбординг удаляется из стека после завершения
- Дебаг меню открывается поверх главного экрана

### 2. Material Design 3
- Использование современных компонентов MD3
- Адаптивные layout для разных размеров экранов

### 3. State management
- Локальное состояние для нижней навигации
- Централизованное управление через NavController

---

## Следующие шаги

- [ ] Добавить сохранение состояния онбординга
- [ ] Реализовать экран создания чек-листа
- [ ] Добавить список чек-листов на главном экране
- [ ] Реализовать функции дебаг меню
- [ ] Добавить анимации переходов между экранами

