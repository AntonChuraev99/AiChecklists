import { test, expect } from "@playwright/test";

/**
 * E2E sync tests using Firebase anonymous auth (no Google popup).
 * Anonymous auth must be enabled in Firebase Console → Authentication → Sign-in method.
 * If not enabled, all tests skip gracefully.
 */

async function ensureAnonymousAuth(page: import("@playwright/test").Page): Promise<string | null> {
  await page.goto("/");
  await page.waitForTimeout(20_000);

  const result = await page.evaluate(async () => {
    const existing = await (globalThis as any).__getCurrentFirebaseUser();
    if (existing) return { ok: true, uid: existing.uid, source: "existing" };

    if (typeof (globalThis as any).__signInAnonymously !== "function") {
      return { ok: false, error: "__signInAnonymously bridge not available" };
    }
    const res = await (globalThis as any).__signInAnonymously();
    return { ...res, source: "anonymous" };
  });

  if (!result.ok) {
    console.log("Auth failed:", result.error);
    return null;
  }

  console.log(`Authenticated: uid=${result.uid} (${result.source})`);
  return result.uid;
}

test.describe("Gisti web — Firestore sync E2E (anonymous auth)", () => {
  test("write → read → verify round-trip", async ({ page }) => {
    const uid = await ensureAnonymousAuth(page);
    if (!uid) { test.skip(); return; }

    const collectionPath = `users/${uid}/checklists`;
    const testId = `pw-crud-${Date.now()}`;

    // Write
    const writeResult = await page.evaluate(
      async ({ path, docId }) => {
        try {
          const data = JSON.stringify({
            cloudId: docId,
            name: "PW Round-Trip Test",
            itemsJson: '[{"text":"Buy milk","id":"1"},{"text":"Walk dog","id":"2"}]',
            position: 0,
            viewMode: "Standard",
            isDeleted: false,
            fills: [],
          });
          const raw = await (globalThis as any).__firestoreSetDoc(path, docId, data);
          return typeof raw === "string" ? JSON.parse(raw) : raw;
        } catch (e: any) { return { ok: false, error: e?.message }; }
      },
      { path: collectionPath, docId: testId },
    );
    console.log("Write:", JSON.stringify(writeResult));
    expect(writeResult.ok, `Write failed: ${writeResult.error}`).toBe(true);

    // Read back
    await page.waitForTimeout(1_500);
    const readResult = await page.evaluate(
      async ({ path, docId }) => {
        try {
          const raw = await (globalThis as any).__firestoreGetDoc(path, docId);
          return typeof raw === "string" ? JSON.parse(raw) : raw;
        } catch (e: any) { return { ok: false, error: e?.message }; }
      },
      { path: collectionPath, docId: testId },
    );
    console.log("Read:", JSON.stringify(readResult).slice(0, 300));
    expect(readResult.ok, `Read failed: ${readResult.error}`).toBe(true);
    expect(readResult.data).not.toBeNull();
    expect(readResult.data.name).toBe("PW Round-Trip Test");
    expect(readResult.data.updatedAt).toBeGreaterThan(0);

    // Cleanup
    await page.evaluate(
      async ({ path, docId }) => { await (globalThis as any).__firestoreDeleteDoc(path, docId); },
      { path: collectionPath, docId: testId },
    );
  });

  test("batch write → list → verify all present", async ({ page }) => {
    const uid = await ensureAnonymousAuth(page);
    if (!uid) { test.skip(); return; }

    const collectionPath = `users/${uid}/checklists`;
    const ids = [`pw-b1-${Date.now()}`, `pw-b2-${Date.now()}`, `pw-b3-${Date.now()}`];

    const ops = ids.map((id) => ({
      collectionPath,
      docId: id,
      data: JSON.stringify({
        cloudId: id, name: `Batch ${id}`, itemsJson: "[]",
        position: 0, viewMode: "Standard", isDeleted: false, fills: [],
      }),
    }));

    const batchResult = await page.evaluate(async (json: string) => {
      try {
        const raw = await (globalThis as any).__firestoreBatchWrite(json);
        return typeof raw === "string" ? JSON.parse(raw) : raw;
      } catch (e: any) { return { ok: false, error: e?.message }; }
    }, JSON.stringify(ops));
    console.log("Batch:", JSON.stringify(batchResult));
    expect(batchResult.ok, `Batch failed: ${batchResult.error}`).toBe(true);

    // List all
    await page.waitForTimeout(2_000);
    const listResult = await page.evaluate(async (path: string) => {
      try {
        const raw = await (globalThis as any).__firestoreGetDocs(path);
        return typeof raw === "string" ? JSON.parse(raw) : raw;
      } catch (e: any) { return { ok: false, error: e?.message }; }
    }, collectionPath);
    expect(listResult.ok).toBe(true);

    for (const id of ids) {
      const found = listResult.data?.find((c: any) => c.cloudId === id);
      expect(found, `${id} must exist in list`).toBeTruthy();
    }
    console.log(`List: ${listResult.data?.length} docs, all 3 batch items found`);

    // Cleanup
    for (const id of ids) {
      await page.evaluate(
        async ({ path, docId }) => { await (globalThis as any).__firestoreDeleteDoc(path, docId); },
        { path: collectionPath, docId: id },
      );
    }
  });

  test("delete → verify gone", async ({ page }) => {
    const uid = await ensureAnonymousAuth(page);
    if (!uid) { test.skip(); return; }

    const collectionPath = `users/${uid}/checklists`;
    const testId = `pw-del-${Date.now()}`;

    // Create
    await page.evaluate(
      async ({ path, docId }) => {
        const data = JSON.stringify({ cloudId: docId, name: "Delete Me", itemsJson: "[]", fills: [] });
        await (globalThis as any).__firestoreSetDoc(path, docId, data);
      },
      { path: collectionPath, docId: testId },
    );

    // Delete
    const delResult = await page.evaluate(
      async ({ path, docId }) => {
        try {
          const raw = await (globalThis as any).__firestoreDeleteDoc(path, docId);
          return typeof raw === "string" ? JSON.parse(raw) : raw;
        } catch (e: any) { return { ok: false, error: e?.message }; }
      },
      { path: collectionPath, docId: testId },
    );
    expect(delResult.ok).toBe(true);

    // Verify gone
    await page.waitForTimeout(1_000);
    const verifyResult = await page.evaluate(
      async ({ path, docId }) => {
        try {
          const raw = await (globalThis as any).__firestoreGetDoc(path, docId);
          return typeof raw === "string" ? JSON.parse(raw) : raw;
        } catch (e: any) { return { ok: false, error: e?.message }; }
      },
      { path: collectionPath, docId: testId },
    );
    expect(verifyResult.ok).toBe(true);
    expect(verifyResult.data, "Document must be null after delete").toBeNull();
    console.log("Delete verified: document gone");
  });

  test("real-time listener fires on document change", async ({ page }) => {
    const uid = await ensureAnonymousAuth(page);
    if (!uid) { test.skip(); return; }

    const collectionPath = `users/${uid}/checklists`;
    const testId = `pw-rt-${Date.now()}`;

    // Start listener
    const listenerId = await page.evaluate(
      ({ path, cbId }) => {
        (globalThis as any).__pw_snaps = [];
        (globalThis as any).__firestoreSnapshotCallbacks.set(
          cbId,
          (_: string, payload: string) => { (globalThis as any).__pw_snaps.push(payload); },
        );
        return (globalThis as any).__firestoreOnSnapshot(path, cbId);
      },
      { path: collectionPath, cbId: "pw-rt-cb" },
    );

    await page.waitForTimeout(3_000);

    // Write — triggers snapshot
    await page.evaluate(
      async ({ path, docId }) => {
        const data = JSON.stringify({ cloudId: docId, name: "RT Test", itemsJson: "[]", fills: [] });
        await (globalThis as any).__firestoreSetDoc(path, docId, data);
      },
      { path: collectionPath, docId: testId },
    );
    await page.waitForTimeout(5_000);

    const count = await page.evaluate(() => (globalThis as any).__pw_snaps?.length ?? 0);
    console.log("Snapshots received:", count);
    expect(count, "Must receive at least 1 snapshot").toBeGreaterThan(0);

    const last = await page.evaluate(() => {
      const arr = (globalThis as any).__pw_snaps;
      return arr?.length > 0 ? JSON.parse(arr[arr.length - 1]) : null;
    });
    const found = last?.data?.find((c: any) => c.cloudId === testId);
    expect(found, "Last snapshot must contain test doc").toBeTruthy();
    console.log("Real-time listener verified");

    // Cleanup
    await page.evaluate((lid: string) => (globalThis as any).__firestoreUnsubscribe(lid), listenerId);
    await page.evaluate(
      async ({ path, docId }) => { await (globalThis as any).__firestoreDeleteDoc(path, docId); },
      { path: collectionPath, docId: testId },
    );
  });
});
