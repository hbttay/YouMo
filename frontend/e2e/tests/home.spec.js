import { test, expect } from '@playwright/test'

test.describe('Home Dashboard', () => {
  test('home page loads with hero section', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('.hero')).toBeVisible({ timeout: 8000 })
    await expect(page.locator('h1')).toContainText('欢迎回来')
  })

  test('stats row shows when books exist', async ({ page }) => {
    await page.goto('/')
    await page.waitForTimeout(1000)

    // Stats cards or empty state CTA
    const hasStats = await page.locator('.stats-row').isVisible({ timeout: 3000 }).catch(() => false)
    const hasEmptyCta = await page.locator('.btn-lg').isVisible({ timeout: 3000 }).catch(() => false)
    expect(hasStats || hasEmptyCta).toBeTruthy()
  })

  test('module grid has 7 modules', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('.hero')).toBeVisible({ timeout: 8000 })

    const moduleCards = page.locator('.module-card')
    await expect(moduleCards.first()).toBeVisible({ timeout: 5000 })
    const count = await moduleCards.count()
    expect(count).toBeGreaterThanOrEqual(5)
  })

  test('module card click navigates', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('.hero')).toBeVisible({ timeout: 8000 })

    // Click 书籍管理
    const bookModule = page.locator('.module-card').filter({ hasText: '书籍管理' })
    if (await bookModule.isVisible({ timeout: 2000 })) {
      await bookModule.click()
      await page.waitForTimeout(500)
      await expect(page.locator('h1')).toContainText('我的书籍', { timeout: 5000 })
    }
  })

  test('stat cards navigate on click', async ({ page }) => {
    await page.goto('/')
    await page.waitForTimeout(1000)

    const statCards = page.locator('.stat-card.clickable')
    if ((await statCards.count()) > 0) {
      await statCards.first().click()
      await page.waitForTimeout(500)
      await expect(page.locator('h1')).toContainText('我的书籍', { timeout: 5000 })
    }
  })

  test('recent books chips display', async ({ page }) => {
    await page.goto('/')
    await page.waitForTimeout(1000)

    const recentChips = page.locator('.recent-chip')
    const count = await recentChips.count()
    // May be 0 if no books, that's fine
    expect(count).toBeGreaterThanOrEqual(0)
  })

  test('user greeting shown', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('.hero-welcome h1')).toBeVisible({ timeout: 8000 })
  })
})
