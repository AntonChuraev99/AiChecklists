import { defineConfig, devices } from '@playwright/test';

/**
 * Visual regression tests for Gisti AI Checklists wasmJs build.
 *
 * Strategy:
 * - Tests boot a single dev server (`./gradlew :composeApp:wasmJsBrowserDevelopmentRun`)
 *   already running on http://localhost:9090. Do NOT auto-start in webServer config —
 *   the wasmJs build takes 1-3 minutes cold and would block every test run.
 * - 4 viewports correspond to Material 3 WindowSizeClass breakpoints + KMP plan:
 *   - phone: 360x800 (Compact)
 *   - tablet-portrait: 800x1280 (Medium upper boundary)
 *   - tablet-landscape: 1280x800 (Medium → Expanded)
 *   - desktop: 1440x900 (Expanded — primary desktop target)
 * - Compose canvas has no DOM accessibility tree — locators by role/text don't work.
 *   Tests rely on screenshots + coordinate clicks. No DOM assertions on app content.
 *
 * Update baselines: `npm run test:update` after intentional UI change.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: {
    // Allow 1% pixel diff for font hinting, anti-aliasing drift between machines.
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.01,
      animations: 'disabled',
    },
  },
  fullyParallel: false, // wasmJs single-instance — parallel viewport-projects would race
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: 'http://localhost:9090',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    // Compose canvas needs longer than typical DOM apps
    actionTimeout: 10_000,
    navigationTimeout: 30_000,
  },
  projects: [
    {
      name: 'phone',
      use: { ...devices['Desktop Chrome'], viewport: { width: 360, height: 800 } },
    },
    {
      name: 'tablet-portrait',
      use: { ...devices['Desktop Chrome'], viewport: { width: 800, height: 1280 } },
    },
    {
      name: 'tablet-landscape',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } },
    },
    {
      name: 'desktop',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
  ],
});
