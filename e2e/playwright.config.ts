import { defineConfig, devices } from "@playwright/test";

const BASE_URL = process.env.GISTI_WEB_URL ?? "http://localhost:9090";

export default defineConfig({
  testDir: "./tests",
  outputDir: "./test-results",
  snapshotDir: "./snapshots",
  timeout: 120_000,
  expect: { timeout: 30_000 },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [
    ["list"],
    ["html", { outputFolder: "./report", open: "never" }],
  ],
  use: {
    baseURL: BASE_URL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    actionTimeout: 15_000,
    navigationTimeout: 60_000,
    // Note: do NOT disable browser cache via --disable-cache — that interferes
    // with WebAssembly.instantiateStreaming on large wasm files (>20MB) served
    // by webpack-dev-server, causing mid-stream truncation errors.
    bypassCSP: true,
  },
  projects: [
    {
      // Compose Multiplatform 1.9.3 wasm requires Wasm GC + Exception Handling
      // (Chrome 119+ with default flags). Bundled Playwright Chromium can lag
      // behind on these proposals — use system Chrome instead via channel.
      name: "chrome",
      use: {
        ...devices["Desktop Chrome"],
        channel: "chrome",
        viewport: { width: 1280, height: 800 },
      },
    },
  ],
});
