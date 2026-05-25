import { test, expect } from '@playwright/test';

/**
 * Adaptive layout visual regression — Gisti AI Checklists.
 *
 * Reproduces the 4 form factors users will see (phone/tablet-portrait/tablet-landscape/
 * desktop) and asserts the rendered canvas matches committed baselines. A failed test
 * = visual regression: the UI changed in a way that's invisible to unit tests.
 *
 * Test discipline (per project CLAUDE.md):
 *   RED → fix → GREEN. If a UI bug is reported, write a failing test first, then fix
 *   until baseline screenshot matches. Don't approve a snapshot for a known bug.
 *
 * Boot order:
 *   1. Start dev server externally:  ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
 *      (wait until http://localhost:9090 responds — wasm compile takes 1-3 min cold)
 *   2. Run tests:                    npm run test  (from playwright/)
 *   3. Update baselines:             npm run test:update
 */

/**
 * Wait for Compose canvas to finish first paint. wasmJs boot has:
 *  - HTML loaded (DOMContentLoaded)
 *  - wasm fetched + instantiated (~3-10s depending on cache)
 *  - first Compose composition + Skiko canvas draw (~500ms-2s)
 *
 * We don't have window.__composeReady yet (Stage 8b). For now: wait for the
 * canvas element to be present + 1500ms buffer for first paint.
 */
async function waitForComposeReady(page: import('@playwright/test').Page) {
  await page.waitForLoadState('networkidle', { timeout: 30_000 });
  await page.waitForSelector('canvas', { timeout: 30_000 });
  // Allow Compose first paint + theme/locale env to settle
  await page.waitForTimeout(2_000);
}

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await waitForComposeReady(page);
});

test('main screen — viewport-specific layout', async ({ page }, testInfo) => {
  await expect(page).toHaveScreenshot(`main-${testInfo.project.name}.png`, {
    fullPage: false,
  });
});

/**
 * No-crash smoke check — page loaded without JS exceptions reaching console.error.
 * Catches the "NavDisplay backstack cannot be empty" class of bug as a failing test.
 */
test('no console errors during boot', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text());
  });
  page.on('pageerror', (err) => errors.push(err.message));

  // Re-navigate to capture errors (beforeEach already ran, but listeners attach after goto)
  await page.goto('/');
  await waitForComposeReady(page);

  // Filter known harmless: webpack "Critical dependency" warning leaks through as console.error
  // on some Chrome versions. It's not an app-level bug — skip it.
  const significant = errors.filter(
    (e) => !e.includes('Critical dependency') && !e.includes('webpack-internal'),
  );

  expect(significant, `console errors during boot:\n${significant.join('\n')}`).toEqual([]);
});
