# 🏠 Home Search Checklist

Приложение для упрощения поиска жилья. Создавайте чек-листы и не забывайте важные детали при просмотре квартир!

## 🎯 О проекте

Kotlin Multiplatform проект для Android и iOS, построенный на Compose Multiplatform.

### 📱 Текущий функционал

- ✅ **Онбординг** - экран приветствия при первом запуске
- ✅ **Главный экран** с нижней навигацией (2 таба)
- ✅ **Дебаг меню** - инструменты для разработки
- ✅ **Navigation Compose** - навигация между экранами
- ✅ **Material Design 3** - современный UI

### 📚 Документация

- [SETUP_NAVIGATION.md](SETUP_NAVIGATION.md) - Инструкция по навигации
- [NAVIGATION.md](NAVIGATION.md) - Подробная документация по структуре навигации
- [KOIN_SETUP.md](KOIN_SETUP.md) - Настройка и использование Koin DI

## 🏗️ Структура проекта

* [/composeApp](./composeApp/src) - общий код для всех платформ
  - [commonMain](./composeApp/src/commonMain/kotlin) - код для всех платформ
    - `screens/` - экраны приложения
    - `viewmodels/` - ViewModels для управления состоянием
    - `navigation/` - навигационные маршруты
    - `di/` - модули Koin для Dependency Injection
  - [androidMain](./composeApp/src/androidMain/kotlin) - Android-специфичный код
  - [iosMain](./composeApp/src/iosMain/kotlin) - iOS-специфичный код

* [/iosApp](./iosApp/iosApp) - iOS приложение и SwiftUI код

## 🚀 Запуск проекта

### Android

#### Из Android Studio:
1. Откройте проект в Android Studio
2. Выберите конфигурацию запуска для Android
3. Нажмите Run ▶️

#### Из терминала:
```bash
# macOS/Linux
./gradlew :composeApp:assembleDebug

# Windows
.\gradlew.bat :composeApp:assembleDebug
```

### iOS

#### Требования:
- macOS с установленным Xcode
- Xcode Command Line Tools:
  ```bash
  xcode-select --install
  ```

#### Запуск:
1. Откройте проект в Android Studio
2. Выберите конфигурацию запуска для iOS
3. Нажмите Run ▶️

Или откройте [/iosApp](./iosApp) в Xcode и запустите оттуда.

## 🛠️ Технологии

- **Kotlin Multiplatform** - кроссплатформенная разработка
- **Compose Multiplatform** - UI фреймворк
- **Material Design 3** - дизайн система
- **Navigation Compose** - навигация
- **Kotlin Serialization** - сериализация данных
- **Koin** - dependency injection (DI) для multiplatform

## 📖 Дополнительно

- [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Navigation Compose](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-navigation-routing.html)