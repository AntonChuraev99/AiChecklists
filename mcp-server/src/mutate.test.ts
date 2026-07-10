/**
 * Unit coverage for the domain mutations (mutate.ts) — verifies the sync invariants the app
 * relies on (dirty-parent bump, templateItemId on every added item, folder-child promotion on
 * delete, AI tree flattening, filled-item mapping by index) WITHOUT any network.
 */
import { describe, expect, it } from "vitest";
import type { GeneratedNode } from "./cf";
import {
  addItem,
  applyFilledItems,
  buildAiChecklist,
  createEmptyChecklist,
  createNamedFill,
  deleteItem,
  editNote,
  findFill,
  listFills,
  renameChecklist,
  renameItem,
  reorderItems,
  softDeleteChecklist,
  toggleItem,
  treeToTemplateItems,
} from "./mutate";
import { defaultFill, parseFillItems, parseTemplateItems, type ChecklistSyncData } from "./model";

const T0 = 1_720_000_000_000;

function template(c: ChecklistSyncData) {
  return parseTemplateItems(c.itemsJson);
}
function fillItems(c: ChecklistSyncData) {
  return parseFillItems(defaultFill(c)!.itemsJson);
}

describe("createEmptyChecklist", () => {
  it("builds template + a mirrored default fill with templateItemId on every item", () => {
    const c = createEmptyChecklist("Groceries", ["Milk", "Eggs", "  ", "Bread"], T0);
    const t = template(c);
    expect(t.map((i) => i.text)).toEqual(["Milk", "Eggs", "Bread"]); // blank dropped
    const fi = fillItems(c);
    expect(fi).toHaveLength(3);
    expect(fi.every((f) => f.templateItemId !== null)).toBe(true);
    expect(fi.map((f) => f.templateItemId)).toEqual(t.map((i) => i.id));
    expect(defaultFill(c)!.isDefault).toBe(true);
    expect(c.foldersEnabled).toBe(false);
  });
});

describe("treeToTemplateItems / buildAiChecklist", () => {
  const tree: GeneratedNode[] = [
    { text: "Passport", checked: false },
    { text: "Clothing", type: "folder", children: [{ text: "Jacket", checked: false }, { text: "Socks", checked: false }] },
  ];

  it("flattens the nested tree with parentId + FOLDER type", () => {
    const items = treeToTemplateItems(tree);
    expect(items.map((i) => `${i.text}:${i.type}`)).toEqual([
      "Passport:ITEM",
      "Clothing:FOLDER",
      "Jacket:ITEM",
      "Socks:ITEM",
    ]);
    const folder = items.find((i) => i.type === "FOLDER")!;
    expect(items.filter((i) => i.parentId === folder.id).map((i) => i.text)).toEqual(["Jacket", "Socks"]);
    expect(items.find((i) => i.text === "Passport")!.parentId).toBeNull();
  });

  it("builds an AI checklist with foldersEnabled and a full mirrored fill (folders included)", () => {
    const c = buildAiChecklist("Ski Trip", tree, T0);
    expect(c.foldersEnabled).toBe(true);
    expect(template(c)).toHaveLength(4);
    expect(fillItems(c)).toHaveLength(4); // folder mirrored as a flat fill item too
  });
});

describe("applyFilledItems", () => {
  it("maps by text regardless of 1-based index or return order (the live-found bug)", () => {
    const c = createEmptyChecklist("Viewing", ["Water pressure", "Windows", "Doors"], T0);
    const before = c.updatedAt;
    // Model returns 1-BASED index, shuffled order — index as-a-0-based-array-index would shift/drop.
    const out = applyFilledItems(
      c,
      [
        { index: 2, text: "Windows", checked: true, note: "new" },
        { index: 1, text: "Water pressure", checked: true, note: "strong" },
        { index: 3, text: "Doors", checked: false, note: null },
      ],
      T0 + 5,
    );
    const fi = fillItems(out);
    expect(fi[0]).toMatchObject({ text: "Water pressure", checked: true, note: "strong" });
    expect(fi[1]).toMatchObject({ text: "Windows", checked: true, note: "new" });
    expect(fi[2]).toMatchObject({ text: "Doors", checked: false, note: null });
    expect(out.updatedAt).toBeGreaterThan(before); // dirty-parent
    expect(defaultFill(out)!.updatedAt).toBeGreaterThan(before);
  });

  it("matches case-insensitively then falls back to numeric index when text is absent", () => {
    const c = createEmptyChecklist("T", ["Milk", "Eggs"], T0);
    const out = applyFilledItems(
      c,
      [
        { index: 1, text: "milk", checked: true, note: null }, // case-insensitive → Milk
        { index: 2, text: "totally different", checked: true, note: "x" }, // no text → index 2→(2-1)=Eggs
      ],
      T0 + 1,
    );
    const fi = fillItems(out);
    expect(fi.find((f) => f.text === "Milk")!.checked).toBe(true);
    expect(fi.find((f) => f.text === "Eggs")!).toMatchObject({ checked: true, note: "x" });
  });
});

