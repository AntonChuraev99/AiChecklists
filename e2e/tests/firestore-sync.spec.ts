import { test, expect } from "@playwright/test";

test.describe("Gisti web — Firestore sync bridges", () => {
  test("init.js contains Firestore ESM imports", async ({ request }) => {
    const res = await request.get("/init.js");
    expect(res.ok()).toBe(true);
    const body = await res.text();
    expect(body).toContain("firebase-firestore.js");
    expect(body).toContain("getFirestore");
    expect(body).toContain("__firestoreSetDoc");
    expect(body).toContain("__firestoreGetDoc");
    expect(body).toContain("__firestoreGetDocs");
    expect(body).toContain("__firestoreDeleteDoc");
    expect(body).toContain("__firestoreBatchWrite");
    expect(body).toContain("__firestoreOnSnapshot");
    expect(body).toContain("__firestoreOnDocSnapshot");
    expect(body).toContain("__firestoreUnsubscribe");
    expect(body).toContain("serverTimestamp");
  });

  test("init.js contains Google Auth bridges", async ({ request }) => {
    const res = await request.get("/init.js");
    expect(res.ok()).toBe(true);
    const body = await res.text();
    expect(body).toContain("__googleSignIn");
    expect(body).toContain("__firebaseSignOut");
    expect(body).toContain("__getCurrentFirebaseUser");
    expect(body).toContain("__authReadyPromise");
    expect(body).toContain("signInWithPopup");
  });

  test("globalThis Firestore bridges are defined after page load", async ({ page }) => {
    await page.goto("/");
    await page.waitForTimeout(15_000);

    const bridges = await page.evaluate(() => ({
      hasSetDoc: typeof (globalThis as any).__firestoreSetDoc === "function",
      hasGetDoc: typeof (globalThis as any).__firestoreGetDoc === "function",
      hasGetDocs: typeof (globalThis as any).__firestoreGetDocs === "function",
      hasDeleteDoc: typeof (globalThis as any).__firestoreDeleteDoc === "function",
      hasBatchWrite: typeof (globalThis as any).__firestoreBatchWrite === "function",
      hasOnSnapshot: typeof (globalThis as any).__firestoreOnSnapshot === "function",
      hasOnDocSnapshot: typeof (globalThis as any).__firestoreOnDocSnapshot === "function",
      hasUnsubscribe: typeof (globalThis as any).__firestoreUnsubscribe === "function",
      hasListeners: typeof (globalThis as any).__firestoreListeners !== "undefined",
    }));

    expect(bridges.hasSetDoc, "__firestoreSetDoc").toBe(true);
    expect(bridges.hasGetDoc, "__firestoreGetDoc").toBe(true);
    expect(bridges.hasGetDocs, "__firestoreGetDocs").toBe(true);
    expect(bridges.hasDeleteDoc, "__firestoreDeleteDoc").toBe(true);
    expect(bridges.hasBatchWrite, "__firestoreBatchWrite").toBe(true);
    expect(bridges.hasOnSnapshot, "__firestoreOnSnapshot").toBe(true);
    expect(bridges.hasOnDocSnapshot, "__firestoreOnDocSnapshot").toBe(true);
    expect(bridges.hasUnsubscribe, "__firestoreUnsubscribe").toBe(true);
    expect(bridges.hasListeners, "__firestoreListeners map").toBe(true);
  });

  test("globalThis Auth bridges are defined after page load", async ({ page }) => {
    await page.goto("/");
    await page.waitForTimeout(15_000);

    const bridges = await page.evaluate(() => ({
      hasGoogleSignIn: typeof (globalThis as any).__googleSignIn === "function",
      hasFirebaseSignOut: typeof (globalThis as any).__firebaseSignOut === "function",
      hasGetCurrentUser: typeof (globalThis as any).__getCurrentFirebaseUser === "function",
      hasAuthReady: typeof (globalThis as any).__authReadyPromise !== "undefined",
    }));

    expect(bridges.hasGoogleSignIn, "__googleSignIn").toBe(true);
    expect(bridges.hasFirebaseSignOut, "__firebaseSignOut").toBe(true);
    expect(bridges.hasGetCurrentUser, "__getCurrentFirebaseUser").toBe(true);
    expect(bridges.hasAuthReady, "__authReadyPromise").toBe(true);
  });

  test("__firestoreGetDocs returns structured error without auth (not crash)", async ({ page }) => {
    await page.goto("/");
    await page.waitForTimeout(15_000);

    const result = await page.evaluate(async () => {
      try {
        const raw = await (globalThis as any).__firestoreGetDocs("users/fake-uid/checklists");
        return typeof raw === "string" ? JSON.parse(raw) : raw;
      } catch (e: any) {
        return { crashed: true, error: e?.message ?? String(e) };
      }
    });

    console.log("__firestoreGetDocs (no auth) result:", JSON.stringify(result));
    expect(result.crashed, "bridge must not crash").not.toBe(true);
    expect(typeof result.ok).toBe("boolean");
  });

  test("__firestoreSetDoc returns structured error without auth (not crash)", async ({ page }) => {
    await page.goto("/");
    await page.waitForTimeout(15_000);

    const result = await page.evaluate(async () => {
      try {
        const data = JSON.stringify({ cloudId: "test", name: "Test" });
        const raw = await (globalThis as any).__firestoreSetDoc("users/fake-uid/checklists", "test-id", data);
        return typeof raw === "string" ? JSON.parse(raw) : raw;
      } catch (e: any) {
        return { crashed: true, error: e?.message ?? String(e) };
      }
    });

    console.log("__firestoreSetDoc (no auth) result:", JSON.stringify(result));
    expect(result.crashed, "bridge must not crash").not.toBe(true);
    expect(typeof result.ok).toBe("boolean");
  });

  test("__getCurrentFirebaseUser returns null when not signed in", async ({ page }) => {
    await page.goto("/");
    await page.waitForTimeout(15_000);

    const user = await page.evaluate(async () => {
      return await (globalThis as any).__getCurrentFirebaseUser();
    });

    expect(user).toBeNull();
  });

  test("__authReadyPromise resolves to null when not signed in", async ({ page }) => {
    await page.goto("/");
    await page.waitForTimeout(15_000);

    const result = await page.evaluate(async () => {
      return await (globalThis as any).__authReadyPromise;
    });

    expect(result).toBeNull();
  });

  test("Firestore listener can be created and unsubscribed without crash", async ({ page }) => {
    await page.goto("/");
    await page.waitForTimeout(15_000);

    const result = await page.evaluate(() => {
      try {
        const listenerId = (globalThis as any).__firestoreOnSnapshot(
          "users/fake-uid/checklists",
          "test-callback-id"
        );
        (globalThis as any).__firestoreUnsubscribe(listenerId);
        return { ok: true, listenerId };
      } catch (e: any) {
        return { ok: false, error: e?.message ?? String(e) };
      }
    });

    console.log("Listener create/unsubscribe result:", JSON.stringify(result));
    expect(result.ok, "listener lifecycle must not crash").toBe(true);
  });
});

test.describe("Gisti web — COOP/COEP headers for Google Sign-In", () => {
  test("COOP header is absent (SAH Pool VFS does not need SharedArrayBuffer)", async ({ request }) => {
    const res = await request.get("/");
    const coop = res.headers()["cross-origin-opener-policy"];
    expect(
      coop,
      "COOP must be absent — SAH Pool VFS doesn't need it, and it blocks Firebase Auth popup"
    ).toBeUndefined();
  });
});
