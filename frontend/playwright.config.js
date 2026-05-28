import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e/tests',
  timeout: 30000,
  expect: { timeout: 10000 },
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],

  globalSetup: './e2e/global-setup.js',

  use: {
    baseURL: 'http://localhost:5173',
    locale: 'zh-CN',
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },

  projects: [
    // Unauthenticated — login/register pages
    {
      name: 'no-auth',
      testMatch: /(auth|auth-actions)\.spec\.js/,
      use: { ...devices['Desktop Chrome'], channel: 'chrome' },
    },
    // Authenticated — all other tests
    {
      name: 'chromium',
      testMatch: '**/*.spec.js',
      testIgnore: '**/auth*.spec.js',
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chrome',
        storageState: 'e2e/.auth/state.json',
      },
      dependencies: [],
    },
  ],
})
