import { test, expect } from '@playwright/test';

test.describe('Base64 crash reproduction', () => {
  test('local build - catch Base64 padding error with full stack', async ({ page }) => {
    const errors: { name: string; message: string; stack: string }[] = [];

    page.on('pageerror', (error) => {
      errors.push({ name: error.name, message: error.message, stack: error.stack || '' });
      console.log(`\n!!! PAGE ERROR: ${error.name}: ${error.message}`);
      console.log(`STACK:\n${error.stack}`);
    });

    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        console.log(`CONSOLE.ERROR: ${msg.text()}`);
      }
    });

    await page.goto('http://localhost:8888', { waitUntil: 'networkidle', timeout: 90_000 });
    await page.waitForTimeout(15_000);

    const base64Errors = errors.filter(e =>
      e.message.includes('padding') || e.message.includes('Base64') || e.name.includes('IllegalArgument')
    );

    console.log(`\n=== Total errors: ${errors.length}, Base64 errors: ${base64Errors.length} ===`);
    errors.forEach((e, i) => console.log(`Error ${i}: ${e.name}: ${e.message.substring(0, 200)}`));

    if (base64Errors.length > 0) {
      console.log('\n=== FULL BASE64 ERROR STACKS ===');
      base64Errors.forEach(e => console.log(e.stack));
    }
  });
});
