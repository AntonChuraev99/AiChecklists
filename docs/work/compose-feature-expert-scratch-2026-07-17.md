# clear_completed_items agent tool — scratch (2026-07-17)

CONTRACT: L3 agent emits `clear_completed_items` + optional `checklist_hint`. Client end-to-end.

## Files / status
- [ ] feature/checklist ChecklistRepository.kt — add `deleteCompletedItems(id): Int = 0` (default for fakes)
- [ ] feature/checklist ChecklistRepositoryImpl.kt — override real dual-write (fillDao+checklistDao, PENDING_UPLOAD, touch)
- [ ] feature/home ChecklistDetailViewModel.kt:1200 — call repo method (keep analytics + optimistic UI + overflow dismiss)
- [ ] feature/aichat/api ToolCall.kt — add ClearCompleted(checklistHint, checklistId=null)
- [ ] feature/aichat/impl AgentToolCallMapper.kt — `clear_completed_items` branch
- [ ] composeApp ToolCallDispatcherImpl.kt — dispatch branch + handleClearCompleted (AmbiguousMatch on >1 / no-target+≥2)
- [ ] feature/aichat/impl ChatViewModel.kt — 10 exhaustive whens + agent-loop picker + isDestructive
- [ ] feature/aichat/impl ToolCallPreviewRenderer.kt — render branch (chat_preview_clear_completed)
- [ ] core/designsystem strings.xml EN + values-ru — 3 strings
- [ ] ChatRoute.kt + App.kt — 2 messageKey map entries each (removed / no_completed)
- [ ] Tests: AgentToolCallMapperTest, ToolCallDispatcherImplTest (+fake override), ChatObjectRowsTest agent picker

## Keys
- chat_dispatch_completed_items_removed = "Removed %1$s completed items from %2$s."
- chat_dispatch_no_completed_items = "No completed items to remove in %1$s."
- chat_preview_clear_completed = "Clear completed items"

## STATUS: DONE — all applied, GREEN
- compileAndroidMain (all touched modules) + :composeApp:compileKotlinWasmJs GREEN
- :feature:aichat:impl:testAndroidHostTest GREEN (mapper x3, VM x2)
- :composeApp:testAndroidHostTest GREEN (dispatcher x7, ChatMessageKeyResolutionTest guard)
- :feature:checklist:testAndroidHostTest GREEN (repo x2)
- Smart-cast fix: local `val hint` in handleClearCompleted
- twoListRepo needed explicit items = emptyList()
