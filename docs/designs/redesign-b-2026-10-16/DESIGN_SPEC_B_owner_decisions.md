# Owner decisions on DESIGN_SPEC_B.md (2026-10-16) — OVERRIDE the spec where they differ

1. **Inbox section header counts — ON, as in the mock.** `AppSectionHeader(count = n)` on the Inbox for every section (Overdue/Today/Upcoming/Anytime): number of open tasks in that section. The old "no debt counter" decision at `InboxScreen.kt:1700` is reopened by the owner — update that comment to cite this decision. Header subtitle keeps the total.
2. **Ask pill — as in the mock: label "Ask Gisti or add a task" + a mic button.**
   - New string key (EN "Ask Gisti or add a task", + RU, + HI); keep `nav_chat_fab_content_description` usage for a11y only if the label differs — the visible label is the new string.
   - Pill tap → `onOpenChat` (unchanged). Mic tap → open the chat dock **already recording** (hold-to-record/voice flow that the chat input already has). This needs a new entry: `V2ChatDockOverlay` / chat state must accept a "start in voice" flag. That is FEATURE work → `@feature-expert` in step (b), compose-expert only renders `onMicClick`.
   - Mic semantics: `Role.Button`, contentDescription from the existing chat mic string (reuse, do not duplicate).
3. **Completed section:** `separateCompleted` defaults to `true` for checklists created from now on; existing rows keep their stored value (as spec §4).
