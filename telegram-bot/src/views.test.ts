import { describe, expect, it } from "vitest";
import { emptyRegistry, resolveKey, decodeCallback } from "./callback";
import { t } from "./i18n";
import { checklistRows, ITEMS_PAGE_SIZE, progress, renderItemsPage, renderListsPage, LISTS_PAGE_SIZE } from "./views";
import { buildAiChecklist, createEmptyChecklist, toggleItem } from "../../mcp-server/src/mutate";
import { parseTemplateItems } from "../../mcp-server/src/model";

describe("checklistRows", () => {
  it("walks the template tree depth-first with the default fill's checked state", () => {
    const c = buildAiChecklist(
      "Trip",
      [{ text: "Docs", type: "folder", children: [{ text: "Passport" }] }, { text: "Charger" }],
      1,
    );
    const passport = parseTemplateItems(c.itemsJson).find((i) => i.text === "Passport")!;
    const toggled = toggleItem(c, passport.id, true, 2)!;
    const rows = checklistRows(toggled);
    expect(rows.map((r) => [r.kind, r.text, r.depth])).toEqual([
      ["folder", "Docs", 0],
      ["item", "Passport", 1],
      ["item", "Charger", 0],
    ]);
    expect(progress(rows)).toEqual({ done: 1, total: 2 });
  });
});

describe("renderItemsPage", () => {
  it("renders 10 rows per page, buttons carry the target state and a key that resolves to the item", () => {
    const c = createEmptyChecklist("Big", Array.from({ length: 23 }, (_, i) => `item ${i}`), 1);
    const keys = emptyRegistry();
    const v = renderItemsPage(c, 2, t("en"), keys);
    const itemRows = v.keyboard.inline_keyboard.filter((r) => r[0] && "callback_data" in r[0] && r[0].callback_data.startsWith("t:"));
    expect(itemRows).toHaveLength(23 - 2 * ITEMS_PAGE_SIZE);
    const first = itemRows[0]![0]!;
    const cb = decodeCallback("callback_data" in first ? first.callback_data : "");
    expect(cb).toMatchObject({ t: "toggle", checked: true, page: 2 });
    const target = resolveKey(keys, cb && cb.t === "toggle" ? cb.key : "");
    expect(target?.c).toBe(c.cloudId);
    expect(target?.i).toBe(parseTemplateItems(c.itemsJson)[20]!.id);
    expect(v.text).toContain("page 3/3");
  });

  it("clamps an out-of-range page", () => {
    const c = createEmptyChecklist("Small", ["a"], 1);
    expect(renderItemsPage(c, 99, t("en"), emptyRegistry()).text).toContain("page 1/1");
  });
});

describe("renderListsPage", () => {
  it("paginates by 8, ordered by position", () => {
    const list = Array.from({ length: 10 }, (_, i) => {
      const c = createEmptyChecklist(`L${i}`, ["x"], 1);
      c.position = 10 - i;
      return c;
    });
    const v = renderListsPage(list, 0, t("ru"), emptyRegistry());
    const rows = v.keyboard.inline_keyboard;
    expect(rows).toHaveLength(LISTS_PAGE_SIZE + 1); // + nav row
    expect(rows[0]![0]!.text.startsWith("L9")).toBe(true);
    expect(v.text).toBe("Ваши чек-листы (1/2):");
  });
});
