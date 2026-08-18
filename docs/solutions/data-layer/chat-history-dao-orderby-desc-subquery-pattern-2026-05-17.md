---
title: "Room observeRecent — DESC ORDER Subquery Pattern"
date: 2026-05-17
type: bug-fix
modules: [feature/aichat/impl]
keywords: [Room, observeRecent, ORDER BY DESC, subquery, chat history, persistence, database]
project: checklists
---

# Room observeRecent — DESC ORDER Subquery Pattern

## Проблема / Контекст

При реализации observeRecent() для chat history, наивный query с `ORDER BY timestamp ASC LIMIT N` возвращает **N самых СТАРЫХ** сообщений вместо N самых свежих. Это особенно проблемно для Flow<List<T>>, потому что при повторной эмиссии (пользователь добавил новое сообщение) observeRecent() вновь запускается и снова возвращает древние записи.

**Симптом на пользователе:**
1. Откроешь ChatScreen → observeRecent(limit=20) эмитит 20 самых старых сообщений за всю историю
2. Добавишь новое сообщение → append в UI
3. observeRecent перезапускается (или Flow снова подписывается) → СНОВА эмитит 20 старых, перезаписывая новые
4. Ощущение что сообщение потеряется или persistence не работает

## Решение

Используй **subquery с DESC/ASC комбинацией** для получения N последних записей в хронологическом порядке:

```sql
-- ❌ НЕПРАВИЛЬНО (возвращает 20 самых СТАРЫХ):
SELECT * FROM chat_history 
WHERE checklist_id = ?1 
ORDER BY timestamp ASC 
LIMIT ?2

-- ✅ ПРАВИЛЬНО (возвращает 20 самых НОВЫХ, упорядочено по возрастанию):
SELECT * FROM (
  SELECT * FROM chat_history 
  WHERE checklist_id = ?1 
  ORDER BY timestamp DESC 
  LIMIT ?2
) 
ORDER BY timestamp ASC
```

**Room DAO реализация:**

```kotlin
@Dao
interface ChatHistoryDao {
  @Query("""
    SELECT * FROM (
      SELECT * FROM chat_history 
      WHERE checklist_id = :checklistId 
      ORDER BY timestamp DESC 
      LIMIT :limit
    ) 
    ORDER BY timestamp ASC
  """)
  fun observeRecent(checklistId: String, limit: Int = 20): Flow<List<ChatMessage>>
}
```

## Почему именно так

1. **DESC LIMIT берёт N самых свежих** — база ходит с конца (новые таймстампы больше), берёт N упорядочив по убыванию.

2. **Внешний ASC ORDER упорядочивает хронологически** — результат N свежих переупорядочивается по возрастанию, чтобы старые были слева (начало чата), новые справа (конец чата).

3. **Стандартный паттерн для chat/message logs** — этот pattern переиспользуется в любом message-ориентированном приложении (chat, feed, activity log, audit trail).

4. **Flow<List<T>> совместимость** — при повторной эмиссии Flow (например, при новой подписке) query корректно возвращает N последних, не древних.

## Примеры

```kotlin
// Вставка 5 сообщений с таймстампами 100, 200, 300, 400, 500
repository.addMessage(ChatMessage(checklistId="1", text="old", timestamp=100))
repository.addMessage(ChatMessage(checklistId="1", text="msg2", timestamp=200))
repository.addMessage(ChatMessage(checklistId="1", text="msg3", timestamp=300))
repository.addMessage(ChatMessage(checklistId="1", text="msg4", timestamp=400))
repository.addMessage(ChatMessage(checklistId="1", text="new", timestamp=500))

// observeRecent(checklistId="1", limit=3):
// ❌ НЕПРАВИЛЬНО: [100, 200, 300]  ← 3 самых старых
// ✅ ПРАВИЛЬНО:  [300, 400, 500]  ← 3 самых новых, в хронологическом порядке

// Визуально в UI (слева = старое, справа = новое):
//   [msg3] [msg4] [new]  ← правильный порядок
```

## Связанные файлы

- `feature/aichat/impl/data/ChatHistoryDao.kt` — DAO с observeRecent query
- `feature/aichat/impl/data/ChatHistoryRepository.kt` — repository которое использует DAO
- `feature/aichat/impl/ChatViewModel.kt` — ViewModel observing observeRecent() Flow

## Дополнительные паттерны (для эффективности)

Если данных много (>100K сообщений) и performance критична, рассмотри:

1. **Индекс на (checklist_id, timestamp DESC)** для ускорения query:
   ```kotlin
   @Entity(indices = [
     Index(value = ["checklist_id", "timestamp"], orders = [IndexOrder.ASC, IndexOrder.DESC])
   ])
   data class ChatMessage(...)
   ```

2. **Pagination вместо LIMIT на все сообщения:**
   ```kotlin
   @Query("""...""")
   fun observeRecent(checklistId: String, offset: Int, limit: Int): Flow<List<ChatMessage>>
   ```

3. **Кэш последних N в памяти** (например, StateFlow) с refresh по schedule, если фулл-scroll не требуется.
