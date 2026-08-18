---
title: "MCP Phase 3 — Named Fills as Independent Tools"
date: 2026-07-10
type: feature
modules: [mcp-server, composeApp, core/navigation, core/designsystem, landing]
keywords: [MCP, named-fills, FillSummary, list_fills, create_fill, claude-desktop, firestore-sync, mcp-tools, backend-contract, optional-fillId]
project: gisti-checklists
---

# MCP Phase 3 — Named Fills as Independent Tools

## Проблема / Контекст

MCP Phase 0–2 expose only the default fill per checklist (default-fill = automatic first fill created by app). Users requesting named fills via Claude Desktop / claude.ai can't enumerate or create multiple independent fills — they're stuck with one fill per checklist. This blocks use cases like "analyze this checklist with two different AI models" or "compare my AI-generated fill vs my manual fill".

Phase 3 solves: expose named fills as first-class MCP entities. Two new tools (`list_fills`, `create_fill`) enumerate and create fills independently. Existing state-mutation tools (toggle_item, edit_note, fill_checklist_ai) now accept optional `fillId` to target a specific fill instead of the implicit default.

## Решение

### Backend — TypeScript contract (mcp-server/src/mutate.ts + mcp.ts)

**FillSummary** — new readonly DTO for named fills:
```typescript
type FillSummary = {
  fillId: string;
  templateId?: string;
  name: string;
  createdAt: Timestamp;
  updatedAt: Timestamp;
  itemCount: number;
};
```

**Core operations (mutate.ts):**
- `buildFill(db, checklistId, options)` — factory: create fill doc with auto-ID, set name + metadata
- `selectFill(fillId)` — resolver: return fill doc by fillId (used by state-tools to select target)
- `findFill(db, checklistId, fillId)` — query: get one fill's metadata + items (SyncData envelope)
- `listFills(db, checklistId, options?)` — query: enumerate all fills for a checklist (array of FillSummary)
- `createNamedFill(db, checklistId, options)` — service: build + initialize empty fill, return FillSummary

No new Firestore types — fills are the same `fills/{fillId}` docs under checklist. Named fill = regular fill with explicit name + optional templateId (vs. default-fill: always unnamed / auto-created).

**MCP tools (mcp.ts):**
1. **list_fills** — Query tool
   - Input: checklistId
   - Output: array of FillSummary (fillId, name, itemCount, createdAt)
   - Use: enumerate named fills in Claude chat

2. **create_fill** — Mutation tool
   - Input: checklistId, name (required), mode ("template" | "clone" | "empty"), [sourceId] (for clone)
   - Output: FillSummary (new fill)
   - Use: create from template, clone existing, or start empty

