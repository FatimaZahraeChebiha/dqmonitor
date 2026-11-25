import { defineConfig, devices } from '@playwright/test';

/**
 * Runs against the frontend dev server (assumed already running on 4200,
 * proxying API calls straight to the backend on 8080 - see
 * frontend/src/environments/environment.ts). `reuseExistingServer` lets this
 * run unattended in CI (which starts its own servers) and against a developer's
 * already-running `ng serve` locally without double-starting anything.
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
