---
title: MVI (Model-View-Intent) Pattern Implementation
category: Architecture
description: Complete guide to the MVI pattern used throughout the Gisti codebase, including AppViewModel base class, ScreenContract definitions, and state management patterns.
tags:
  - mvi
  - architecture
  - viewmodel
  - state-management
  - kotlin
created_at: 2026-01-25
---

# MVI (Model-View-Intent) Pattern Implementation

## Overview

The Gisti project implements the **MVI (Model-View-Intent) pattern**, a unidirectional data flow architecture that ensures predictable state management and testability. Every screen in the application follows this pattern consistently.

### Pattern Components

The MVI pattern consists of three core components:

1. **Model (State)** - Immutable data representing the UI state
2. **View (Screen)** - Composable that renders the UI based on state
3. **Intent** - User actions/events that trigger state changes

Additionally, the pattern supports:

4. **SideEffect** - One-time events (navigation, dialogs, snackbars) - currently unused but available

## Architecture Layers

```
┌─────────────────────────────────┐
│         View (Screen)           │  ← Composable UI
│  Observes state, sends intents  │
└─────────────┬───────────────────┘
              │
              ↓ sendIntent(intent)
        ┌──────────────┐
        │  ViewModel   │
        │ (AppViewModel)
        └──────────────┘
              ↑
              │ screenState.collect()
              │
        ┌──────────────┐
        │    State     │
        │  (StateFlow) │
        └──────────────┘
```

## Core Components

### 1. AppViewModel Base Class

Located in `core/common/api/AppViewModel.kt`, this abstract base class provides the foundation for all ViewModels:

```kotlin
abstract class AppViewModel<S : State, I : Intent, SE : SideEffect> : ViewModel() {

    abstract val screenState: StateFlow<S>

    private val _intent: MutableSharedFlow<I> = MutableSharedFlow(extraBufferCapacity = 64)

    init {
        viewModelScope.launch {
            _intent.collect {
                onIntent(it)
            }
        }
    }

    abstract fun onIntent(intent: I)

    final fun sendIntent(intent: I) {
        _intent.tryEmit(intent)
    }

    fun Flow<S>.defaultStateIn(initial: S): StateFlow<S> = stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initial
    )
}

interface State
interface Intent
interface SideEffect
```

### Key Features

| Feature | Purpose |
|---------|---------|
| **Generic Type Parameters** | `<S : State, I : Intent, SE : SideEffect>` - Type-safe pattern enforcement |
| **screenState** | Abstract property for UI state exposure |
| **_intent** | Private buffer for intent processing (64-item capacity) |
| **sendIntent()** | Final method for views to dispatch intents |
| **onIntent()** | Abstract method for handling intents (implemented per ViewModel) |
| **defaultStateIn()** | Helper to create StateFlow with WhileSubscribed sharing |

### Intent Processing Flow

1. View calls `viewModel.sendIntent(intent)`
2. Intent emitted to `_intent` MutableSharedFlow
3. Collected in `init` block and dispatched to `onIntent()`
4. ViewModel updates `screenState` StateFlow
5. View collects new state and recomposes

## ScreenContract Pattern

Each screen defines its contract in a dedicated `*ScreenContract.kt` file containing:

- **State interface/data class** - Sealed interface (for multiple variants) or data class (single variant)
- **Intent sealed interface** - All possible user actions
- **SideEffect sealed interface** (optional) - One-time events

### Example 1: Simple Contract (Data Class State)

**CreateChecklistScreenContract.kt:**

```kotlin
data class CreateChecklistState(
    val name: String = "",
    val items: List<ChecklistItem> = emptyList(),
    val nameError: String? = null,
    val isEditMode: Boolean = false,
    val editChecklistId: Long? = null
) : State

sealed interface CreateChecklistIntent : Intent {
    data object OnBackClick : CreateChecklistIntent
    data object OnSaveClick : CreateChecklistIntent
    data class OnNameChange(val name: String) : CreateChecklistIntent
    data class OnAddItem(val itemText: String) : CreateChecklistIntent
    data class OnDeleteItem(val item: ChecklistItem) : CreateChecklistIntent
}
```

**When to use data class state:**
- Single state variant
- All properties optional with defaults
- Simple state structure

### Example 2: Complex Contract (Sealed Interface State)

**MainScreenContract.kt:**

