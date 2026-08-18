# ast-index Rules

## Mandatory Search Rules

1. **ALWAYS use ast-index FIRST** for any code-discovery task (symbols, files, usages).
2. **NEVER duplicate results** — if ast-index returned usages/implementations, that IS the complete answer.
3. **DO NOT run grep "for completeness"** after ast-index returns results.
4. **Use grep/Grep ONLY when:**
   - ast-index returns empty results
   - regex patterns (ast-index is literal match)
   - string literals inside code (`"some text"`)
   - searching comment content

## Why ast-index

17-69x faster than grep (1-10ms vs 200ms-3s) and returns structured `file:line` results.

## Command Reference

| Task | Command |
|------|---------|
| Universal search | `ast-index search "query"` |
| Find file | `ast-index file "Name"` |
| Find class | `ast-index class "ClassName"` |
| Find usages | `ast-index usages "SymbolName"` |
| Find implementations | `ast-index implementations "Interface"` |
| Find callers | `ast-index callers "functionName"` |
| Call hierarchy | `ast-index call-tree "function" --depth 3` |
| Class hierarchy | `ast-index hierarchy "ClassName"` |
| Module deps | `ast-index deps "module-name"` |
| File outline (before reading >500 lines) | `ast-index outline <file>` |

## KMP / Compose commands

| Task | Command |
|------|---------|
| Composables | `ast-index composables` |
| Suspend functions | `ast-index suspend` |
| Flows | `ast-index flows` |

> **DI note:** this project uses **Koin**, not Hilt/Dagger — the `provides`/`inject` Dagger
> subcommands apply only to the `androidMain` Hilt EntryPoint bridges, not to common DI.

## Index Management

- `ast-index rebuild` — full reindex (cold-start after clone only).
- `ast-index update` — after `git pull`/merge.
- `ast-index stats` — index statistics.

> Auto-refresh is handled by the plugin hooks (PostToolUse on Edit/Write + SessionStart-refresh).
> Manual `rebuild` is **not** needed in normal work.