describe("toggleItem", () => {
  it("sets checked by templateItemId and bumps BOTH fill and checklist updatedAt", () => {
    const c = createEmptyChecklist("T", ["a", "b"], T0);
    const id = template(c)[0]!.id;
    const out = toggleItem(c, id, true, T0 + 10)!;
    expect(fillItems(out).find((f) => f.templateItemId === id)!.checked).toBe(true);
    expect(out.updatedAt).toBeGreaterThan(c.updatedAt);
    expect(defaultFill(out)!.updatedAt).toBeGreaterThan(defaultFill(c)!.updatedAt);
  });

  it("returns null for an unknown item id", () => {
    const c = createEmptyChecklist("T", ["a"], T0);
    expect(toggleItem(c, "nope", true, T0 + 1)).toBeNull();
  });

  it("advances updatedAt even when now equals the current stamp (monotonic)", () => {
    const c = createEmptyChecklist("T", ["a"], T0);
    const id = template(c)[0]!.id;
    const out = toggleItem(c, id, true, T0)!; // same now
    expect(out.updatedAt).toBe(c.updatedAt + 1);
  });
});

describe("addItem", () => {
  it("adds a leaf to template + fill with templateItemId linking them", () => {
    const c = createEmptyChecklist("T", ["a"], T0);
    const { checklist: out, itemId } = addItem(c, "b", null, T0 + 1);
    expect(template(out).map((i) => i.text)).toEqual(["a", "b"]);
    const fi = fillItems(out);
    expect(fi.map((f) => f.text)).toEqual(["a", "b"]);
    expect(fi.find((f) => f.templateItemId === itemId)).toBeTruthy();
  });

  it("nests under a folder parentId, ignores a non-folder/absent parent", () => {
    const c = buildAiChecklist("T", [{ text: "F", type: "folder", children: [] }], T0);
    const folderId = template(c).find((i) => i.type === "FOLDER")!.id;
    const nested = addItem(c, "child", folderId, T0 + 1);
    expect(template(nested.checklist).find((i) => i.id === nested.itemId)!.parentId).toBe(folderId);
    const rootAdded = addItem(c, "loose", "bogus", T0 + 1);
    expect(template(rootAdded.checklist).find((i) => i.id === rootAdded.itemId)!.parentId).toBeNull();
  });
});

describe("renameItem / editNote", () => {
  it("renames both template and mirrored fill item", () => {
    const c = createEmptyChecklist("T", ["old"], T0);
    const id = template(c)[0]!.id;
    const out = renameItem(c, id, "new", T0 + 1)!;
    expect(template(out)[0]!.text).toBe("new");
    expect(fillItems(out)[0]!.text).toBe("new");
  });

  it("sets and clears a note", () => {
    const c = createEmptyChecklist("T", ["x"], T0);
    const id = template(c)[0]!.id;
    const withNote = editNote(c, id, "hello", T0 + 1)!;
    expect(fillItems(withNote)[0]!.note).toBe("hello");
    const cleared = editNote(withNote, id, "", T0 + 2)!;
    expect(fillItems(cleared)[0]!.note).toBeNull();
  });
});

describe("deleteItem", () => {
  it("removes the item from template + fill", () => {
    const c = createEmptyChecklist("T", ["a", "b", "c"], T0);
    const id = template(c)[1]!.id;
    const out = deleteItem(c, id, T0 + 1)!;
    expect(template(out).map((i) => i.text)).toEqual(["a", "c"]);
    expect(fillItems(out).map((f) => f.text)).toEqual(["a", "c"]);
  });

  it("promotes a deleted folder's direct children to its parent (no dangling parentId)", () => {
    const c = buildAiChecklist("T", [{ text: "F", type: "folder", children: [{ text: "child", checked: false }] }], T0);
    const folderId = template(c).find((i) => i.type === "FOLDER")!.id;
    const out = deleteItem(c, folderId, T0 + 1)!;
    const child = template(out).find((i) => i.text === "child")!;
    expect(child.parentId).toBeNull(); // promoted to the folder's parent (root)
  });
});

describe("reorderItems", () => {
  it("reorders template + fill to the given id order", () => {
    const c = createEmptyChecklist("T", ["a", "b", "c"], T0);
    const ids = template(c).map((i) => i.id);
    const out = reorderItems(c, [ids[2]!, ids[0]!, ids[1]!], T0 + 1)!;
    expect(template(out).map((i) => i.text)).toEqual(["c", "a", "b"]);
    expect(fillItems(out).map((f) => f.text)).toEqual(["c", "a", "b"]);
  });

  it("returns null when no id matches", () => {
    const c = createEmptyChecklist("T", ["a"], T0);
    expect(reorderItems(c, ["x", "y"], T0 + 1)).toBeNull();
  });
});

