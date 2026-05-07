import { test, expect } from "@playwright/test";

const STATIC_URL = "http://localhost:9091";

test.describe("Phase 7b: Static server (bypass webpack-dev-server)", () => {
  // SKIPPED: this diagnostic test confirmed the wasm truncation reproduces on
  // a plain Python http.server too — meaning the issue is Playwright's wasm
  // streaming, NOT webpack-dev-server. See app-render.spec.ts header comment.
  test.skip(true, "Diagnostic test — issue confirmed in Playwright not server");

  test("Compose app loads and Onboarding renders via static Python server", async ({ page }) => {
    const errors: string[] = [];
    page.on("pageerror", (err) => errors.push(`pageerror: ${err.message}`));
    page.on("console", (msg) => {
      if (msg.type() === "error") errors.push(`console.error: ${msg.text()}`);
    });

    await page.goto(`${STATIC_URL}/`, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(60_000);
    await page.screenshot({ path: "screenshots/phase7b-static.png" });

    const seen = await page.getByText(/What do you want to organize|Get Started|Skip|Travel/i).count();
    console.log("Found onboarding text:", seen);
    console.log("Errors:", errors.slice(0, 10).join("\n"));

    expect(seen, `Onboarding visible (errors: ${errors.slice(0, 3).join(" | ")})`).toBeGreaterThan(
      0,
    );
  });
});
