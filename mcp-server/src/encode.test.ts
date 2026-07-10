/**
 * Kotlin↔TS byte-equality contract test — the GATE before any write ships.
 *
 * The golden strings below are the EXACT bytes the Kotlin app writes, hand-traced from source
 * (file:line cited in the design doc / encode.ts). If the TS encoder ever diverges from what
 * the app produces, sync corruption follows silently — so this test pins it byte-for-byte.
 *
 * Coverage: (1) the canonical "Trip" fixture — folder tree + default fill with templateItemId
 * + full Firestore doc; (2) omit-defaults edge cases; (3) decode→encode round-trip identity on
 * a rich fill item (attachments + item-level repeatRule) to guard verbatim pass-through.
 *
 * ⚠ Kotlin-side mirror: a future new sync field will break THIS test only if the golden is also
 * regenerated from Kotlin. A companion commonTest that emits these goldens from the real
 * serializer is the drift alarm (see design doc — tracked as a follow-up).
 */
import { describe, expect, it } from "vitest";
import {
  CHECKLIST_DOC_FIELD_PATHS,
  encodeFillItems,
  encodeTemplateItems,
  toChecklistDoc,
  wrapFields,
  wrapValue,
} from "./encode";
import {
  parseFillItems,
  parseTemplateItems,
  type ChecklistFillItem,
  type ChecklistItem,
  type ChecklistSyncData,
} from "./model";

// ── Golden bytes (from the Kotlin write contract, "Trip" fixture) ────────────────

const BAGS = "1720000000001_1000";
const PASSPORT = "1720000000002_2000";
const CHARGER = "1720000000003_3000";

const GOLDEN_TEMPLATE_JSON =
  `[{"text":"Bags","id":"${BAGS}","type":"FOLDER"},` +
  `{"text":"Passport","id":"${PASSPORT}","parentId":"${BAGS}"},` +
  `{"text":"Charger","id":"${CHARGER}","parentId":"${BAGS}"}]`;

const GOLDEN_FILL_JSON =
  `[{"text":"Bags","checked":false,"id":"1720000000010_5000","templateItemId":"${BAGS}"},` +
  `{"text":"Passport","checked":true,"id":"1720000000011_6000","templateItemId":"${PASSPORT}"},` +
  `{"text":"Charger","checked":false,"id":"1720000000012_7000","templateItemId":"${CHARGER}"}]`;

/** Build a template item with the Kotlin defaults, overridden by `p`. */
function templateItem(p: Partial<ChecklistItem> & { text: string; id: string }): ChecklistItem {
  return {
    text: p.text,
    checked: p.checked ?? false,
    id: p.id,
    weekday: p.weekday ?? null,
    priority: p.priority ?? 0,
    type: p.type ?? "ITEM",
    parentId: p.parentId ?? null,
  };
}

/** Build a fill item with the Kotlin defaults, overridden by `p`. */
function fillItem(p: Partial<ChecklistFillItem> & { text: string; id: string; checked: boolean }): ChecklistFillItem {
  return {
    text: p.text,
    checked: p.checked,
    note: p.note ?? null,
    id: p.id,
    weekday: p.weekday ?? null,
    priority: p.priority ?? 0,
    reminderAt: p.reminderAt ?? null,
    repeatRule: p.repeatRule ?? null,
    repeatTimeOfDayMinutes: p.repeatTimeOfDayMinutes ?? null,
    repeatNextAt: p.repeatNextAt ?? null,
    repeatOccurrenceCount: p.repeatOccurrenceCount ?? 0,
    attachments: p.attachments ?? [],
    templateItemId: p.templateItemId ?? null,
  };
}

const TRIP_TEMPLATE: ChecklistItem[] = [
  templateItem({ text: "Bags", id: BAGS, type: "FOLDER" }),
  templateItem({ text: "Passport", id: PASSPORT, parentId: BAGS }),
  templateItem({ text: "Charger", id: CHARGER, parentId: BAGS }),
];

const TRIP_FILL: ChecklistFillItem[] = [
  fillItem({ text: "Bags", checked: false, id: "1720000000010_5000", templateItemId: BAGS }),
  fillItem({ text: "Passport", checked: true, id: "1720000000011_6000", templateItemId: PASSPORT }),
  fillItem({ text: "Charger", checked: false, id: "1720000000012_7000", templateItemId: CHARGER }),
];

const TRIP_CHECKLIST: ChecklistSyncData = {
  cloudId: "c0000000-0000-4000-8000-000000000001",
  name: "Trip",
  itemsJson: GOLDEN_TEMPLATE_JSON,
  reminderAt: null,
  repeatRule: null,
  repeatTimeOfDayMinutes: null,
  repeatNextAt: null,
  repeatOccurrenceCount: 0,
  separateCompleted: false,
  position: 0,
  autoDeleteCompleted: false,
  viewMode: "Standard",
  foldersEnabled: true,
  updatedAt: 1720000000500,
  isDeleted: false,
  fills: [
    {
      cloudId: "f0000000-0000-4000-8000-000000000002",
      name: "",
      itemsJson: GOLDEN_FILL_JSON,
      coverImagePath: null,
      createdAt: 1720000000500,
      isDefault: true,
      updatedAt: 1720000000500,
      isDeleted: false,
    },
  ],
};

// ── Tests ────────────────────────────────────────────────────────────────────────

