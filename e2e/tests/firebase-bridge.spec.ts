import { test, expect } from "@playwright/test";

/**
 * Phase 3 verification:
 * - init.js loads as <script type="module"> from index.html
 * - Firebase JS SDK is imported (initializeApp, RC, Functions)
 * - globalThis.__rcGetString / __rcGetBoolean / __rcGetNumber bridges defined
 * - globalThis.__rcFetchPromise resolves (or rejects gracefully if API key missing)
 * - globalThis.__functionsCall bridge defined (for httpsCallable Cloud Functions)
 */
test.describe("Gisti web — Phase 3: Firebase JS SDK bridges", () => {
  test("init.js is served at root and contains Firebase imports", async ({ request }) => {
    const res = await request.get("/init.js");
    expect(res.ok()).toBe(true);
    const body = await res.text();
    expect(body).toContain("initializeApp");
    expect(body).toContain("getRemoteConfig");
    expect(body).toContain("__rcGetString");
    expect(body).toContain("__rcFetchPromise");
    expect(body).toContain("composeApp.js");
  });

  test("globalThis.__rc* bridges are defined after page load", async ({ page }) => {
    await page.goto("/");
    // init.js is a module — give the import chain time to resolve
    await page.waitForTimeout(15_000);

    const bridges = await page.evaluate(() => ({
      hasRcGetString: typeof (globalThis as any).__rcGetString === "function",
      hasRcGetBoolean: typeof (globalThis as any).__rcGetBoolean === "function",
      hasRcGetNumber: typeof (globalThis as any).__rcGetNumber === "function",
      hasRcFetchPromise: typeof (globalThis as any).__rcFetchPromise !== "undefined",
      hasFunctionsCall: typeof (globalThis as any).__functionsCall === "function",
    }));

    expect(bridges.hasRcGetString, "__rcGetString must be defined").toBe(true);
    expect(bridges.hasRcGetBoolean, "__rcGetBoolean must be defined").toBe(true);
    expect(bridges.hasRcGetNumber, "__rcGetNumber must be defined").toBe(true);
    expect(bridges.hasRcFetchPromise, "__rcFetchPromise must be defined").toBe(true);
    expect(bridges.hasFunctionsCall, "__functionsCall must be defined").toBe(true);
  });

  test("__rcGetString returns string for known key (defaultConfig fallback works)", async ({
    page,
  }) => {
    await page.goto("/");
    await page.waitForTimeout(15_000);

    const value = await page.evaluate(() => {
      try {
        return (globalThis as any).__rcGetString("templates_json");
      } catch (e) {
        return `error: ${e}`;
      }
    });

    // Even without real API key, defaultConfig should return a string (never throw)
    expect(typeof value).toBe("string");
    console.log("templates_json default config value:", value.slice(0, 200));
  });

  // NOTE: We don't assert canvas-level render in Playwright because the bundled
  // Playwright Chromium and even system Chrome via Playwright produce a wasm
  // streaming error ("section extends past end") on the 26MB Compose Multiplatform
  // wasm bundle. Same wasm renders correctly in user's regular Chrome (manually
  // verified). Track at: https://github.com/JetBrains/compose-multiplatform/issues
  // For e2e-render coverage, run Compose UI Tests in commonTest, not Playwright.
  test.skip("Compose canvas mounts in Playwright (skipped — known wasm streaming issue)", async () => {});
});
