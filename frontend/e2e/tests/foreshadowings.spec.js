import { test, expect } from '@playwright/test'
import { setupAllMocks } from '../utils/mock-setup.js'

test.describe('Foreshadowing Management', () => {
  const uniqueId = () => Date.now().toString(36)

  test.beforeEach(async ({ page }) => {
    await setupAllMocks(page)
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    // Wait for navigation to complete before extracting book ID
    await expect(page.locator('.detail-card h1')).toBeVisible({ timeout: 5000 })
    const bookId = page.url().split('/').pop()
    await page.goto(`/books/${bookId}/foreshadowings`)
    await page.waitForTimeout(500)
  })

  test('page loads with header and toolbar', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('伏笔管理', { timeout: 5000 })

    // Toolbar should have add and scan buttons
    await expect(page.locator('button:has-text("添加伏笔")')).toBeVisible()
    await expect(page.locator('button:has-text("AI 扫描章节")')).toBeVisible()

    // Filter selects should exist
    const filters = page.locator('.filter-select')
    await expect(filters).toHaveCount(2)
  })

  test('create foreshadowing via modal', async ({ page }) => {
    const desc = `E2E 测试伏笔 ${uniqueId()}`

    await page.locator('button:has-text("添加伏笔")').click()

    // Modal form should appear
    await expect(page.locator('.modal-content')).toBeVisible({ timeout: 3000 })

    // Fill form
    await page.locator('.modal-content textarea').first().fill(desc)

    // Select type
    await page.locator('.modal-content select').first().selectOption('CHARACTER')

    // Submit
    await page.locator('.modal-content button:has-text("保存")').click()

    // Modal should close
    await expect(page.locator('.modal-content')).not.toBeVisible({ timeout: 3000 })

    // Success message
    await expect(page.locator('.success-msg')).toBeVisible({ timeout: 5000 })

    // New item should appear (may need time for list refresh)
    await page.waitForTimeout(1000)
    const items = page.locator('.card-desc').filter({ hasText: desc })
    const count = await items.count()
    expect(count).toBeGreaterThanOrEqual(1)
  })

  test('edit foreshadowing', async ({ page }) => {
    // Need existing item — if none, skip
    const card = page.locator('.foreshadowing-card').first()
    if (!(await card.isVisible({ timeout: 3000 }).catch(() => false))) {
      return
    }

    const newDesc = `编辑 ${uniqueId()}`
    await card.locator('button:has-text("编辑")').click()

    await expect(page.locator('.modal-content')).toBeVisible({ timeout: 3000 })

    // Change description
    const textarea = page.locator('.modal-content textarea').first()
    await textarea.fill(newDesc)

    // Save
    await page.locator('.modal-content button:has-text("保存")').click()

    await expect(page.locator('.modal-content')).not.toBeVisible({ timeout: 3000 })

    // Updated text should appear
    await expect(page.locator('.card-desc').filter({ hasText: newDesc }).first()).toBeVisible({ timeout: 5000 })
  })

  test('delete foreshadowing', async ({ page }) => {
    const before = await page.locator('.foreshadowing-card').count()
    if (before === 0) return

    const card = page.locator('.foreshadowing-card').first()
    await card.locator('button:has-text("删除")').click()

    // ModalConfirm should appear
    await expect(page.locator('.modal-overlay')).toBeVisible({ timeout: 3000 })

    // Confirm
    await page.locator('.modal-overlay button:has-text("确定")').click()

    // Count should decrease
    await page.waitForTimeout(1000)
    const after = await page.locator('.foreshadowing-card').count()
    expect(after).toBeLessThan(before)
  })

  test('AI scan foreshadowings triggers mock', async ({ page }) => {
    // Fill scan input with a structure ID
    await page.locator('.scan-input').fill('1')

    const scanBtn = page.locator('button:has-text("AI 扫描章节")')
    await expect(scanBtn).toBeEnabled({ timeout: 3000 })
    await scanBtn.click()

    // Success message should appear from mocked response
    await expect(page.locator('.success-msg')).toBeVisible({ timeout: 10000 })
  })

  test('type filter filters items', async ({ page }) => {
    const filterSelects = page.locator('.filter-select')
    // First filter is type
    await filterSelects.first().selectOption('EVENT')
    await page.waitForTimeout(300)

    // All visible cards should have EVENT type
    const cards = page.locator('.foreshadowing-card')
    const count = await cards.count()
    if (count > 0) {
      for (let i = 0; i < count; i++) {
        const typeBadge = cards.nth(i).locator('.card-type')
        if (await typeBadge.isVisible()) {
          const text = await typeBadge.textContent()
          expect(text).toBe('事件')
        }
      }
    }
  })

  test('status filter filters items', async ({ page }) => {
    const filterSelects = page.locator('.filter-select')
    // Second filter is status
    await filterSelects.nth(1).selectOption('ACTIVE')
    await page.waitForTimeout(300)

    const cards = page.locator('.foreshadowing-card')
    const count = await cards.count()
    if (count > 0) {
      for (let i = 0; i < count; i++) {
        const statusBadge = cards.nth(i).locator('.card-status')
        if (await statusBadge.isVisible()) {
          const text = await statusBadge.textContent()
          expect(text).toBe('活跃')
        }
      }
    }
  })
})
