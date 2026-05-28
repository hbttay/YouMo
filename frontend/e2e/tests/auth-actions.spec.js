import { test, expect } from '@playwright/test'

test.describe('Auth Actions', () => {
  // These tests use no auth state (they're in the no-auth project)
  // They test actual login/register behavior

  test('login with valid credentials', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('h1')).toContainText('登录', { timeout: 5000 })

    await page.locator('input[type="text"]').fill('e2e-test@youmo.dev')
    await page.locator('input[type="password"]').fill('test1234')
    await page.locator('button:has-text("登录")').click()

    // Should redirect to home
    await expect(page.locator('.hero')).toBeVisible({ timeout: 8000 })
  })

  test('login fails with wrong password', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('h1')).toContainText('登录')

    await page.locator('input[type="text"]').fill('e2e-test@youmo.dev')
    await page.locator('input[type="password"]').fill('wrongpassword')
    await page.locator('button:has-text("登录")').click()

    // Error message should appear
    await expect(page.locator('.msg-bar.error')).toBeVisible({ timeout: 5000 })
  })

  test('login fails with empty fields', async ({ page }) => {
    await page.goto('/login')
    await page.locator('button:has-text("登录")').click()

    // Should show validation error
    await expect(page.locator('.msg-bar.error')).toBeVisible({ timeout: 3000 })
  })

  test('register page renders and validates', async ({ page }) => {
    await page.goto('/register')
    // Clear any leaked token from other tests
    await page.evaluate(() => localStorage.clear())
    await page.goto('/register')
    await expect(page.locator('h1')).toContainText('注册', { timeout: 5000 })

    // Has link to login (might have multiple)
    await expect(page.locator('a[href="/login"]').first()).toBeVisible()
  })

  test('register with password mismatch', async ({ page }) => {
    await page.goto('/register')

    const uniqueEmail = `test-${Date.now()}@e2e.dev`
    const inputs = page.locator('input[type="text"], input[type="email"]')
    const inputCount = await inputs.count()

    if (inputCount >= 2) {
      await inputs.nth(0).fill(uniqueEmail)
      await inputs.nth(1).fill('testuser')
    }

    // Fill passwords differently
    const passwordInputs = page.locator('input[type="password"]')
    const pwdCount = await passwordInputs.count()
    if (pwdCount >= 2) {
      await passwordInputs.nth(0).fill('test1234')
      await passwordInputs.nth(1).fill('different')
      await page.locator('button:has-text("注册")').click()
      await expect(page.locator('.msg-bar.error')).toBeVisible({ timeout: 3000 })
    }
  })
})
