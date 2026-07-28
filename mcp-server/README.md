# Gisti MCP Server

A **remote [MCP](https://modelcontextprotocol.io) server** that lets any Gisti user connect their
account from an MCP client (Claude Desktop / Code / claude.ai) and **read, AI-generate, and edit
their checklists** in natural language — cross-device, cloud-synced, while the app is closed.

- **Live:** `https://gisti-mcp.gisti.workers.dev/mcp`
- **Status:** Phases 0–2 + public-launch hardening shipped & prod-verified (2026-07-10).
- **Runtime:** a single Cloudflare Worker (TypeScript). No app changes — the MCP is a *second writer*
  of the same Firestore documents the KMP client already writes.

## Architecture

```
MCP client (Claude)
   │  MCP over HTTP + OAuth 2.1
   ▼
Cloudflare Worker (this repo)              ← the trusted auth boundary
   ├─ OAuth: @cloudflare/workers-oauth-provider ↔ upstream "Login with Google" (Hono)
   ├─ Identity: Google email → Firestore user docs (service-account JWT, REST)
   ├─ Read:      Firestore users/{id}/checklists
   ├─ AI write:  POST existing Cloud Functions generate_checklist / analyze_and_fill_checklist
   └─ CRUD write: read-modify-write the whole checklist doc (mirrors the Kotlin sync schema)
   ▼
Firestore  ──live listeners──▶  the open Gisti app reflects changes
```

**Cloudflare Workers ≠ Node:** the Firebase Admin SDK does not run here. All Firestore access is
REST + a service-account JWT signed RS256 with Web Crypto (`firestore.ts`) — no `firebase-admin`.

## Tools

| Phase | Tool | Notes |
|-------|------|-------|
| 0 read | `list_checklists` / `get_checklist` / `search_checklists` | `get_checklist` renders the folder tree with checked state, notes, and item ids |
| 1 AI | `create_checklist_ai(prompt)` | → `generate_checklist` CF → tree flattened to a checklist |
| 1 AI | `fill_checklist_ai(id, input)` | → `analyze_and_fill_checklist` CF → results merged into the fill |
| 2 CRUD | `toggle_item` / `add_item` / `rename_item` / `edit_note` / `delete_item` / `reorder_items` | operate on the item id from `get_checklist` |
| 2 CRUD | `rename_checklist` / `delete_checklist` (soft) / `create_checklist_empty` | |
| 3 fills | `list_fills` / `create_fill` | extra named "sessions" of a checklist. The state tools (`get_checklist` / `toggle_item` / `edit_note` / `fill_checklist_ai`) take an optional `fillId` to target one; structural edits (add/rename/delete/reorder item) stay on the template + default fill, mirroring the app |

AI tools spend the user's AI credits (enforced server-side by the Cloud Functions).

## ⚠ Identity model — TWO user docs per person

Gisti keeps two `users/{id}` docs per person, and they play different roles here:

- **device/credit doc** — id is a random UUID, carries `google_email` + `google_uid` + `ai_credits`.
  This is what the app sends to the Cloud Functions as `user_id`; **AI credit calls charge it**.
- **google_uid doc** — id == the Firebase `google_uid`, holds the `checklists` (cross-device source
  of truth). **Creates write here; mutations write back to whichever doc the checklist was found in.**

`resolveUserContext` (`firestore.ts`) returns `{ creditUserId, writeUserId, allIds }`. Resolving by
`google_email` alone lands on the (usually empty) device doc — matching the union is mandatory.

## Write contract — byte-perfect mirror of the Kotlin serializer

The MCP is a third place that must encode the exact bytes the app writes (`encode.ts`). Two layers:

1. **`itemsJson`** (template + fill) = kotlinx `encodeDefaults=false`: omit default-valued fields,
   enums by name (`ITEM`/`FOLDER`). Fill `checked` has no default → always emitted.
2. **Firestore doc map** = every key always present (null → nullValue); all numbers `integerValue`
   (epoch-millis int64, never Timestamp/double). Writes via PATCH + `updateMask` (merge semantics,
   preserves unknown future fields).

Invariants (`mutate.ts`, mirroring `ChecklistRepositoryImpl`): default-fill target, `templateItemId`
on every fill item, dirty-parent (`updatedAt` bump), monotonic `updatedAt`, one shared timestamp on
reorder, folder-child promotion on delete.

**Contract test (the gate):** `src/encode.test.ts` pins the encoder to golden bytes; the Kotlin
`ItemsJsonEncodingContractTest.kt` (in `feature/checklist` commonTest) encodes the same fixture
through the real serializer and asserts the identical bytes — a field rename/reorder on either side
breaks its own test.

## Public-launch hardening (`security.ts`)

- **Opaque OAuth `state`** — a random nonce stored in `OAUTH_KV` (TTL 600s), consumed one-time on
  callback; forged/replayed nonces are rejected (CSRF/tamper safe).
- **Per-user rate limiting** — KV fixed-window counters keyed by email (read 180/min, write 60/min,
  AI 30/hour). Best-effort, defense-in-depth over the Cloud Function's transactional credit guard.
- **Deterministic `request_id`** — `SHA-256(userId|tool|args|minute)` so a retry dedups the
  idempotent CF credit reservation instead of double-charging.
- **`tracked()` wrapper** on every tool — never leaks a raw 500 to the client; errors are logged
  and reported to analytics (below).

## Analytics (`analytics.ts`)

Seven events to Amplitude project **786722** via the HTTP V2 API (plain `fetch`, no SDK). They
answer: did anyone even get through OAuth · how many people connected, from which client · which of
the 16 tools are live vs dead · which fail, how, and how slowly · and whether MCP users retain
better — the last one only works because the events land on the **same user profile the app writes**.

| Event | Properties |
|---|---|
| `mcp_auth_started` | *(none)* — `/authorize` accepted the client and redirected to Google |
| `mcp_auth_completed` | *(none)* — the grant exists and the client is connected |
| `mcp_auth_failed` | `reason` — `invalid_request` / `missing_client_id` / `missing_code_or_state` / `invalid_state` / `state_missing_client_id` / `google_token_<status>` / `no_id_token` / `id_token_unreadable` / `no_email` / `grant_failed` |
| `mcp_session_started` | `linked` (false = connected but no Gisti account yet), `identity_resolved`, `client_name`, `client_version`. Once per MCP session, on its first tool call |
| `mcp_tool_called` | `tool_name`. Every invocation, emitted **before** the handler runs |
| `mcp_tool_completed` | `tool_name`, `duration_ms`, `outcome` (`ok` / `soft_fail`), `reason` (only when `soft_fail`) — `rate_limited` / `not_linked` / `checklist_not_found` / `fill_not_found` / `item_not_found` / `empty_query` / `empty_name` / `no_items` |
| `mcp_tool_failed` | `tool_name`, `duration_ms`, `reason` — `cf_402` (no credits) / `cf_429` / `cf_503` / `cf_network` / `exception` |

**Reading the data — four traps:**

- **The OAuth funnel is EVENT TOTALS, not unique users.** `/authorize` has no email, so
  `mcp_auth_started` / `mcp_auth_failed` are attributed to one shared synthetic device
  (`mcp_auth_flow`) — a per-attempt random id would mint a new Amplitude "user" per attempt and
  inflate DAU. `mcp_auth_completed` is the exception: by then the email is known, so it carries the
  real `mcp_<sha256(email)>` device and joins to the person. Zero `mcp_session_started` with a
  healthy `mcp_auth_started` count means people arrived and bounced — that distinction is the whole
  reason these three exist.
- **A tool has three outcomes, and `mcp_tool_called` is not one of them.** `called` fires before the
  handler (so a hang still leaves a trace) and is closed by exactly one of `mcp_tool_completed` /
  `mcp_tool_failed`. Success rate = `completed[outcome=ok] ÷ called`; `called − completed − failed`
  is the never-returned bucket. **`mcp_tool_failed` only fires on a thrown error** — every "soft"
  refusal (rate-limit deny, `SIGN_IN_HINT`, "No checklist found with id X") is an ordinary return
  and shows up as `mcp_tool_completed{outcome:soft_fail}`. `checklist_not_found` / `item_not_found` /
  `fill_not_found` are the "the agent invented an id" signal.
- **`linked=false` means "no Gisti account" only when `identity_resolved=true`.** When the Firestore
  identity lookup fails the session degrades to a device-id-only identity and reports
  `identity_resolved=false`; filter those out before quoting the unlinked population. (Previously
  such a session was dropped from analytics entirely — no events at all, not even `session_started`.)
- **`mcp_session_started` counts CONNECTIONS, not people.** It is once per MCP session id, persisted
  in Durable Object storage so an evicted/hibernated DO does not re-fire it — but a client that
  reconnects legitimately opens a new session. MCP has no user-level session concept; count people
  with distinct `user_id` / `device_id`.

Notes:

- **`user_id` = the `users/{doc_id}` UUID** (`resolveUserContext().creditUserId`), sent verbatim.
  That is exactly what the app passes to `setUserId` (`Analytics.kt` ← `SplashViewModel` ←
  DataStore `USER_ID_KEY` ← `register_user`) and what the CFs use for `push_sent`. **Do not
  "normalise" it** — a prefix or a swap to `google_uid` silently detaches MCP from every profile
  and makes the retention question unanswerable. An unlinked email gets no `user_id`, only a
  pseudonymous `device_id` = `mcp_<sha256(email)[0:16]>` (the email itself never leaves the worker).
- **Privacy: checklist names, item text, notes, and prompts are never sent.** Enforced by the type
  system, not by discipline — `McpEvent` is a closed union whose payloads are tool names, reasons
  from fixed vocabularies, and durations, so a raw error message (which quotes user data) cannot be
  attached without a compile error. The one non-enumerable string is the MCP client's self-reported
  `clientInfo`, and it is forced through `sanitizeClientSlug` / `sanitizeClientVersion` into a
  ≤24-char `[a-z0-9-]` token first. `analytics.test.ts` pins the payload key allowlist across
  **every** event variant (`Record<McpEvent["type"], McpEvent>` makes a new event fail to compile
  until it is added to the privacy tests).
- **Never on the critical path** — fire-and-forget; a missing key or a failed POST logs and no-ops.
  Tool events use **no** `ctx.waitUntil()`: they run inside the Durable Object, where `waitUntil`
  [has no effect](https://developers.cloudflare.com/durable-objects/api/state/) — a DO stays alive
  on its own while the fetch is pending. The `mcp_auth_*` events are the opposite case: they run in
  a plain Worker request that can be torn down at response time, so `executionCtx.waitUntil` there
  is required, not cargo cult.

## Files

| File | Purpose |
|------|---------|
| `src/index.ts` | Worker entry — `OAuthProvider` wiring, `serve("/mcp")` |
| `src/google-handler.ts` | Upstream Google OAuth leg (`/authorize`, `/callback`) + the `mcp_auth_*` funnel |
| `src/mcp.ts` | `GistiMCP` McpAgent — all tools, `tracked()` instrumentation wrapper |
| `src/types.ts` | Worker `Env` bindings/secrets + the OAuth `Props` (proven Google claims) |
| `src/firestore.ts` | SA-JWT auth + Firestore REST (resolve, read, write) |
| `src/model.ts` | Domain types + `itemsJson` decoders (read path) |
| `src/encode.ts` | `itemsJson` + Firestore-doc encoders (write path) |
| `src/mutate.ts` | Pure domain mutations for the write tools |
| `src/cf.ts` | Cloud Function client (AI write) |
| `src/security.ts` | OAuth state, rate limiting, request_id |
| `src/analytics.ts` | Amplitude event union, identity, privacy sanitisers, HTTP V2 upload |
| `src/*.test.ts` | vitest unit + contract tests |
| `scripts/verify-*.ts` | Node scripts to exercise the real read/write path against prod |

## Develop / test / deploy

```bash
npm install
npm run typecheck            # tsc --noEmit
npm run test                 # vitest (encode contract + mutate + security + analytics privacy)
npm run dev                  # wrangler dev (local)

# deploy (gmail Cloudflare account — account_id pinned in wrangler.jsonc)
CLOUDFLARE_API_TOKEN=$(cat ~/.gisti-mcp-secrets/cf-token) npx wrangler deploy

# data-layer smoke against prod (service account, no OAuth)
npx tsx scripts/verify-resolve.ts <email>   # read
npx tsx scripts/verify-write.ts <email>     # write (creates + deletes a marked test checklist)
```

## Secrets & config

Nothing secret is committed. Values live **outside the repo** in `~/.gisti-mcp-secrets/` and, for
local dev, in gitignored `mcp-server/.dev.vars`. Prod secrets are set via `wrangler secret put`:

| Secret | Required? | Value |
|---|---|---|
| `GOOGLE_OAUTH_CLIENT_ID` | yes | Google OAuth web client |
| `GOOGLE_OAUTH_CLIENT_SECRET` | yes | ″ |
| `FIREBASE_SERVICE_ACCOUNT` | yes | SA JSON key, one JSON string |
| `AMPLITUDE_SERVER_API_KEY` | **optional** | Amplitude project 786722 API key — the same value the Cloud Functions read under this name (Secret Manager `amplitude-server-key`). **Unset → the server behaves exactly as before, minus the `mcp_*` events**: analytics is never a hard dependency of a tool call |

The service account is least-privilege (`roles/datastore.user` — Firestore only, no Firebase Auth).
Bindings (`OAUTH_KV`, the `GistiMCP` Durable Object) and the `aichecklists-40230` project id are in
`wrangler.jsonc` (safe to commit).

**Setup gate:** the Google OAuth web client's Authorized redirect URIs must include
`https://gisti-mcp.gisti.workers.dev/callback` (console-only, already done). After a deploy that adds
tools, an already-connected client must **reconnect** to pick up the new tool list.

## Deferred (Phase 3)

**Named fills shipped** — `list_fills` / `create_fill` plus an optional `fillId` on the state tools
(code-complete, unit-tested; a field rename/reorder can't drift because a named fill is the same
`FillSyncData` the encoder already pins). Still postponed to a focused pass: **reminders/repeat via
tools** — the nested `ReminderRepeatRule` + polymorphic `RepeatEndCondition` serialization warrants
its own contract-test first. The full design log lives in
`docs/active/gisti-mcp-server-design-2026-07-10.md` (local, gitignored).
