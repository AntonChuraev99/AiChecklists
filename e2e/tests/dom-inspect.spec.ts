import { test } from "@playwright/test";

test("inspect DOM + console errors", async ({ page }) => {
  const messages: string[] = [];
  page.on("console", (msg) => messages.push(`[${msg.type()}] ${msg.text()}`));
  page.on("pageerror", (err) => messages.push(`[pageerror] ${err.message}\n${err.stack}`));
  page.on("requestfailed", (req) =>
    messages.push(`[requestfailed] ${req.url()} — ${req.failure()?.errorText}`),
  );

  await page.goto("/");
  // Long wait to give WASM time
  await page.waitForTimeout(60_000);

  const dom = await page.evaluate(() => {
    return {
      canvases: document.querySelectorAll("canvas").length,
      bodyTagsCount: document.body.children.length,
      title: document.title,
    };
  });

  console.log("=== DOM after 60s ===");
  console.log("Canvas count:", dom.canvases);
  console.log("Body children count:", dom.bodyTagsCount);
  console.log("=== Console / errors / failed requests ===");
  for (const m of messages.slice(-50)) console.log(m);

  await page.screenshot({ path: "screenshots/00-dom-inspect.png" });
});
