/**
 * "Checklist from lines" — the free, no-AI path: every non-empty line becomes an item.
 *
 * Rules (contract, "Worker"):
 *  - list markers are stripped: `-`, `•`, `*`, `–`, `—`, `1.`, `1)`, `[ ]`, `[x]` (and combos like `- [ ]`);
 *  - empty lines (and lines that were only a marker) are dropped;
 *  - if the FIRST non-empty line ends with `:`, it is the title, not an item;
 *  - otherwise the caller's localized default name is used.
 * Items are capped at the app's per-checklist limit (`truncated` tells the caller to say so).
 */

/** Items per checklist — `max_items_per_checklist` (RemoteConfigKeys.kt), same for Free and Premium. */
export const MAX_ITEMS = 100;

/** Longest title we keep; a "title" longer than this is almost certainly a sentence, not a name. */
const MAX_TITLE_CHARS = 100;

export interface ParsedLines {
  title: string;
  items: string[];
  /** True when more than MAX_ITEMS lines were found and the tail was dropped. */
  truncated: boolean;
}

/**
 * One leading marker: bullet glyphs need trailing whitespace for `-`/`*` (so "-5°C" or "*important*"
 * survive), numbering needs whitespace too (so "1.5 kg" survives), checkboxes are taken as-is.
 */
const MARKER = /^(?:[-*–—](?:\s+|$)|•\s*|\d{1,3}[.)](?:\s+|$)|\[(?:\s|x|X)?\]\s*|[☐☑✅✔️]+\s*)/u;

/** Strip up to three stacked markers ("1. [ ] milk", "- [x] eggs"). */
export function stripMarkers(line: string): string {
  let s = line.trim();
  for (let i = 0; i < 3; i++) {
    const next = s.replace(MARKER, "").trim();
    if (next === s) break;
    s = next;
  }
  return s;
}

export function parseLines(text: string, defaultTitle: string): ParsedLines {
  const raw = text.split(/\r?\n/).map((l) => l.trim()).filter((l) => l.length > 0);

  let title = defaultTitle;
  let start = 0;
  const first = raw[0];
  if (first !== undefined && first.endsWith(":")) {
    const candidate = stripMarkers(first.slice(0, -1)).trim();
    if (candidate.length > 0 && candidate.length <= MAX_TITLE_CHARS) {
      title = candidate;
      start = 1;
    }
  }

  const all = raw
    .slice(start)
    .map(stripMarkers)
    .filter((l) => l.length > 0);

  return {
    title,
    items: all.slice(0, MAX_ITEMS),
    truncated: all.length > MAX_ITEMS,
  };
}