3. Existing state-mutation tools (get_checklist, toggle_item, edit_note, fill_checklist_ai)
   - **New optional parameter:** `fillId?: string`
   - Behavior: if fillId omitted, default to template-default-fill (backward-compatible)
   - If fillId specified, operate on that fill (renderChecklist targetId=fillId, mutate that fill's items)

**Version bump:** mcp-server v0.4.0 (was 0.3.5). Trailing edge: Firestore reads/writes to fills unchanged (default-fill reconciliation stays in app, not MCP).

### Unit testing

40 unit tests (mutate.test.ts, new +7):
- buildFill: initialize fillId + metadata
- selectFill: resolve fillId→doc
- listFills: multi-fill enumeration
- createNamedFill: factory end-to-end
- Optional fillId on state-tools: backward-compatible + targeted mutations
- Edge cases: missing fillId (default to template), fillId not found (error)

Typecheck: green (no type errors, full Kotlin↔TS contract validation via codec).

### Frontend — In-app MCP Screen

**Location:** `composeApp/commonMain/navigation/mcp/McpScreen.kt`

```kotlin
@Composable
fun McpScreen(
  onNavigateUp: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isEndpointCopied by rememberSaveable { mutableStateOf(false) }
  
  Scaffold(
    topBar = { /* app bar */ },
    modifier = modifier
  ) { padding ->
    Column(
      modifier = Modifier
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .fillMaxWidth()
    ) {
      // Hero section: aiGradient (2-stop: blue→purple), title + subtitle
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(280.dp)
          .background(
            brush = Brush.linearGradient(
              colors = listOf(Color(0xFF4F46E5), Color(0xFFA855F7)),
              start = Offset.Zero,
              end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
          )
      ) {
        Column(
          modifier = Modifier
            .align(Alignment.Center)
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = stringResource(Res.string.mcp_hero_title),
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = stringResource(Res.string.mcp_hero_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
          )
        }
      }
      
      Spacer(modifier = Modifier.height(32.dp))
      
      // Value props: 3 cards
      Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        listOf(
          Pair(
            stringResource(Res.string.mcp_prop1_title),
            stringResource(Res.string.mcp_prop1_desc)
          ),
          Pair(
            stringResource(Res.string.mcp_prop2_title),
            stringResource(Res.string.mcp_prop2_desc)
          ),
          Pair(
            stringResource(Res.string.mcp_prop3_title),
            stringResource(Res.string.mcp_prop3_desc)
          )
        ).forEach { (title, desc) ->
          AppCard(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
      
      Spacer(modifier = Modifier.height(24.dp))
      
      // Endpoint URL + copy button
      Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
          text = stringResource(Res.string.mcp_endpoint_label),
          style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              color = MaterialTheme.colorScheme.surfaceContainer,
              shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "https://gisti-mcp.gisti.workers.dev/mcp",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
          )
          AppButton(
            text = if (isEndpointCopied) {
              stringResource(Res.string.mcp_endpoint_copied)
            } else {
              stringResource(Res.string.mcp_endpoint_copy)
            },
            onClick = {
              // Copy to clipboard
              isEndpointCopied = true
              // Auto-reset after 2s
            },
            size = AppButtonDefaults.Small,
            style = AppButtonDefaults.Outlined
          )
        }
      }
      
      Spacer(modifier = Modifier.height(24.dp))
      
      // CTA: Connection guide
      Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        AppButton(
          text = stringResource(Res.string.mcp_guide_cta),
          onClick = { /* navigate to gisti-ai.com/mcp or openLink */ },
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(Res.string.mcp_guide_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }
      
      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}
```

**Strings (core/designsystem/composeResources/en/strings.xml):**
```xml
<!-- MCP Screen -->
<string name="mcp_hero_title">Gisti MCP</string>
<string name="mcp_hero_subtitle">Manage your checklists in Claude</string>
<string name="mcp_prop1_title">Chat-native checklists</string>
<string name="mcp_prop1_desc">Create, fill, and analyze checklists without leaving Claude Desktop</string>
<string name="mcp_prop2_title">AI assistant integration</string>
<string name="mcp_prop2_desc">16 tools: read, write, analyze, generate fills</string>
<string name="mcp_prop3_title">Your data, your control</string>
<string name="mcp_prop3_desc">Synced to Firestore, signed-in users only</string>
<string name="mcp_endpoint_label">MCP Server Endpoint</string>
<string name="mcp_endpoint_copy">Copy</string>
<string name="mcp_endpoint_copied">Copied!</string>
<string name="mcp_guide_cta">View connection guide</string>
<string name="mcp_guide_hint">Works with Claude Desktop and claude.ai</string>
<string name="drawer_mcp">Gisti MCP</string>
```

**Navigation (AppNavRoute.kt, AppNavigationDrawerContent.kt):**
```kotlin
// AppNavRoute.kt
sealed interface AppNavRoute : Route {
  data object Mcp : AppNavRoute
}

// AppNavigationDrawerContent.kt
DrawerDestination(
  route = AppNavRoute.Mcp,
  label = stringResource(Res.string.drawer_mcp),
  icon = Icons.Outlined.Settings,
  onNavigate = onMcpClick  // wired to push route
)

// App.kt — route handler
is AppNavRoute.Mcp -> McpScreen(onNavigateUp = navigator::navigateUp)
```

**Stateless, no app state mutation** — screen is read-only UI (URL copy, CTA link). Works on both Android + wasmJs.

### SEO Landing Page

**Location:** `landing/mcp/index.html`

- Static HTML + Tailwind CSS (zero-JS)
- Hero + 3 sections (benefits / tools / integration steps)
- Tools table: 16 tools (list_checklists, list_fills, create_fill, etc.)
- Full setup guide (Claude Desktop / claude.ai / Code steps)
- JSON-LD structured data: SoftwareApplication + HowTo + FAQPage
- Deployed to `gisti-ai.com/mcp` (307→`/mcp/`) via `gisti-landing` Cloudflare Worker

Deploy config (landing-src/tailwind.config.js, landing/tailwind.css):
```javascript
// tailwind.config.js
content: ['landing/**/*.html', 'landing/mcp/**/*.html'],
// → regen landing/tailwind.css to include mcp styles
```

**Deployment (2026-07-10):**
- Cloudflare account: gmail (2c9dfaad) — NOT swapify
- Worker: `gisti-landing` v`a2f1823d`
- Route: `gisti-ai.com/mcp`
- Smoke test: 200 OK, correct HTML returned, JSON-LD valid

## Почему именно так

1. **Optional fillId (backward-compatible):** Existing integrations calling `toggle_item` without fillId still work (default to template-default-fill). New clients can specify fillId for targeted mutations. No breaking changes.

2. **FillSummary (stateless DTO):** Named fills don't require new Firestore types or contract changes — fills are regular `fills/{fillId}` docs with a name field. Serialization remains unchanged (existing codec works).

3. **MCP screen in commonMain:** Zero platform duplication. The screen renders identically on Android (native Compose) and wasmJs (Skiko canvas). No platform-specific state.

4. **SEO landing separate from web app:** Static HTML (zero-JS, fast, SEO-friendly) reduces page size and avoids runtime parsing complexity. Dedicated page at `/mcp` allows targeted promotion.

5. **Reminders/repeat deferred:** ReminderRepeatRule + RepeatEndCondition are sealed classes with platform-specific subtypes (e.g., AndroidAlarmScheduler). Serializing polymorphic sealed classes to JSON requires custom codecs. Deferred to Phase 4 with dedicated contract-test.

## Примеры

**list_fills response (named fill enumeration):**
```json
{
  "fills": [
    {
      "fillId": "fill_123abc",
      "templateId": "template_456def",
      "name": "AI-generated weekly plan",
      "createdAt": "2026-07-08T10:30:00Z",
      "updatedAt": "2026-07-10T15:45:12Z",
      "itemCount": 12
    },
    {
      "fillId": "fill_default",
      "templateId": null,
      "name": "Default fill",
      "createdAt": "2026-07-08T09:15:00Z",
      "updatedAt": "2026-07-09T20:00:00Z",
      "itemCount": 12
    }
  ]
}
```

**create_fill (from template):**
```json
{
  "checklistId": "checklist_789xyz",
  "name": "Vacation packing—second pass",
  "mode": "template",
  "sourceId": "template_456def"
}
// → returns FillSummary with new fillId
```

**toggle_item with fillId:**
```json
{
  "checklistId": "checklist_789xyz",
  "fillId": "fill_123abc",
  "itemId": "item_aaa",
  "checked": true
}
// → toggles item in fill_123abc (not default-fill)
```

## Связанные файлы

- `mcp-server/src/mutate.ts` — FillSummary, buildFill, createNamedFill, listFills
- `mcp-server/src/mcp.ts` — list_fills, create_fill tools, v0.4.0
- `mcp-server/src/mutate.test.ts` — 40 unit tests (new +7)
- `mcp-server/README.md` — tools documentation
- `composeApp/commonMain/navigation/mcp/McpScreen.kt`
- `core/navigation/api/AppNavRoute.kt`
- `core/designsystem/composeResources/en/strings.xml`
- `landing/mcp/index.html`
- `landing/tailwind.css`
