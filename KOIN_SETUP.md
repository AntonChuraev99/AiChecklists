# 🔧 Koin Multiplatform - Настройка и использование

## ✅ Что было добавлено

### 1. Зависимости
В проект добавлены следующие библиотеки Koin (версия 4.0.0):
- `koin-core` - ядро Koin
- `koin-compose` - интеграция с Compose
- `koin-compose-viewmodel` - поддержка ViewModels

### 2. ViewModels
Созданы ViewModels для всех экранов:

#### 📱 OnboardingViewModel
```kotlin
// Путь: viewmodels/OnboardingViewModel.kt
class OnboardingViewModel : ViewModel()
```
Управляет логикой экрана онбординга.

#### 📱 MainViewModel
```kotlin
// Путь: viewmodels/MainViewModel.kt
class MainViewModel : ViewModel()
```
Управляет состоянием главного экрана с навигацией и выбором табов.

#### 🐛 DebugViewModel
```kotlin
// Путь: viewmodels/DebugViewModel.kt
class DebugViewModel : ViewModel()
```
Управляет дебаг меню, включая диалоги и действия разработчика.

#### 🏠 HomeTabViewModel
```kotlin
// Путь: viewmodels/HomeTabViewModel.kt
class HomeTabViewModel : ViewModel()
```
Управляет списком чек-листов на главном табе.

#### ⭐ FutureTabViewModel
```kotlin
// Путь: viewmodels/FutureTabViewModel.kt
class FutureTabViewModel : ViewModel()
```
Заглушка для будущего функционала.

### 3. Koin модули
```kotlin
// Путь: di/AppModule.kt
val appModule = module {
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::MainViewModel)
    viewModelOf(::DebugViewModel)
    viewModelOf(::HomeTabViewModel)
    viewModelOf(::FutureTabViewModel)
}
```

### 4. Инициализация
В `App.kt` добавлен `KoinApplication`:
```kotlin
@Composable
fun App() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        // ... остальной код
    }
}
```

## 📚 Как использовать

### Получение ViewModel в Composable функции
```kotlin
@Composable
fun MyScreen(
    viewModel: MyViewModel = koinViewModel()
) {
    // Используйте ViewModel
    val state by viewModel.state.collectAsStateWithLifecycle()
}
```

### Добавление новой ViewModel

1. **Создайте ViewModel**:
```kotlin
// В папке viewmodels/
class MyNewViewModel : ViewModel() {
    private val _state = MutableStateFlow(MyState())
    val state: StateFlow<MyState> = _state.asStateFlow()
    
    fun doSomething() {
        // Ваша логика
    }
}
```

2. **Зарегистрируйте в Koin модуле**:
```kotlin
// В di/AppModule.kt
val appModule = module {
    // ... существующие ViewModels
    viewModelOf(::MyNewViewModel)
}
```

3. **Используйте в экране**:
```kotlin
@Composable
fun MyScreen(
    viewModel: MyNewViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // ...
}
```

## 🎯 Преимущества

- ✅ **Единая точка инициализации** - все зависимости в одном месте
- ✅ **Легкое тестирование** - ViewModels можно легко подменять моками
- ✅ **Разделение ответственности** - бизнес-логика отделена от UI
- ✅ **Управление жизненным циклом** - ViewModels автоматически переживают пересоздание экранов
- ✅ **Multiplatform** - работает одинаково на Android и iOS

## 📖 Дополнительная информация

- [Koin Documentation](https://insert-koin.io/)
- [Koin Compose](https://insert-koin.io/docs/reference/koin-compose/compose)
- [Koin Multiplatform](https://insert-koin.io/docs/reference/koin-mp/kmp)

