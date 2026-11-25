import path from 'node:path';
import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers';

test.describe('Dataset upload and analysis', () => {
  test('uploads a CSV, runs analysis, and the resulting report appears in Reports', async ({ page }) => {
    // Real Spark + Great Expectations job, not mocked - give it real headroom
    // beyond Playwright's 30s default instead of tuning the poll timeout alone.
    test.setTimeout(90_000);
    const fileName = `e2e-${Date.now()}.csv`;

    await loginAsAdmin(page);
    await page.goto('/datasets');

    await page.locator('input[type="file"]').setInputFiles({
      name: fileName,
      mimeType: 'text/csv',
      buffer: await readFixture(),
    });
    await expect(page.locator('.selected-file .file-name')).toHaveText(fileName);

    await page.locator('.selected-file button').nth(1).click();

    const row = page.locator('table tbody tr', { hasText: fileName });
    await expect(row).toBeVisible({ timeout: 10_000 });

    await row.locator('button').first().click();

    await expect(row.locator('.badge')).toHaveClass(/status-completed|status-failed/, { timeout: 30_000 });
    await expect(row.locator('.badge')).toHaveClass(/status-completed/);

    await page.goto('/reports');
    const reportRow = page.locator('table tbody tr', { hasText: fileName });
    await expect(reportRow).toBeVisible({ timeout: 10_000 });

    await reportRow.click();
    await expect(page).toHaveURL(/\/reports\/\d+$/);
  });
});

async function readFixture(): Promise<Buffer> {
  const fs = await import('node:fs/promises');
  return fs.readFile(path.join(__dirname, '..', 'fixtures', 'sample-dataset.csv'));
}
