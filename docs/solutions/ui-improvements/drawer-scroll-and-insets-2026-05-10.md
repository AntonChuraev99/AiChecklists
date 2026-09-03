---
title: "ModalNavigationDrawer Scroll & System Insets Pattern"
date: 2026-05-10
type: ui-improvement
modules: [navigation, designsystem]
keywords: [drawer, scroll, modal-drawer-sheet, window-insets, accessibility, landscape, footer-pinning, system-bars]
project: Checklists
---

# ModalNavigationDrawer Scroll & System Insets Pattern

## Контекст

ModalNavigationDrawer используется в Compose Multiplatform для вмещения меню во все главные экраны. На устройствах с малой высотой (landscape, foldables, font scale ≥130%) содержимое drawer'а обрезается за экран, footer исчезает в системной navigation bar'е, и пользователь теряет доступ к последним пунктам меню.

Кроме того, `ModalDrawerSheet` (Material 3) уже применяет `DrawerDefaults.windowInsets = WindowInsets.systemBars` к своему Surface — это вводит автоматический padding для status bar и navigation bar. Если дополнительно дублировать `.navigationBarsPadding()` на child'ях (особенно footer'е), получается избыточный отступ с пространством, которое невозможно использовать.

## Проблема

**1. Обрезка контента на малых экранах**
- На landscape-ориентации (например, width 640dp, height 320dp) или на foldables с cover screen
- Footer (версия приложения) прижимался статичным `Spacer(weight=1f)` внутри единого Column'а → если контент вмещается ровно в высоту, footer отрезается
- Последние пункты (Privacy, Terms) невозможно скроллить

**2. Дублирующийся system insets padding**
- `ModalDrawerSheet` обвивает `AppNavigationDrawerContent` и применяет `DrawerDefaults.windowInsets`
- Если внутри вручную добавить `.navigationBarsPadding()` на Footer, получается двойное padding (одно от Surface, одно от модификатора)
- Визуально: footer отодвинут слишком далеко от нижнего края, визуальный gap

## Решение

**Финальный паттерн (default choice): full-content scroll.** Корневой `Column(fillMaxHeight + verticalScroll)` содержит всё — header, секции, footer (версию). Никаких вложенных Column'ов, никаких pinned-областей.

### Код до (неправильно — обрезка + двойной inset)
```kotlin
Column(modifier = Modifier.fillMaxHeight()) {
    DrawerBrandHeader()
    HorizontalDivider(...)
    // 9 NavigationDrawerItem'ов в 3 секциях
    Spacer(modifier = Modifier.weight(1f))   // прижимал footer, но без скролла
    DrawerFooter(versionName)                // .navigationBarsPadding() — дубль системного inset
}
```

### Код после (правильно — full-content scroll)
```kotlin
Column(
    modifier = Modifier
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
) {
    DrawerBrandHeader()
    HorizontalDivider(...)

    // Все пункты меню (Navigate + Help + About) — 9 NavigationDrawerItem
    DrawerSectionLabel("Navigate")
    NavigationDrawerItem(...) // Home
    NavigationDrawerItem(...) // Today
    NavigationDrawerItem(...) // Updates
    NavigationDrawerItem(...) // Settings

    DrawerSectionLabel("Help")
    NavigationDrawerItem(...) // Rate
    NavigationDrawerItem(...) // Feedback
    NavigationDrawerItem(...) // Support

    DrawerSectionLabel("About")
    NavigationDrawerItem(...) // Privacy
    NavigationDrawerItem(...) // Terms

    Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
    DrawerFooter(versionName)   // часть scroll-Column'а, не pinned
}
```

### Удаление дублирующихся insets
```kotlin
// БЫЛО (DrawerFooter):
@Composable
private fun DrawerFooter(versionName: String) {
    Text(
        text = stringResource(...),
        modifier = Modifier
            .padding(...)
            .navigationBarsPadding()  // ❌ ДУБЛИРУЕТ DrawerDefaults.windowInsets
    )
}

// СТАЛО (DrawerFooter):
@Composable
private fun DrawerFooter(versionName: String) {
    Text(
        text = stringResource(...),
        modifier = Modifier
            .padding(...)
            // ✅ NO .navigationBarsPadding() — ModalDrawerSheet уже обработала
    )
}
```

