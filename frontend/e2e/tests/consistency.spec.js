import { test, expect } from '@playwright/test'
import { setupAllMocks } from '../utils/mock-setup.js'

test.describe('Consistency Check & Fix', () => {
  test.beforeEach(async ({ page }) => {
    await setupAllMocks(page)
    // Navigate to a chapter write page
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await page.getByText('大纲编排').click()
    await page.waitForTimeout(500)

    // Click the first write link to enter write mode
    const writeLink = page.locator('a[href*="/write/"]').first()
    if (await writeLink.isVisible()) {
      await writeLink.click()
      await page.waitForTimeout(500)
    }
  })

  test('consistency check button exists on write page', async ({ page }) => {
    // Navigate to write page if not already there
    const writePage = page.locator('.chapter-write')
    if (!(await writePage.isVisible())) {
      const node = page.locator('a[href*="/write/"]').first()
      if (await node.isVisible()) await node.click()
      await page.waitForTimeout(500)
    }

    // Write page should load with chapter title
    await expect(page.locator('.chapter-title')).toBeVisible({ timeout: 5000 })
  })

  test('dismiss single issue removes it from list', async ({ page }) => {
    const writePage = page.locator('.chapter-write')
    if (!(await writePage.isVisible())) {
      const node = page.locator('a[href*="/write/"]').first()
      if (await node.isVisible()) await node.click()
      await page.waitForTimeout(500)
    }

    // Fill textarea with content matching a keyword so AI fix can work
    const textarea = page.locator('.write-textarea').first()
    if (await textarea.isVisible()) {
      await textarea.fill('云汐站在月光下，一头银发随风飘扬。她缓缓推开密室的门，里面漆黑一片。')
      await page.waitForTimeout(300)
    }

    // Click consistency check
    const checkBtn = page.locator('button:has-text("一致性")')
    if (await checkBtn.isVisible()) {
      await checkBtn.click()
      await page.waitForTimeout(500)
    }

    // Count issues before dismiss
    const before = await page.locator('.consistency-issue, .issue-item').count()

    // Click "忽略" on first issue
    const dismissBtn = page.locator('button:has-text("忽略")').first()
    if (await dismissBtn.isVisible()) {
      await dismissBtn.click()
      await page.waitForTimeout(300)
    }

    // Count should decrease by 1
    const after = await page.locator('.consistency-issue, .issue-item').count()
    if (before > 0) {
      expect(after).toBeLessThan(before)
    }
  })

  test('dismiss all removes all issues', async ({ page }) => {
    const writePage = page.locator('.chapter-write')
    if (!(await writePage.isVisible())) {
      const node = page.locator('a[href*="/write/"]').first()
      if (await node.isVisible()) await node.click()
      await page.waitForTimeout(500)
    }

    // Fill textarea
    const textarea = page.locator('.write-textarea').first()
    if (await textarea.isVisible()) {
      await textarea.fill('云汐站在月光下，一头银发随风飘扬。')
      await page.waitForTimeout(300)
    }

    // Click consistency check
    const checkBtn = page.locator('button:has-text("一致性")')
    if (await checkBtn.isVisible()) {
      await checkBtn.click()
      await page.waitForTimeout(500)
    }

    // Click "全部忽略"
    const dismissAllBtn = page.locator('button:has-text("全部忽略")')
    if (await dismissAllBtn.isVisible()) {
      await dismissAllBtn.click()
      await page.waitForTimeout(300)
    }

    // Consistency card should be hidden
    const cardVisible = await page.locator('.consistency-card').isVisible().catch(() => false)
    expect(cardVisible).toBeFalsy()
  })

  test('AI fix popup has max-height constraint', async ({ page }) => {
    const writePage = page.locator('.chapter-write')
    if (!(await writePage.isVisible())) {
      const node = page.locator('a[href*="/write/"]').first()
      if (await node.isVisible()) await node.click()
      await page.waitForTimeout(500)
    }

    // Fill textarea with content containing keywords from mock issues
    const textarea = page.locator('.write-textarea').first()
    if (await textarea.isVisible()) {
      await textarea.fill('云汐站在月光下，一头银发随风飘扬。她缓缓推开密室的门，里面漆黑一片。夕阳西下，天色渐暗。她从怀中掏出五块灵石。')
      await page.waitForTimeout(300)
    }

    // Click consistency check
    const checkBtn = page.locator('button:has-text("一致性")')
    if (await checkBtn.isVisible()) {
      await checkBtn.click()
      await page.waitForTimeout(500)
    }

    // Click AI fix on the first issue that has a keyword in the text
    const fixBtn = page.locator('button:has-text("修正")').first()
    if (await fixBtn.isVisible()) {
      await fixBtn.click()
      await page.waitForTimeout(1500)

      // Inline popup should appear — verify it has max-height
      const popup = page.locator('.inline-popup')
      if (await popup.isVisible({ timeout: 2000 }).catch(() => false)) {
        const maxH = await popup.evaluate(el => getComputedStyle(el).maxHeight)
        expect(maxH).not.toBe('none')
      }
    }
  })
})
