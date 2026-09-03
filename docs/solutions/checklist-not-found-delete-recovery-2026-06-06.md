---
title: "Delete Corrupted/Restored Checklists from NotFound Screen"
date: 2026-06-06
type: bug-fix
modules: [feature/home, core/designsystem]
keywords: [checklist-not-found, dead-end, soft-delete, syncstatus-pending-delete, corrupt-checklists, zombie-resurrection]
project: checklists-gisti
---

# Delete Corrupted/Restored Checklists from NotFound Screen

## Проблема / Контекст

Когда чеклист восстанавливается/повреждается и не открывается (failed to load, no default fill → `ChecklistDetailState.NotFound`), пользователь попадает на экран «Checklist not found» с сообщением об ошибке. Из этого состояния **нет способа удалить чеклист** — удаление-функция живёт ВНУТРИ обычного Content-экрана (ChecklistDetailScreen.delete → repository.deleteChecklist(checklist)), в который user не может попасть из-за NotFound.

Результат: dead-end состояние. Пользователь видит мусорный/разломанный чеклист в списке, не может открыть и не может удалить → требует ручного вмешательства (DB reset / support).

**Root causes:**
1. **Primary delete-flow требует loaded checklist.** ChecklistDetailScreen содержит действительный checklist через selectivelyLoaded state → deleteChecklist(checklist: Checklist) в repository требует checklist.id. State.NotFound поля checklist не имеет.
2. **Restored checklists поддерживают zombie-resurrection.** После мягкого удаления (soft-delete → syncStatus=PENDING_DELETE → tombstone в Firestore), если пользователь переустановил приложение или произошла race-condition, syncStatus могла остаться SYNCED (и ещё не синхронизирована удаления). При следующем pullAndMerge reconcileDeletedRemotely() скачивает запись обратно (статус=SYNCED значит она синхронизирована). Вот откуда появляются восстановленные чеклисты. Если raw DAO.delete() используется вместо soft-delete → PENDING_DELETE не установлен → reconciliation полностью её пропускает → чеклист гарантированно resurrects.

## Решение

### 1. Новый Intent для corrupted checklists

В `ChecklistDetailScreenContract.kt`:
```kotlin
sealed class ChecklistDetailIntent {
    data class OnDeleteCorruptedChecklist(val checklistId: String) : ChecklistDetailIntent()
    // ... остальные intents
}
```

### 2. ViewModel handler с доступом к checklistId из конструктора

В `ChecklistDetailViewModel.kt`:
```kotlin
private fun deleteCorruptedChecklist(checklistId: String) {
    launchAsyncTask {
        // Fetch checklist, or proceed even if null (orphaned fill case)
        val checklist = repository.getChecklistById(checklistId)
        
        // Cancel any reminders
        checklist?.let { 
            reminderRepository.getRemindersByChecklistId(it.id).forEach { reminder ->
                reminderRepository.deleteReminder(reminder.id)
            }
        }
        
        // Soft-delete: sets syncStatus=PENDING_DELETE
        checklist?.let { 
            repository.deleteChecklist(it)
            analytics.trackEvent("checklist_deleted", mapOf("source" to "not_found_screen"))
        } ?: run {
            // Orphaned fill — already-deleted checklist
            analytics.trackEvent("checklist_deleted", mapOf(
                "source" to "not_found_screen",
                "template_found" to false
            ))
        }
        
        // Navigate back regardless (success or orphaned)
        emitSideEffect(ChecklistDetailSideEffect.NavigateBack)
    }
}

override fun onIntent(intent: ChecklistDetailIntent) {
    when (intent) {
        is ChecklistDetailIntent.OnDeleteCorruptedChecklist -> 
            deleteCorruptedChecklist(intent.checklistId)
        // ... other intents
    }
}
```

### 3. NotFound UI с delete-кнопкой и confirm-диалогом

В `ChecklistDetailScreen.kt`, секция `ChecklistDetailContent.NotFound`:
```kotlin
@Composable
private fun NotFoundContent(
    checklistId: String,
    onSendIntent: (ChecklistDetailIntent) -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = GistiTheme.colors.error,
            modifier = Modifier.size(48.dp),
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.checklist_not_found),
            style = GistiTheme.typography.bodyLarge,
            color = GistiTheme.colors.onBackground,
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.checklist_not_found_description),
            style = GistiTheme.typography.bodyMedium,
            color = GistiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        AppButtonDestructive(
            text = stringResource(R.string.checklist_not_found_delete),
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.checklist_not_found_delete_title)) },
            text = { Text(stringResource(R.string.checklist_not_found_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSendIntent(ChecklistDetailIntent.OnDeleteCorruptedChecklist(checklistId))
                        showDeleteConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
```

