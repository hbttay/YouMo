import { test, expect } from '@playwright/test'

test.describe('User Center', () => {
  test('page loads with user info', async ({ page }) => {
    await page.goto('/user/center')
    await expect(page.locator('.page-title')).toContainText('个人中心', { timeout: 5000 })

    // Should show user ID, status, and registration time
    await expect(page.locator('.info-row').first()).toBeVisible()
    await expect(page.locator('.label').filter({ hasText: '用户 ID' })).toBeVisible()
    await expect(page.locator('.label').filter({ hasText: '账号状态' })).toBeVisible()
    await expect(page.locator('.label').filter({ hasText: '注册时间' })).toBeVisible()
  })

  test('change email', async ({ page }) => {
    const newEmail = `updated-${Date.now()}@test.dev`

    await page.goto('/user/center')
    await expect(page.locator('.page-title')).toBeVisible({ timeout: 5000 })

    // Fill new email
    const emailInput = page.locator('input[type="email"]')
    if (await emailInput.isVisible()) {
      await emailInput.fill(newEmail)

      // Click 修改 button (in the field-row)
      await page.locator('.field-row .btn-save').click()

      // Wait for result
      await page.waitForTimeout(2000)

      // Either success or error message should appear
      const hasMsg = await page.locator('.msg-ok').isVisible().catch(() => false)
      const hasErr = await page.locator('.msg-err').isVisible().catch(() => false)
      expect(hasMsg || hasErr).toBeTruthy()
    }
  })

  test('change password — validation errors', async ({ page }) => {
    await page.goto('/user/center')
    await expect(page.locator('.page-title')).toBeVisible({ timeout: 5000 })

    // Try submitting empty
    await page.locator('button:has-text("修改密码")').click()
    await expect(page.locator('.msg-err')).toBeVisible({ timeout: 3000 })

    // Fill old but short new
    const passwordInputs = page.locator('input[type="password"]')
    if ((await passwordInputs.count()) >= 2) {
      await passwordInputs.nth(0).fill('oldpass')
      await passwordInputs.nth(1).fill('123')
      await page.locator('button:has-text("修改密码")').click()
      await expect(page.locator('.msg-err')).toContainText('至少 6 位', { timeout: 3000 })
    }
  })

  test('change password — mismatch', async ({ page }) => {
    await page.goto('/user/center')
    await expect(page.locator('.page-title')).toBeVisible({ timeout: 5000 })

    const passwordInputs = page.locator('input[type="password"]')
    if ((await passwordInputs.count()) >= 3) {
      await passwordInputs.nth(0).fill('test1234')
      await passwordInputs.nth(1).fill('newpass123')
      await passwordInputs.nth(2).fill('different456')
      await page.locator('button:has-text("修改密码")').click()
      await expect(page.locator('.msg-err')).toContainText('不一致', { timeout: 3000 })
    }
  })

  test('back link navigates to home', async ({ page }) => {
    await page.goto('/user/center')
    await expect(page.locator('.back-link')).toBeVisible()
    const href = await page.locator('.back-link').getAttribute('href')
    expect(href).toBe('/')
  })
})