```kotlin
sealed interface MainScreenState : State {
    data object Loading : MainScreenState
    data class Success(
        val checklists: List<Checklist>,
        val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE,
        val formattedExpirationDate: String? = null,
        val aiCredits: Int = 0,
        val userLimits: UserLimits? = null,
        val showLimitReachedDialog: Boolean = false
    ) : MainScreenState
}

sealed interface MainScreenIntent : Intent {
    data object OnAddChecklistClick : MainScreenIntent
    data object OnAiAnalyzeClick : MainScreenIntent
    data class OnChecklistClick(val checklist: Checklist) : MainScreenIntent
    data object OnPremiumBannerClick : MainScreenIntent
    data object OnCreditsClick : MainScreenIntent
    data object OnDismissLimitDialog : MainScreenIntent
    data object OnUpgradeToPremiumClick : MainScreenIntent
}
```

**When to use sealed interface state:**
- Multiple state variants (Loading, Success, Error, etc.)
- Type-safe variant handling with `when` expressions
- Different data per state variant

### Intent Naming Convention

Intents should describe **user actions**, not state changes:

✅ **Correct:**
- `OnAddChecklistClick` - what the user did
- `OnNameChange` - user input action
- `OnSaveClick` - button press

❌ **Incorrect:**
- `SetName` - sounds like a setter
- `UpdateLoading` - sounds like state manipulation
- `FetchData` - internal operation, not user action

## ViewModel Implementation

### Pattern Structure

Every ViewModel extends `AppViewModel` and implements three main parts:

```kotlin
class MyViewModel(
    private val repository: Repository,
    private val navigator: AppNavigator
) : AppViewModel<MyState, MyIntent, Nothing>() {

    // 1. State Management
    private val _screenState = MutableStateFlow(MyState())
    override val screenState: StateFlow<MyState> = _screenState.asStateFlow()

    // 2. Initialization
    init {
        // Load data if needed
    }

    // 3. Intent Handling
    override fun onIntent(intent: MyIntent) {
        when (intent) {
            // Handle each intent
        }
    }
}
```

### Example: CreateChecklistViewModel

**Simple state mutations:**

```kotlin
class CreateChecklistViewModel(
    private val editChecklistId: Long?,
    private val checklistRepository: ChecklistRepository,
    private val appNavigator: AppNavigator
) : AppViewModel<CreateChecklistState, CreateChecklistIntent, Nothing>() {

    private val _screenState = MutableStateFlow(CreateChecklistState(
        isEditMode = editChecklistId != null,
        editChecklistId = editChecklistId
    ))
    override val screenState: StateFlow<CreateChecklistState> = _screenState.asStateFlow()

    init {
        if (editChecklistId != null) {
            loadChecklist(editChecklistId)
        }
    }

    override fun onIntent(intent: CreateChecklistIntent) {
        when (intent) {
            CreateChecklistIntent.OnBackClick -> appNavigator.onBack()
            CreateChecklistIntent.OnSaveClick -> onSaveClick()
            is CreateChecklistIntent.OnNameChange -> _screenState.update {
                it.copy(name = intent.name, nameError = null)
            }
            is CreateChecklistIntent.OnAddItem -> _screenState.update {
                it.copy(items = it.items + ChecklistItem(intent.itemText, false))
            }
            is CreateChecklistIntent.OnDeleteItem -> _screenState.update {
                it.copy(items = it.items - intent.item)
            }
        }
    }

    private fun onSaveClick() {
        val currentState = _screenState.value

        // Validate state
        if (currentState.name.isBlank()) {
            _screenState.update { it.copy(nameError = "Enter checklist name") }
            return
        }

        // Async operation
        viewModelScope.launch {
            if (currentState.isEditMode && currentState.editChecklistId != null) {
                checklistRepository.updateChecklist(...)
                appNavigator.onBack()
            } else {
                checklistRepository.addChecklist(...)
                appNavigator.navigateToMainScreen(clearBackStack = true)
            }
        }
    }
}
```

**Key patterns:**
- Use `_screenState.update { it.copy(...) }` for immutable state updates
- Use `_screenState.value` to read current state for validation
- Always use `viewModelScope.launch` for async operations
- Handle navigation in intent handlers, not in side effects

### Example: MainScreenViewModel with Combined Flows

**Complex state composition from multiple sources:**

