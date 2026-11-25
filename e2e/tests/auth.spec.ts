import { test, expect } from '@playwright/test';
import { ADMIN_EMAIL, ADMIN_PASSWORD, login, loginAsAdmin, uniqueEmail } from './helpers';

test.describe('Authentication', () => {
  test('rejects an unknown login with an inline error and stays on the login page', async ({ page }) => {
    await page.goto('/login');
    await page.locator('input[type="email"]').fill('nobody@dqmonitor.com');
    await page.locator('input[type="password"]').fill('wrong-password');
    await page.locator('.submit-btn').click();

    // Generous timeout: this is often the first navigation in the run, and the
    // Angular dev server compiles the lazy-loaded /login chunk on demand.
    await expect(page.locator('.error-message')).toBeVisible({ timeout: 15_000 });
    await expect(page).toHaveURL(/\/login$/);
  });

  test('logs in with valid admin credentials and reaches the dashboard', async ({ page }) => {
    await loginAsAdmin(page);
    await expect(page.locator('.sidebar .brand-name')).toBeVisible();
  });

  test('redirects an unauthenticated visitor away from a protected route', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('registers a new account and immediately logs in with it', async ({ page }) => {
    const email = uniqueEmail('e2e-register');

    await page.goto('/register');
    await page.locator('input[autocomplete="name"]').fill('E2E Test User');
    await page.locator('input[type="email"]').fill(email);
    await page.locator('input[autocomplete="new-password"]').first().fill('password123');
    await page.locator('input[autocomplete="new-password"]').nth(1).fill('password123');
    await page.locator('.submit-btn').click();

    await expect(page).toHaveURL(/\/login$/);

    await login(page, email, 'password123');
  });

  test('logout clears the session and blocks returning to a protected route via back navigation', async ({ page }) => {
    await loginAsAdmin(page);

    await page.locator('.logout-link').click();
    await expect(page).toHaveURL(/\/login$/);

    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('a newly registered user cannot see or reach the admin-only Administration page', async ({ page }) => {
    const email = uniqueEmail('e2e-nonadmin');
    await page.goto('/register');
    await page.locator('input[autocomplete="name"]').fill('Non Admin');
    await page.locator('input[type="email"]').fill(email);
    await page.locator('input[autocomplete="new-password"]').first().fill('password123');
    await page.locator('input[autocomplete="new-password"]').nth(1).fill('password123');
    await page.locator('.submit-btn').click();

    await login(page, email, 'password123');

    await expect(page.locator('a[href="/administration"]')).toHaveCount(0);

    await page.goto('/administration');
    await expect(page).toHaveURL(/\/dashboard$/);
  });
});

test.describe('Admin access', () => {
  test('admin sees the Administration nav link and can open it', async ({ page }) => {
    await loginAsAdmin(page);

    const adminLink = page.locator('a[href="/administration"]');
    await expect(adminLink).toBeVisible();
    await adminLink.click();
    await expect(page).toHaveURL(/\/administration$/);
  });
});