## Почему это работает

1. **Один scroll-region:** `verticalScroll` на корневом `Column` делает всё содержимое (включая footer) скроллящимся как единое целое. На малом viewport — скроллится; на большом — содержимое короче, чем sheet, и footer виден сразу под последним пунктом.

2. **`fillMaxHeight()` остаётся:** без него Column сожмётся до высоты содержимого, и под ним появится дыра до низа sheet'а (background не закроет). С `fillMaxHeight()` фон Column тянется на всю высоту, контент при этом остаётся вверху (Compose default arrangement = Top).

3. **DrawerDefaults.windowInsets уже применён:** `ModalDrawerSheet` обвивает наш composable и вводит padding для status/navigation bar автоматически через `WindowInsets.systemBars` на Surface. Дублирующий модификатор нарушает это вычисление.

## Альтернативы

**Вариант 1: split scroll + pinned footer (отклонён по UX)**

Изначально был выбран, потом по предпочтению пользователя заменён на full-content scroll. Когда уместен:
- Жёсткое требование «футер всегда виден без скролла» (sticky CTA в drawer'е, например, кнопка Upgrade)
- Footer содержит критичный action, не информацию

Структура (для exception case):
```kotlin
Column(modifier = Modifier.fillMaxHeight()) {
    Column(modifier = Modifier.weight(1f).verticalScroll(...)) {
        // Header + sections
    }
    DrawerFooter(versionName)   // pinned bottom
}
```

Минусы по сравнению с full-scroll:
- На больших экранах (PermanentNavigationDrawer / планшет) появляется визуальный gap между пунктами и закреплённым футером.
- Двойная семантическая иерархия для TalkBack (два scroll-region'а).
- Лишняя вложенность Column'ов.

**Вариант 2: LazyColumn вместо Column + verticalScroll (отклонён)**
- На малом количестве пунктов (9 item'ов) избыточное усложнение (нужны key/contentType, SaveableStateHolder для scroll-state).
- Column + rememberScrollState проще и достаточен.

**Вариант 3: NestedScroll с header/footer anchoring (отклонён)**
- Для drawer'а excessive complexity.
- NestedScroll обычно используется для app-bar shrinking (consumePreScroll), не для меню.

## Применимость

Правило распространяется на **любой ModalNavigationDrawer в KMP-приложениях**, где:
- Содержимое меню может быть больше высоты экрана
- Footer (версия, copy, контакты) — информационный, не critical action
- Целевые экраны: landscape, foldables, малые планшеты, font scale > 125%

## Reusable Pattern (default — full-content scroll)

```
ModalNavigationDrawer
├─ ModalDrawerSheet (обвивает, уже применяет DrawerDefaults.windowInsets)
│  └─ Column(fillMaxHeight + verticalScroll(rememberScrollState))
│     ├─ Header
│     ├─ Divider + spacing
│     ├─ Section 1 (Label + Items)
│     ├─ Spacer
│     ├─ Section 2 (Label + Items)
│     ├─ Spacer
│     ├─ Section 3 (Label + Items)
│     ├─ Spacer(bottom spacing)
│     └─ Footer
│        ├─ NO statusBarsPadding / navigationBarsPadding
│        └─ Padding(horizontal, vertical) для внутреннего отступа
```

**Импорты для реализации:**
```kotlin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
```

**Что НЕ должно быть в коде:**
- `navigationBarsPadding()` / `statusBarsPadding()` на child'ях (Surface ModalDrawerSheet уже обработал через windowInsets)
- Вложенный `Column(weight(1f).verticalScroll())` без необходимости в pinned footer'е
- `Spacer(weight=1f)` где-либо в drawer-content'е (не работает в scroll-области, излишен в full-scroll)

## Связанные файлы

- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/navigation/AppNavigationDrawerContent.kt` — реализация
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/App.kt` — 4 caller-сайта `ModalDrawerSheet` (Main / Settings / UpdateFeed / Today destinations) — не требуют изменений