describe("renameChecklist / softDeleteChecklist", () => {
  it("renames the envelope and bumps updatedAt", () => {
    const c = createEmptyChecklist("Old", [], T0);
    const out = renameChecklist(c, "New", T0 + 1);
    expect(out.name).toBe("New");
    expect(out.updatedAt).toBeGreaterThan(c.updatedAt);
  });

  it("soft-deletes (isDeleted=true)", () => {
    const c = createEmptyChecklist("X", [], T0);
    const out = softDeleteChecklist(c, T0 + 1);
    expect(out.isDeleted).toBe(true);
  });
});

describe("named fills (Phase 3)", () => {
  it("createNamedFill adds a non-default fill mirroring the template, unchecked, with templateItemId", () => {
    const c = createEmptyChecklist("Trip", ["Passport", "Charger"], T0);
    const before = c.updatedAt;
    const { checklist: out, fillId } = createNamedFill(c, "  March trip  ", T0 + 5);
    const fill = out.fills.find((f) => f.cloudId === fillId)!;
    expect(fill.isDefault).toBe(false);
    expect(fill.name).toBe("March trip"); // trimmed
    const items = parseFillItems(fill.itemsJson);
    expect(items.map((i) => i.text)).toEqual(["Passport", "Charger"]);
    expect(items.every((i) => i.templateItemId !== null && !i.checked)).toBe(true);
    expect(items.map((i) => i.templateItemId)).toEqual(template(out).map((t) => t.id)); // links match template
    expect(out.updatedAt).toBeGreaterThan(before); // dirty-parent
    expect(out.fills).toHaveLength(2); // default + named
  });

  it("listFills reports default + named with checked/total counts", () => {
    const c = createEmptyChecklist("Trip", ["a", "b", "c"], T0);
    const id = template(c)[0]!.id;
    const toggled = toggleItem(c, id, true, T0 + 1)!; // default fill: 1/3 checked
    const { checklist: withFill, fillId } = createNamedFill(toggled, "Second", T0 + 2);
    const fills = listFills(withFill);
    expect(fills).toHaveLength(2);
    expect(fills.find((f) => f.isDefault)!).toMatchObject({ total: 3, checked: 1, name: "" });
    expect(fills.find((f) => f.fillId === fillId)!).toMatchObject({
      total: 3,
      checked: 0,
      name: "Second",
      isDefault: false,
    });
  });

  it("toggle_item with fillId targets ONLY that fill, leaving the default untouched", () => {
    const c = createEmptyChecklist("Trip", ["a", "b"], T0);
    const { checklist: withFill, fillId } = createNamedFill(c, "S", T0 + 1);
    const itemId = template(withFill)[0]!.id;
    const out = toggleItem(withFill, itemId, true, T0 + 2, fillId)!;
    // named fill checked, default fill NOT touched
    expect(parseFillItems(findFill(out, fillId)!.itemsJson).find((i) => i.templateItemId === itemId)!.checked).toBe(true);
    expect(fillItems(out).find((i) => i.templateItemId === itemId)!.checked).toBe(false);
  });

  it("edit_note with fillId targets that fill, default untouched", () => {
    const c = createEmptyChecklist("Trip", ["a"], T0);
    const { checklist: withFill, fillId } = createNamedFill(c, "S", T0 + 1);
    const itemId = template(withFill)[0]!.id;
    const out = editNote(withFill, itemId, "hi", T0 + 2, fillId)!;
    expect(parseFillItems(findFill(out, fillId)!.itemsJson)[0]!.note).toBe("hi");
    expect(fillItems(out)[0]!.note).toBeNull();
  });

  it("state ops return null for an unknown fillId", () => {
    const c = createEmptyChecklist("Trip", ["a"], T0);
    const itemId = template(c)[0]!.id;
    expect(toggleItem(c, itemId, true, T0 + 1, "no-such-fill")).toBeNull();
    expect(editNote(c, itemId, "x", T0 + 1, "no-such-fill")).toBeNull();
  });

  it("findFill ignores deleted fills", () => {
    const c = createEmptyChecklist("Trip", ["a"], T0);
    const { checklist: withFill, fillId } = createNamedFill(c, "S", T0 + 1);
    expect(findFill(withFill, fillId)).not.toBeNull();
    withFill.fills.find((f) => f.cloudId === fillId)!.isDeleted = true;
    expect(findFill(withFill, fillId)).toBeNull();
  });
});