```kotlin
class MainScreenViewModel(
    private val repository: ChecklistRepository,
    private val appNavigator: AppNavigator,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase,
    private val userDataRepository: UserDataRepository,
    private val getUserLimitsUseCase: GetUserLimitsUseCase,
) : AppViewModel<MainScreenState, MainScreenIntent, Nothing>() {

    private val _showLimitDialog = MutableStateFlow(false)

    // Combine multiple flows into single state
    override val screenState: StateFlow<MainScreenState> = combine(
        repository.checklists,
        getSubscriptionStatusUseCase(),
        userDataRepository.getUserDataFlow().map { it.aiCredits },
        getUserLimitsUseCase(),
        _showLimitDialog
    ) { checklists, subscriptionStatus, aiCredits, userLimits, showLimitDialog ->
        MainScreenState.Success(
            checklists = checklists,
            subscriptionStatus = subscriptionStatus,
            formattedExpirationDate = subscriptionStatus.expirationDate?.let {
                formatExpirationDate(it)
            },
            aiCredits = aiCredits,
            userLimits = userLimits,
            showLimitReachedDialog = showLimitDialog
        )
    }.defaultStateIn(MainScreenState.Loading)

    override fun onIntent(intent: MainScreenIntent) {
        when (intent) {
            MainScreenIntent.OnAddChecklistClick -> handleAddChecklistClick()
            MainScreenIntent.OnAddChecklistFromTemplatesClick ->
                handleAddChecklistFromTemplatesClick()
            MainScreenIntent.OnAiAnalyzeClick -> appNavigator.navigateToAnalyzeScreen()
            is MainScreenIntent.OnChecklistClick ->
                appNavigator.navigateToChecklistDetail(intent.checklist.id)
            MainScreenIntent.OnPremiumBannerClick -> handlePremiumOrCreditsClick()
            MainScreenIntent.OnCreditsClick -> handlePremiumOrCreditsClick()
            MainScreenIntent.OnDismissLimitDialog -> _showLimitDialog.update { false }
            MainScreenIntent.OnUpgradeToPremiumClick -> {
                _showLimitDialog.update { false }
                appNavigator.navigateToPaywall()
            }
        }
    }

    private fun handleAddChecklistClick() {
        val currentState = screenState.value
        if (currentState is MainScreenState.Success) {
            val limits = currentState.userLimits
            if (limits != null && !limits.canCreateChecklist) {
                appNavigator.navigateToPaywall()
            } else {
                appNavigator.navigateToTemplatesScreen()
            }
        }
    }
}
```

**Key patterns:**
- Use `combine()` to merge multiple data sources
- Apply `.defaultStateIn()` to create StateFlow with proper lifecycle handling
- Start with `Loading` state while data loads
- Access current state in intent handlers with `screenState.value`

### Example: AnalyzeViewModel with Async Results

**Complex async operations with result handling:**

```kotlin
private fun analyzeInput() {
    val state = _screenState.value
    val inputData = buildInputData(state) ?: run {
        _screenState.update { it.copy(error = "Please provide input") }
        return
    }

    viewModelScope.launch {
        _screenState.update { it.copy(isAnalyzing = true, error = null) }

        analyzeRepository.analyzeData(inputData, targetChecklist)
            .onSuccess { result ->
                _screenState.update {
                    it.copy(
                        isAnalyzing = false,
                        analyzeResult = result
                    )
                }
                // Navigate only after state updated
                appNavigator.navigateToAnalyzeResultPreview()
            }
            .onFailure { error ->
                _screenState.update {
                    it.copy(
                        isAnalyzing = false,
                        error = error.message ?: "Analysis failed"
                    )
                }
            }
    }
}
```

**Key patterns:**
- Always set loading state before async call
- Clear loading state in both success and failure cases
- Navigate after state update for better UX
- Use Result type for error handling (`.onSuccess/.onFailure`)

## View/Screen Integration

### Using the ViewModel in Composable

```kotlin
@Composable
fun CreateChecklistScreen(
    viewModel: CreateChecklistViewModel = koinViewModel(),
) {
    // Collect state with lifecycle awareness
    val screenState: CreateChecklistState by viewModel.screenState
        .collectAsStateWithLifecycle()

    // Render based on current state
    Column(modifier = Modifier.fillMaxSize()) {
        // UI elements
        AppTextField(
            value = screenState.name,
            onValueChange = { newName ->
                viewModel.sendIntent(
                    CreateChecklistIntent.OnNameChange(newName)
                )
            }
        )

        // Show error if present
        if (screenState.nameError != null) {
            Text(screenState.nameError!!, color = Color.Red)
        }

        // List items
        LazyColumn {
            items(screenState.items) { item ->
                ItemRow(
                    item = item,
                    onDelete = {
                        viewModel.sendIntent(
                            CreateChecklistIntent.OnDeleteItem(item)
                        )
                    }
                )
            }
        }

        // Save button
        AppButton(
            text = "Save",
            onClick = {
                viewModel.sendIntent(CreateChecklistIntent.OnSaveClick)
            }
        )
    }
}
```