### 4. Strings (EN + RU)

`core/designsystem/.../values/strings.xml`:
```xml
<string name="checklist_not_found_description">This checklist could not be loaded. It may have been corrupted or deleted. You can safely remove it.</string>
<string name="checklist_not_found_delete_message">Delete this checklist? This action cannot be undone.</string>
```

`core/designsystem/.../values-ru/strings.xml`:
```xml
<string name="checklist_not_found_description">Чеклист не удалось загрузить. Он может быть повреждён или удалён. Вы можете его безопасно удалить.</string>
<string name="checklist_not_found_delete_message">Удалить этот чеклист? Это действие не может быть отменено.</string>
```

### 5. Test

`ChecklistDetailViewModelTest.kt`:
```kotlin
@Test
fun `onIntent DeleteCorruptedChecklist calls repository and navigates back`() = runTest {
    val checklistId = "test-checklist-id"
    val intent = ChecklistDetailIntent.OnDeleteCorruptedChecklist(checklistId)
    
    viewModel.onIntent(intent)
    
    verify(repository).getChecklistById(checklistId)
    verify(repository).deleteChecklist(any())
    verify(navigator).onBack()
    
    assertTrue(viewModel.sideEffect.value is ChecklistDetailSideEffect.NavigateBack)
}
```

## Почему именно так

### Переиспользование существующего soft-delete-пути

`repository.deleteChecklist(checklist)` требует только `.id` из параметра. Он:
1. Вызывает `checklistDao.softDelete(checklist.id)` → устанавливает `syncStatus = PENDING_DELETE`
2. На следующем sync-цикле tombstone улетает в Firestore
3. На следующем `pullAndMerge`, `reconcileDeletedRemotely()` видит запись с SYNCED-status и cloudId, который отсутствует в облаке → **hard-delete локально**

**Контраст — raw DAO.delete():**
```kotlin
// ❌ WRONG: leaves syncStatus=SYNCED
checklistDao.delete(checklistId)

// На следующем sync:
// - reconciliation: видит syncStatus=SYNCED → пропускает (уже синхронизировано)
// - cloudRepository.pullRemoteChecklist() может вернуть запись
// - чеклист resurrects
```

Мягкое удаление = правильный слой для cross-platform sync.

### Dead-end intent + constructor checklistId

ChecklistDetailState.NotFound не имеет checklist-поля. Вместо того, чтобы строить synthetic Checklist или менять contract, используем:
- Новый intent `OnDeleteCorruptedChecklist` с явным checklistId
- ViewModel имеет доступ к checklistId через конструктор (параметр маршрута)
- Никаких fake-implementations в тестах (getChecklistById прозрачен)

### Confirm-диалог — local Composable state

State.NotFound не имеет полей. Добавлять `showConfirmDelete: Boolean` в контракт = boilerplate для одного действия. Предпочтение: `remember { mutableStateOf(showDeleteConfirm) }` внутри NotFoundContent. Delete-action сам проходит через intent → async-safe.

### Edge case: orphaned fill (checklist==null)

Если шаблон удалён после того как fill был создан, getChecklistById вернёт null. Не разбухивать ViewModel логированием (это добавляет dependencies и cascading test updates). Вместо этого:
- `analytics.trackEvent()` с `template_found=false` → видимость в Amplitude
- Unconditional `onBack()` → уходим из dead-end состояния
- Zero noise в логах

## Связанные файлы

- `feature/home/src/commonMain/.../ChecklistDetailScreenContract.kt` — Intent добавлен
- `feature/home/src/commonMain/.../ChecklistDetailViewModel.kt` — handler добавлен
- `feature/home/src/commonMain/.../ChecklistDetailScreen.kt` — UI + confirm-диалог
- `core/designsystem/src/commonMain/composeResources/values/strings.xml` — EN strings
- `core/designsystem/src/commonMain/composeResources/values-ru/strings.xml` — RU strings
- `feature/home/src/commonTest/.../ChecklistDetailViewModelTest.kt` — test покрывает handler

**Related patterns:**
- Soft-delete flow: `feature/checklist/.../SyncRepository.reconcileDeletedRemotely()`
- Intent/SideEffect routing: `app-root-layer-kmp-migration-2026-04-15.md`
- Checklist domain: `.claude/rules/checklist-domain.md`
