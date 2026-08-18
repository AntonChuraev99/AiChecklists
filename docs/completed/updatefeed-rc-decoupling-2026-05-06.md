# Update Feed RC Decoupling

**Статус:** Done
**Дата старта:** 2026-05-06
**Start SHA:** 63844a2d47762af9582c741b6526d31642909c99
**Project:** gisti-checklists
**Тип:** refactoring
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** feature/updatefeed (commonMain + commonTest), core/remoteconfig/api, core/remoteconfig/impl, composeApp, docs/guidelines/

## Цель (продуктовая)
Отвязать Update Feed feature от Firebase Remote Config. Контент перемещается из `RemoteConfigDefaults.UPDATE_FEED_JSON` (глобальная константа) в локальную константу `UpdateFeedContent.JSON` внутри `feature/updatefeed/data/`. Это упрощает архитектуру, снижает синхронизацию (убирается требование "зеркалить JSON в RC Console"), и позволяет обновлять содержимое Release Notes directly в коде без RC deployment.

## Технический план
1. **feature/updatefeed**: создать `data/UpdateFeedContent.kt` с константой `JSON: String` (скопировать содержимое из `RemoteConfigDefaults.UPDATE_FEED_JSON`)
2. **feature/updatefeed:data/impl**: обновить `UpdateFeedRepositoryImpl` — удалить `RemoteConfigProvider` dependency, использовать `UpdateFeedContent.JSON` как fallback (или основной источник)
3. **feature/updatefeed:tests**: обновить `UpdateFeedRepositoryTest` — поменять mock RemoteConfigProvider на прямое использование константы
4. **core/remoteconfig/api**: удалить `UPDATE_FEED_JSON` из `RemoteConfigKeys.kt` и `RemoteConfigDefaults.kt`
5. **core/remoteconfig/impl**: удалить регистрацию ключа из `FirebaseRemoteConfigProvider.remoteConfigDefaults()`
6. **composeApp**: проверить, что нет других ссылок на `RemoteConfigKeys.UPDATE_FEED_JSON` или `RemoteConfigDefaults.UPDATE_FEED_JSON`
7. **Документация**: обновить `CLAUDE.md` (убрать требование зеркалить JSON в RC) и `docs/guidelines/updates-feed.md`
8. **Module dependency**: убрать зависимость `feature/updatefeed → core/remoteconfig/api` если RC больше не используется в updatefeed

## Лог итераций

### Итерация 1 — 2026-05-06 — главный агент
**Что сделано:** Реализация всех 8 пунктов плана. Создана `UpdateFeedContent.kt` с 19 постами + 9 групп (v1.6–v1.14 с weekly_mode + per-item-reminders). Обновлены `UpdateFeedRepositoryImpl` конструктор (RemoteConfigProvider → jsonSource: String), тесты (FakeRemoteConfigProvider удалён, 20 ссылок переведены на UpdateFeedContent.JSON). Удалены регистрации RC параметра в `FirebaseRemoteConfigProvider.remoteConfigDefaults()` и `RemoteConfigKeys.kt`. Удалена зависимость `feature/updatefeed → core/remoteconfig/api` из Gradle. Обновлены CLAUDE.md (убрано "зеркалить в RC Console", добавлено "post и version поставляются вместе") и docs/guidelines/updates-feed.md (диаграмма, таблица файлов, блокировка hotfix через RC, post-count assertions, расширено тестовое покрытие 13→19 постов).

**Почему так:** Update Feed контент жёстко привязан к версии APK (post v1.14 фичей не может быть раньше выхода v1.14 в Play). Remote Config предполагает независимое обновление параметров — это противоречит модели. Bundled JSON убирает ложную гибкость и точку рассинхрона "in-code default vs RC live value". Упрощение архитектуры + одна точка истины.

**Баги/проблемы:** Один retry-цикл в тестах: `UpdateFeedRepositoryImplTest.invalidJson()` инстанциировал репозиторий со старой сигнатурой (не через helper buildRepository). `compileDebugUnitTestKotlinAndroid` упал на ошибке типа. 

**Решение:** Единичная правка в `invalidJson()` — использовать helper, как остальные 5 тестов. BUILD SUCCESSFUL.

**Скрытый риск (предотвращён):** `scripts/firebase_remote_config.py` содержал блок `update_feed_json` со старыми дефолтами. При следующем `publish` параметр вернулся бы в RC как "мёртвый". Удалён блок + добавлен комментарий-маркер с датой (защита от регрессии, если кто-то забудет о рефакторе).

## Выводы

**Что получилось:** Полная отвязка Update Feed от Remote Config (RC). Контент теперь в `UpdateFeedContent.JSON` (code-bundled), регистрация параметра удалена из RC infrastructure. Мёртвая gradle-зависимость удалена. Документация обновлена (CLAUDE.md, updates-feed.md). Тесты переписаны, build успешен.

**Compound effect:** Update Feed теперь проще синхронизировать — нет требования "зеркалить в RC Console" и никаких рассинхронов code vs live. Post и версия APK поставляются вместе, что отражает логику привязки контента к версии. Заодно обновлена документация guidelines + предотвращён скрытый регрессионный риск в deployment-скрипте (блок UPDATE_FEED_JSON).

**Сложность:** Standard (как заявлено). Одна фаза, один retry-цикл в тестах, 8 пунктов плана выполнены полностью. Выводы не требуются по Impact-критериям: Impact = Medium, но это не recurring bug и не инфраструктурный фикс — рефакторинг archicture, применимый в будущем.

**Learnings для команды:** Patterns "когда выносить контент из Remote Config обратно в code" задокументирован в solution-файле (architecture point of view). Риск скрытой регрессии при удалении параметров из RC найден (скрипты deployment), добавлены маркеры-комментарии на год вперёд.

## Предложения по улучшению агентов

(Без предложений — задача решена главным агентом, специалисты не использовались. Архитектурное решение не требует обновления playbook агентов.)
