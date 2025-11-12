# ViewModels

Эта папка содержит все ViewModels для управления состоянием экранов приложения.

## 📁 Структура

- **OnboardingViewModel.kt** - управление экраном онбординга
- **MainViewModel.kt** - управление главным экраном и навигацией по табам
- **DebugViewModel.kt** - управление дебаг меню
- **HomeTabViewModel.kt** - управление списком чек-листов
- **FutureTabViewModel.kt** - заглушка для будущего функционала

## 💡 Использование

Все ViewModels автоматически внедряются через Koin с помощью `koinViewModel()`:

```kotlin
@Composable
fun MyScreen(
    viewModel: MyViewModel = koinViewModel()
) {
    // Получение состояния
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    // Использование в UI
    Button(onClick = { viewModel.doSomething() }) {
        Text(state.someValue)
    }
}
```

## 🔧 Добавление новой ViewModel

1. Создайте класс ViewModel:
```kotlin
class MyNewViewModel : ViewModel() {
    private val _state = MutableStateFlow(MyState())
    val state: StateFlow<MyState> = _state.asStateFlow()
    
    fun doSomething() {
        // Ваша логика
    }
}
```

2. Зарегистрируйте в `di/AppModule.kt`:
```kotlin
val appModule = module {
    viewModelOf(::MyNewViewModel)
}
```

3. Используйте в экране:
```kotlin
@Composable
fun MyScreen(viewModel: MyNewViewModel = koinViewModel()) {
    // ...
}
```

## 📚 Подробнее

См. [KOIN_SETUP.md](../../../../../KOIN_SETUP.md) для полной документации по Koin.

