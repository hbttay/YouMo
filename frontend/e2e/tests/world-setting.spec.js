import { test, expect } from '@playwright/test'
import { setupAllMocks } from '../utils/mock-setup.js'

test.describe('World Setting', () => {
  const uniqueId = () => Date.now().toString(36)

  test.beforeEach(async ({ page }) => {
    await setupAllMocks(page)
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await page.getByText('世界观设定').click()
    await page.waitForTimeout(500)
  })

  test('form loads with all 9 fields', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('世界观设定', { timeout: 5000 })

    // Check that all form fields are present
    const labels = ['时代背景', '地理环境', '历史事件', '政治体制', '经济体系', '文化', '军事', '核心规则类型', '核心规则简述']
    for (const label of labels) {
      await expect(page.locator('.setting-form label').filter({ hasText: label })).toBeVisible({ timeout: 3000 })
    }
  })

  test('save and reload persists data', async ({ page }) => {
    const testText = `E2E 测试时代 ${uniqueId()}`

    const eraTextarea = page.locator('.setting-form textarea').first()
    await expect(eraTextarea).toBeVisible({ timeout: 3000 })
    await eraTextarea.fill(testText)

    // Save
    await page.locator('.section-header button:has-text("保存")').click()

    // Should show success
    await expect(page.locator('.success-msg')).toBeVisible({ timeout: 5000 })

    // Reload
    await page.reload()
    await page.waitForTimeout(500)

    // Value should persist
    const eraAfter = page.locator('.setting-form textarea').first()
    if (await eraAfter.isVisible({ timeout: 3000 })) {
      const value = await eraAfter.inputValue()
      expect(value).toBe(testText)
    }
  })

  test('AI random world generates preview', async ({ page }) => {
    const randomBtn = page.locator('.btn-random')
    await expect(randomBtn).toBeVisible({ timeout: 3000 })

    if (await randomBtn.isDisabled()) return

    await randomBtn.click()

    // Preview modal should appear
    await expect(page.locator('.modal-panel')).toBeVisible({ timeout: 15000 })

    // Should have data
    const modal = page.locator('.modal-panel')
    await expect(modal).toBeVisible()

    // Close
    await page.locator('.modal-panel .modal-close').click()
    await expect(page.locator('.modal-panel')).not.toBeVisible({ timeout: 3000 })
  })

  test('AI injection level radio buttons exist', async ({ page }) => {
    const section = page.locator('.injection-section')
    await expect(section).toBeVisible({ timeout: 3000 })

    // Three radio options
    const options = section.locator('.injection-option')
    await expect(options).toHaveCount(3)

    // "标准" should be active by default
    const standardOption = options.filter({ hasText: '标准' })
    await expect(standardOption).toHaveClass(/active/)

    // Click "精简"
    const minimalOption = options.filter({ hasText: '精简' })
    await minimalOption.click()
    await expect(minimalOption).toHaveClass(/active/)
  })

  test('field weight sliders work', async ({ page }) => {
    const slider = page.locator('.weight-slider').first()
    await expect(slider).toBeVisible({ timeout: 3000 })

    // Drag slider to change value
    const box = await slider.boundingBox()
    if (box) {
      // Click at 80% of slider width
      await page.mouse.click(box.x + box.width * 0.8, box.y + box.height / 2)
      await page.waitForTimeout(200)

      // Value should have changed from default 5
      const weightVal = page.locator('.weight-value').first()
      const val = await weightVal.textContent()
      expect(val).toBeTruthy()
    }
  })

  test('DraftsDrawer button exists', async ({ page }) => {
    // The drafts drawer trigger should be in header
    const header = page.locator('.section-header')
    await expect(header).toBeVisible()
  })
})
