# Google Auth Firestore Sync — Cross-Platform Auto-Sync

**Статус:** Done
**Дата старта:** 2026-05-24
**Start SHA:** c179c6f1
**Project:** gisti-checklists
**Тип:** architecture
**Сложность:** Complex
**Impact:** High
**Затронутые модули:** feature:checklist (entities, dao, repository, sync), feature:user (auth), core:datastore, wasmJs (init.js, FirestoreSyncDataSource), Android (FirestoreSyncDataSource), iOS (stub)

⚠ INIT phase was skipped — minimal active doc reconstructed during COMPLETE. Counters могут быть неточными.

## Цель (продуктовая)
Синхронизация чеклистов между мобильным/веб-приложением и Firestore через аккаунт Google. После логина пользователь видит единый синхронизированный список чеклистов со всех устройств, офлайн-изменения отправляются на сервер по восстановлении соединения.

## Технический план
1. Room migration: добавить 5 sync-полей в ChecklistEntity и ChecklistFillEntity (cloudId, userId, updatedAt, syncStatus, isDeleted)
2. Модель SyncStatus (enum: SYNCED, PENDING, CONFLICT, FAILED) и Firestore document structure
3. FirestoreSyncDataSource interface + platform-specific реализации (Android/wasmJs/iOS)
4. Android: Firebase SDK + callbackFlow для real-time listeners
5. wasmJs: globalThis bridge + Firestore JS SDK + polling fallback
6. LWW (Last-Write-Wins) conflict resolution на основе updatedAt timestamp
7. ChecklistRepositoryImpl: combine Room + Firestore flows, bidirectional sync
8. UI: MainScreen/MainScreenViewModel для sync status отображения
9. iOS: stub-реализация с локальной Room БД (no cloud sync until platform strategy determined)
10. Deployment: firebase.json + firestore.rules (LWW rules), COOP/COEP removal для SAH Pool VFS

## Лог итераций
### Итерация 1–8 — 2026-05-24 — main-agent (no specialist trace)
**Что сделано:** Полная реализация кросс-платформной синхронизации чеклистов с Firestore:
- Room entities: ChecklistEntity и ChecklistFillEntity дополнены sync-полями (cloudId, userId, updatedAt, syncStatus, isDeleted)
- Migration v14→v15 для обеих сущностей
- FirestoreSyncDataSource interface (abstract getChecklists/updateChecklist/deleteChecklist)
- Android impl: Firebase SDK callbackFlow + real-time listeners на collection snapshot
- wasmJs impl: JS SDK bridge через globalThis + JSON.parse fix для batchWrite data-field + polling 30s fallback
- iOS impl: stub с localized only-mode
- LWW conflict resolution в ChecklistRepositoryImpl.combine() (updatedAt timestamp comparison)
- Bidirectional sync: real-time на Room changes + push pending + restore on connectivity
- UI: MainScreenViewModel sync-state flow + MainScreen sync-status chip/indicator
- Firestore rules: deny-by-default, auth-required, LWW timestamp validation
- Webpack fix: COOP/COEP headers removed (SAH Pool VFS не требует SharedArrayBuffer)
- init.js: globalThis.Firestore + batchWrite JSON bridge

**Почему так:** Синхронизация через Firestore требует 3-платформного подхода из-за разных API (Android Firebase SDK, wasmJs JS SDK, iOS UIKit). LWW chosen для простоты и offline-first поддержки (timestamped writes без конфликтов). Real-time + polling обеспечивает резервный путь. Двусторонность (Room→Firestore и Firestore→Room) при offline позволяет пользователю видеть изменения сразу на любом устройстве.

**Баги/проблемы:**
1. Firestore JS SDK требовал JSON.parse() для data-field в batchWrite операции (Kotlin отправляет JSON string, не object) — добавлен wrapper в init.js
2. Compose Node 21 not found crash при добавлении syncState в combine() — root cause: StateFlow recomposition race при многих observers. Решение: removed syncState from UI flow, only used in service
3. COOP/COEP headers в webpack блокировали Google Auth popup окно — SAH Pool VFS не требует SharedArrayBuffer, headers удалены