### Key Integration Points

| Element | Method |
|---------|--------|
| **Get ViewModel** | `koinViewModel()` - Koin DI |
| **Observe state** | `viewModel.screenState.collectAsStateWithLifecycle()` |
| **Send intent** | `viewModel.sendIntent(intent)` |
| **State safety** | Type-safe with sealed interfaces and data classes |

## State Management Patterns

### 1. Simple State Update

```kotlin
_screenState.update { currentState ->
    currentState.copy(name = "New Name")
}
```

### 2. Conditional Update

```kotlin
_screenState.update { currentState ->
    if (someCondition) {
        currentState.copy(showDialog = true)
    } else {
        currentState
    }
}
```

### 3. List Mutation

```kotlin
// Add item
_screenState.update {
    it.copy(items = it.items + newItem)
}

// Remove item
_screenState.update {
    it.copy(items = it.items - itemToRemove)
}

// Update specific item
_screenState.update {
    it.copy(items = it.items.map { item ->
        if (item.id == targetId) item.copy(checked = true) else item
    })
}
```

### 4. Reading Current State for Validation

```kotlin
override fun onIntent(intent: MyIntent) {
    when (intent) {
        is MyIntent.SaveClick -> {
            val currentState = _screenState.value

            // Validate before proceeding
            if (currentState.name.isBlank()) {
                _screenState.update {
                    it.copy(error = "Name is required")
                }
                return
            }

            // Proceed with operation
            saveData(currentState)
        }
    }
}
```

## Common Patterns & Best Practices

### ✅ DO

- **Name intents after user actions**: `OnDeleteClick`, `OnNameChange`
- **Keep state immutable**: Always use `.copy()` or create new instances
- **Handle errors in state**: Store error messages in state for UI display
- **Use sealed interfaces for multiple state variants**: Enables exhaustive when
- **Combine flows for derived state**: Use `combine()` to merge repositories
- **Call navigation after state update**: Ensures consistent state during navigation
- **Use `viewModelScope.launch`**: Proper lifecycle handling
- **Validate state before operations**: Read `_screenState.value` for validation

### ❌ DON'T

- **Don't use callback-based side effects**: Avoid Flow<SideEffect>, use state instead
- **Don't mutate state directly**: Never do `screenState.value.name = "x"`
- **Don't navigate before state update**: State should reflect intended action
- **Don't expose MutableStateFlow**: Always wrap with `asStateFlow()`
- **Don't perform IO in intent handlers without viewModelScope**: Can cause memory leaks
- **Don't use `GlobalScope`**: Always use `viewModelScope`
- **Don't name intents like setters**: Bad: `SetName`, Good: `OnNameChange`

## Architecture Decision: No SideEffects (Currently)

The project currently uses `Nothing` as the SideEffect type parameter:

```kotlin
class MyViewModel : AppViewModel<MyState, MyIntent, Nothing>() {
    // ...
}
```

**Why:**
- Navigation handled via `AppNavigator` in intent handlers
- UI events (dialogs, snackbars) stored in state
- One-time events not needed in current design
- Simpler state management without extra event flow

**If SideEffects become needed:**
- Define sealed interface: `sealed interface MySideEffect : SideEffect`
- Expose StateFlow: `val sideEffects: SharedFlow<MySideEffect>`
- Emit in intent handlers: `_sideEffects.emit(SideEffect.ShowSnackbar(...))`

## Testing Considerations

The MVI pattern supports easy testing:

```kotlin
@Test
fun testNameValidation() {
    val viewModel = CreateChecklistViewModel(...)

    // Send intent
    viewModel.sendIntent(CreateChecklistIntent.OnSaveClick)

    // Verify state changed
    val state = viewModel.screenState.value
    assertThat(state.nameError).isNotNull()
    assertThat(state.nameError).contains("required")
}

@Test
fun testAddItem() {
    val viewModel = CreateChecklistViewModel(...)

    // Initial state
    assertThat(viewModel.screenState.value.items).isEmpty()

    // Send intent
    viewModel.sendIntent(
        CreateChecklistIntent.OnAddItem("New Item")
    )

    // Verify state
    val state = viewModel.screenState.value
    assertThat(state.items).hasSize(1)
    assertThat(state.items[0].text).isEqualTo("New Item")
}
```

