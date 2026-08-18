# MCP directory submissions (Tier-3 GEO discoverability) — Active

**Date:** 2026-07-14 · **Status:** In Progress (official registry ✅; community directories pending)
**Impact:** Medium (organic discovery of the Gisti MCP server inside AI clients)

## Goal

Make the Gisti MCP server (`https://gisti-mcp.gisti.workers.dev/mcp`) discoverable in the places MCP
users/devs browse for servers to connect to Claude → free distribution, Tier-3 of the SEO/organic
plan (`docs/plans/2026-07-14-seo-organic-growth-strategy.md`). Canonical listing metadata + exact
per-directory steps live in the **playbook**: `docs/marketing/mcp-directory-submissions-2026-07-14.md`.

## Canonical listing (paste identically everywhere)

- **Name:** Gisti — AI Checklists
- **Registry name:** `io.github.AntonChuraev99/gisti-mcp` (exact GitHub case — lowercase 403s)
- **Endpoint:** `https://gisti-mcp.gisti.workers.dev/mcp` (remote, streamable-http, Google OAuth)
- **Repo:** https://github.com/AntonChuraev99/AiChecklists · **Docs:** https://gisti-ai.com/mcp
- **Short desc (≤100 for the registry):** Read, AI-create, and edit your Gisti checklists in plain language from any MCP client.
- **Tags:** checklists, productivity, tasks, ai, todo, google-oauth, remote

## Checklist

- [x] **Official MCP Registry** (registry.modelcontextprotocol.io) — published `io.github.AntonChuraev99/gisti-mcp` v0.4.0 (2026-07-14, via `mcp-publisher`). `mcp-server/server.json` committed (`b82e0ca7`). Verify: `curl "https://registry.modelcontextprotocol.io/v0.1/servers?search=gisti"`.
- [ ] **glama.ai/mcp** — auto-indexes public GitHub repos. **Check first:** https://glama.ai/mcp/servers?query=gisti — only submit the repo URL manually if absent after ~1 week (many dirs mirror the official registry, so it may appear on its own).
- [ ] **punkpeye/awesome-mcp-servers** — GitHub-markdown PR. `gh` is authenticated as `AntonChuraev99`; ready recipe in the playbook. Outward-facing contribution under the owner's identity → run on explicit go («отправь PR»).
- [ ] **mcp.so** — community directory (~20k servers), submit via their GitHub-issue flow. Category: Productivity.
- [ ] **smithery.ai** — optional (account + repo connect; adds hosted execution). Lower priority for pure discovery.

## Notes

- Keep the name string byte-identical everywhere so dirs that de-dupe by name collapse to one entity.
- Do NOT fabricate ratings/reviews on any directory (registry + Google spam policy).
- Since the official registry (done) is authoritative and several directories mirror it, the community
  submissions are **incremental reach**, not a hard requirement — do glama-check first, then the rest as time allows.

## Resume

User says «отправь PR / сабмить mcp директории / mcp.so / glama / доделай mcp discovery».