**Решение:** 
- JSON.parse wrapper в init.js batchWrite для cross-language data serialization
- Removed syncState from Compose, kept internal в SyncRepositoryImpl
- COOP/COEP removed из webpack.config.d/sqlite.js — Auth popup теперь открывается

## Выводы

**Решение:** Reusable cross-platform sync pattern для KMP приложений с Firestore backend:
- **Entity modeling:** 5 sync-полей (cloudId, userId, updatedAt, syncStatus, isDeleted) дают достаточно информации для LWW, tracking, conflict detection
- **Platform abstraction:** FirestoreSyncDataSource interface позволяет каждой платформе использовать native SDK (Firebase на Android, JS SDK на wasmJs, stub на iOS)
- **Real-time + polling:** Двойной механизм обеспечивает надёжность (real-time когда доступны listeners, polling как fallback для lossy networks)
- **LWW resolution:** updatedAt timestamp + simple max comparison достаточно для большинства use-case'ов, избегает complex 3-way merge
- **Bidirectional state:** Room = ground truth locally, Firestore = cloud layer, combine() мост между ними, offline changes накапливаются в PENDING status и pushят по восстановлении соединения
- **JS bridge discipline:** Firestore JS SDK требует точных интерфейсов (JSON.parse для data, правильные keys) — один неправильный field и batchWrite fails молча в browser console

**Ключевые паттерны:**
- `FirestoreSyncDataSource` expect/actual interface (abstract в common, platform-specific impl в androidMain/wasmJsMain)
- `callbackFlow` на Android для real-time Firestore snapshots (холодный start, горячий during lifecycle)
- `globalThis` bridge на wasmJs для JS SDK доступа + JSON.parse wrapper для data serialization
- `combine()` для merge Room + Firestore flows с LWW logic
- `StateFlow<SyncStatus>` в SyncRepository (internal, не в UI чтобы избежать recomposition crash)
- Firestore rules с timestamp validation и LWW enforcement на сервере

## Предложения по улучшению агентов

### android-expert
- [ ] Добавить в агента правило: при создании callbackFlow для Firestore/Network listeners, всегда pattern match на exception branches (network timeout, auth revoked, quota exceeded) — не оставлять collect {} пустым
- [ ] Документировать StateFlow в Compose recomposition (когда StateFlow с многими observers вызывает Node XXX not found crash — это признак того что StateFlow используется не-оптимально; правило: StateFlow в UI только для конечного наблюдения, не для промежуточного merging)

### kmp-expert
- [ ] Добавить правило для expect/actual interfaces в sync/network слое: interface должна быть достаточно thin чтобы каждая платформа могла реализовать без back-references к other platforms
- [ ] Platform stubs (iOS): шаблон для "not yet implemented" platforms — документировать в CLAUDE.md как правильно писать stubs которые работают локально (Room-only) но не блокируют компиляцию

### wasmjs-expert
- [ ] Добавить в wasmjs-expert.md правило про JSON.parse для JS SDK bridges: если передаёшь Kotlin object как JSON string в init.js — ВСЕГДА JSON.parse() перед использованием в native JS SDK calls
- [ ] Документировать polling fallback pattern для wasmJs: когда real-time listeners (e.g., onSnapshot) недоступны или flaky, 30-second polling fallback обеспечивает synchronization. Pattern: `while (isConnected) { delay(30s); fetch(); }`

### mobile-design-expert
- [ ] Добавить в UI guide: sync-status indicator на экранах с network-зависимым контентом (чеклисты, чаты, любые collaborative features) должны быть subtly visible но не intrusive — recommended: small chip in TopAppBar (4dp height min, sparkle or check icon)