## Benefits of MVI Pattern

| Benefit | Description |
|---------|-------------|
| **Unidirectional Flow** | Clear data flow: View → Intent → ViewModel → State → View |
| **Predictability** | Same intent always produces same state change |
| **Testability** | Pure functions, no side effects in state management |
| **Time Travel** | Can replay intents to reproduce bugs |
| **Type Safety** | Sealed interfaces and data classes enforce correctness |
| **Reusability** | ViewModels independent of Compose framework |
| **Maintainability** | Consistent pattern across entire codebase |
| **Debugging** | Clear state transitions, easy to inspect |

## Migration Guide: Adding New Screen

### Step 1: Create ScreenContract

`feature/myfeature/src/commonMain/kotlin/.../MyScreenContract.kt`:

```kotlin
data class MyScreenState(
    val isLoading: Boolean = false,
    val data: List<Item> = emptyList(),
    val error: String? = null
) : State

sealed interface MyScreenIntent : Intent {
    data object OnLoad : MyScreenIntent
    data object OnRetry : MyScreenIntent
    data class OnItemClick(val id: Long) : MyScreenIntent
}
```

### Step 2: Create ViewModel

`feature/myfeature/src/commonMain/kotlin/.../MyViewModel.kt`:

```kotlin
class MyViewModel(
    private val repository: MyRepository,
    private val navigator: AppNavigator
) : AppViewModel<MyScreenState, MyScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(MyScreenState())
    override val screenState: StateFlow<MyScreenState> = _screenState.asStateFlow()

    init {
        loadData()
    }

    override fun onIntent(intent: MyScreenIntent) {
        when (intent) {
            MyScreenIntent.OnLoad -> loadData()
            MyScreenIntent.OnRetry -> loadData()
            is MyScreenIntent.OnItemClick -> navigator.navigateToDetail(intent.id)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _screenState.update { it.copy(isLoading = true, error = null) }

            repository.loadData()
                .onSuccess { data ->
                    _screenState.update {
                        it.copy(isLoading = false, data = data)
                    }
                }
                .onFailure { error ->
                    _screenState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                }
        }
    }
}
```

### Step 3: Create Screen Composable

`feature/myfeature/src/commonMain/kotlin/.../MyScreen.kt`:

```kotlin
@Composable
fun MyScreen(
    viewModel: MyViewModel = koinViewModel()
) {
    val screenState: MyScreenState by viewModel.screenState
        .collectAsStateWithLifecycle()

    when (screenState) {
        is MyScreenState -> {
            if (screenState.isLoading) {
                LoadingIndicator()
            } else if (screenState.error != null) {
                ErrorState(
                    message = screenState.error,
                    onRetry = {
                        viewModel.sendIntent(MyScreenIntent.OnRetry)
                    }
                )
            } else {
                ItemList(
                    items = screenState.data,
                    onItemClick = { id ->
                        viewModel.sendIntent(MyScreenIntent.OnItemClick(id))
                    }
                )
            }
        }
    }
}
```

### Step 4: Register ViewModel in Koin

`feature/myfeature/src/commonMain/kotlin/.../di/MyFeatureModule.kt`:

```kotlin
val myFeatureModule = module {
    viewModel { MyViewModel(get(), get()) }
}
```

## File Structure Reference

Standard locations for MVI pattern files:

```
feature/myfeature/
├── src/commonMain/kotlin/...
│   └── presentation/
│       ├── MyScreenContract.kt        ← State, Intent definitions
│       ├── MyScreen.kt                ← Composable View
│       ├── MyViewModel.kt             ← ViewModel implementation
│       └── di/
│           └── MyFeatureModule.kt     ← Koin registration
```

## See Also

- **Core Module**: `core/common/api/AppViewModel.kt`
- **Example Implementations**:
  - MainScreenViewModel (complex combined state)
  - CreateChecklistViewModel (simple mutable state)
  - AnalyzeViewModel (async operations)
  - PaywallViewModel (result handling)
- **Navigation Integration**: `core/navigation/api/AppNavigator.kt`
- **Koin DI Setup**: `appModule` in main application
