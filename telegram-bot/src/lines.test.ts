import { describe, expect, it } from "vitest";
import { MAX_ITEMS, parseLines, stripMarkers } from "./lines";

const DEFAULT = "Checklist from Telegram";

describe("parseLines", () => {
  it("turns each line into an item and drops empty lines", () => {
    expect(parseLines("milk\n\neggs\r\n  bread  \n", DEFAULT)).toEqual({
      title: DEFAULT,
      items: ["milk", "eggs", "bread"],
      truncated: false,
    });
  });

  it("strips every supported list marker", () => {
    const text = ["- milk", "• eggs", "* bread", "1. butter", "2) cheese", "[ ] jam", "[x] tea", "[X] honey", "- [ ] salt"].join("\n");
    expect(parseLines(text, DEFAULT).items).toEqual([
      "milk",
      "eggs",
      "bread",
      "butter",
      "cheese",
      "jam",
      "tea",
      "honey",
      "salt",
    ]);
  });

  it("uses a first line ending with ':' as the title", () => {
    expect(parseLines("Shopping:\n- milk\n- eggs", DEFAULT)).toEqual({
      title: "Shopping",
      items: ["milk", "eggs"],
      truncated: false,
    });
  });

  it("does not treat a later line ending with ':' as the title", () => {
    const r = parseLines("milk\nFruit:\napples", DEFAULT);
    expect(r.title).toBe(DEFAULT);
    expect(r.items).toEqual(["milk", "Fruit:", "apples"]);
  });

  it("drops lines that are only a marker", () => {
    expect(parseLines("-\n[ ]\n1.\n•\nmilk", DEFAULT).items).toEqual(["milk"]);
  });

  it("keeps hyphens and numbers that are content, not markers", () => {
    expect(stripMarkers("-5°C outside")).toBe("-5°C outside");
    expect(stripMarkers("1.5 kg flour")).toBe("1.5 kg flour");
    expect(stripMarkers("*important* call")).toBe("*important* call");
  });

  it("caps items at the per-checklist limit and reports truncation", () => {
    const text = Array.from({ length: MAX_ITEMS + 5 }, (_, i) => `item ${i}`).join("\n");
    const r = parseLines(text, DEFAULT);
    expect(r.items).toHaveLength(MAX_ITEMS);
    expect(r.truncated).toBe(true);
  });
});
