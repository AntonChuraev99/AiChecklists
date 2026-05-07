import { test } from "@playwright/test";

test("static server: full console log + DOM dump after 60s", async ({ page }) => {
  const messages: string[] = [];
  page.on("console", (msg) => messages.push(`[${msg.type()}] ${msg.text()}`));
  page.on("pageerror", (err) => messages.push(`[pageerror] ${err.message}`));
  page.on("requestfailed", (req) =>
    messages.push(`[reqfail] ${req.url().slice(0, 120)} — ${req.failure()?.errorText}`),
  );

  await page.goto("http://localhost:9091/", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(45_000);

  const dom = await page.evaluate(() => ({
    title: document.title,
    canvases: document.querySelectorAll("canvas").length,
    bodyChildren: document.body.children.length,
    bodyTags: Array.from(document.body.children).map((el) => el.tagName).join(","),
    loadingExists: !!document.getElementById("loading"),
    bodyTextSample: (document.body.innerText || "").slice(0, 500),
  }));

  console.log("=== DOM after 45s on 9091 ===");
  console.log(JSON.stringify(dom, null, 2));
  console.log("=== Messages (last 60) ===");
  for (const m of messages.slice(-60)) console.log(m);
});