describe("itemsJson encoding (byte-equal to Kotlin kotlinx)", () => {
  it("encodes the template tree exactly (omit checked=false/priority=0, type only on FOLDER)", () => {
    expect(encodeTemplateItems(TRIP_TEMPLATE)).toBe(GOLDEN_TEMPLATE_JSON);
  });

  it("encodes the default fill exactly (checked always present even when false)", () => {
    expect(encodeFillItems(TRIP_FILL)).toBe(GOLDEN_FILL_JSON);
  });

  it("omits every field equal to its Kotlin default (leaf item → only text+id)", () => {
    expect(encodeTemplateItems([templateItem({ text: "Milk", id: "x1" })])).toBe(
      `[{"text":"Milk","id":"x1"}]`,
    );
  });

  it("emits non-default template fields in constructor order (checked,weekday,priority,parentId)", () => {
    const out = encodeTemplateItems([
      templateItem({ text: "T", id: "id9", checked: true, weekday: 3, priority: 1, parentId: "p1" }),
    ]);
    expect(out).toBe(`[{"text":"T","checked":true,"id":"id9","weekday":3,"priority":1,"parentId":"p1"}]`);
  });

  it("a minimal fill item still emits checked (no default)", () => {
    expect(encodeFillItems([fillItem({ text: "A", id: "f1", checked: false })])).toBe(
      `[{"text":"A","checked":false,"id":"f1"}]`,
    );
  });
});

describe("Firestore document map (all keys present, null → nullValue)", () => {
  it("builds the full checklist doc with every key in order and nulls preserved", () => {
    const doc = toChecklistDoc(TRIP_CHECKLIST);
    expect(Object.keys(doc)).toEqual([...CHECKLIST_DOC_FIELD_PATHS]);
    expect(doc).toEqual({
      cloudId: "c0000000-0000-4000-8000-000000000001",
      name: "Trip",
      itemsJson: GOLDEN_TEMPLATE_JSON,
      reminderAt: null,
      repeatRule: null,
      repeatTimeOfDayMinutes: null,
      repeatNextAt: null,
      repeatOccurrenceCount: 0,
      separateCompleted: false,
      position: 0,
      autoDeleteCompleted: false,
      viewMode: "Standard",
      foldersEnabled: true,
      updatedAt: 1720000000500,
      isDeleted: false,
      fills: [
        {
          cloudId: "f0000000-0000-4000-8000-000000000002",
          name: "",
          itemsJson: GOLDEN_FILL_JSON,
          coverImagePath: null,
          createdAt: 1720000000500,
          isDefault: true,
          updatedAt: 1720000000500,
          isDeleted: false,
        },
      ],
    });
  });
});

describe("Firestore REST value wrapping (int64 millis, nullValue, nested)", () => {
  it("wraps integers as string-encoded integerValue and nulls as nullValue", () => {
    expect(wrapValue(1720000000500)).toEqual({ integerValue: "1720000000500" });
    expect(wrapValue(0)).toEqual({ integerValue: "0" });
    expect(wrapValue(null)).toEqual({ nullValue: null });
    expect(wrapValue("Trip")).toEqual({ stringValue: "Trip" });
    expect(wrapValue(true)).toEqual({ booleanValue: true });
  });

  it("wraps the fills array as arrayValue → mapValue with null fields kept", () => {
    const fields = wrapFields(toChecklistDoc(TRIP_CHECKLIST));
    expect(fields["reminderAt"]).toEqual({ nullValue: null });
    expect(fields["foldersEnabled"]).toEqual({ booleanValue: true });
    expect(fields["updatedAt"]).toEqual({ integerValue: "1720000000500" });
    const fillsVal = fields["fills"] as { arrayValue: { values: unknown[] } };
    expect(fillsVal.arrayValue.values).toHaveLength(1);
    const fill0 = fillsVal.arrayValue.values[0] as { mapValue: { fields: Record<string, unknown> } };
    expect(fill0.mapValue.fields["isDefault"]).toEqual({ booleanValue: true });
    expect(fill0.mapValue.fields["coverImagePath"]).toEqual({ nullValue: null });
  });
});

describe("decode→encode round-trip identity (verbatim pass-through)", () => {
  it("re-emits the Trip goldens byte-for-byte after a parse", () => {
    expect(encodeTemplateItems(parseTemplateItems(GOLDEN_TEMPLATE_JSON))).toBe(GOLDEN_TEMPLATE_JSON);
    expect(encodeFillItems(parseFillItems(GOLDEN_FILL_JSON))).toBe(GOLDEN_FILL_JSON);
  });

  it("preserves a rich fill item (note, priority, item-level repeatRule object, attachments)", () => {
    const rich =
      `[{"text":"Water plants","checked":false,"note":"balcony","id":"r1","priority":1,` +
      `"reminderAt":1720000000000,` +
      `"repeatRule":{"type":"weekly","interval":2,"weekDays":[1,5],"endCondition":{"type":"after_count","maxCount":5}},` +
      `"repeatOccurrenceCount":2,` +
      `"attachments":[{"id":"a1","path":"/local/x.jpg","fileName":"x.jpg","sizeBytes":1234,"createdAt":1720000000000,"storagePath":"cloud/x.jpg"}],` +
      `"templateItemId":"t1"}]`;
    expect(encodeFillItems(parseFillItems(rich))).toBe(rich);
  });
});
