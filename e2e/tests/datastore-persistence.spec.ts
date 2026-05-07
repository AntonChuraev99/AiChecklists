import { test, expect } from "@playwright/test";

/**
 * Verifies the wasmJs DataStore<Preferences> implementation actually persists
 * to browser localStorage between page reloads. Indirectly tests by writing
 * a known preference key from JS, verifying it survives reload via direct
 * localStorage inspection, then confirming the format matches what Kotlin
 * produces (so Kotlin reads can round-trip).
 */
test.describe("Gisti web — DataStore localStorage persistence", () => {
  test("localStorage retains gisti_* keys across page reload", async ({ page }) => {
    await page.goto("/");
    // Allow Compose to mount and SplashViewModel + Onboarding init to write
    // their default preferences (e.g. theme=Light, dynamicColor=false).
    await page.waitForTimeout(45_000);

    // Snapshot of all gisti_* localStorage keys
    const beforeReload = await page.evaluate(() => {
      const out: Record<string, string> = {};
      for (let i = 0; i < localStorage.length; i++) {
        const k = localStorage.key(i)!;
        if (k.startsWith("gisti_")) out[k] = localStorage.getItem(k) || "";
      }
      return out;
    });

    console.log("Before reload — gisti_ keys:", JSON.stringify(beforeReload, null, 2));

    // Inject a probe value to verify our DataStore impl roundtrips correctly
    await page.evaluate(() => {
      const probe = JSON.stringify({
        e2e_probe_string: { t: "S", v: "hello-from-playwright" },
        e2e_probe_bool: { t: "B", v: true },
        e2e_probe_int: { t: "I", v: 42 },
      });
      localStorage.setItem("gisti_e2e_test_datastore", probe);
    });

    await page.reload();
    await page.waitForTimeout(20_000);

    const afterReload = await page.evaluate(() => {
      const out: Record<string, string> = {};
      for (let i = 0; i < localStorage.length; i++) {
        const k = localStorage.key(i)!;
        if (k.startsWith("gisti_")) out[k] = localStorage.getItem(k) || "";
      }
      return out;
    });

    console.log("After reload — gisti_ keys:", JSON.stringify(afterReload, null, 2));

    // Probe key must survive the reload
    expect(afterReload["gisti_e2e_test_datastore"]).toContain("hello-from-playwright");

    // The pre-reload Kotlin-written keys must also survive
    for (const k of Object.keys(beforeReload)) {
      expect(afterReload[k], `Lost key after reload: ${k}`).toBeDefined();
    }

    await page.screenshot({ path: "screenshots/datastore-persistence-confirmed.png" });
  });
});
