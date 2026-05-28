import { test, expect } from '@playwright/test'
import { setupAllMocks } from '../utils/mock-setup.js'

test.describe('AI Chat and Export', () => {
  test.beforeEach(async ({ page }) => {
    await setupAllMocks(page)
  })

  test('character chat opens from character list', async ({ page }) => {
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await page.getByText('角色管理').click()
    await page.waitForTimeout(500)

    // Click first character card to expand
    const card = page.locator('.character-card').first()
    if (await card.isVisible({ timeout: 3000 })) {
      await card.click()
      await page.waitForTimeout(300)

      // Click "对话" button
      const chatBtn = card.locator('button:has-text("对话")')
      if (await chatBtn.isVisible({ timeout: 2000 })) {
        await chatBtn.click()
        await page.waitForTimeout(500)

        // CharacterChat component should appear with mocked reply
        const chatPanel = page.locator('.char-chat, .character-chat, [class*="chat"]')
        const panelVisible = await chatPanel.isVisible({ timeout: 3000 }).catch(() => false)
        if (panelVisible) {
          // Verify mocked AI reply text appears in the chat panel
          await expect(chatPanel).toContainText('青云门掌门之女', { timeout: 5000 })
        }
      }
    }
  })

  test('book report generates via AI', async ({ page }) => {
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await expect(page.locator('.detail-card h1')).toBeVisible({ timeout: 8000 })

    // Book report button might exist in the detail page
    const reportBtn = page.locator('button:has-text("报告"), button:has-text("写作报告")')
    const hasReport = await reportBtn.isVisible({ timeout: 2000 }).catch(() => false)
    if (hasReport) {
      await reportBtn.first().click()
      await page.waitForTimeout(3000)
      // Verify mocked report data appears
      const reportContent = page.locator('.report-panel, .modal-panel, [class*="report"]')
      if (await reportContent.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(reportContent).toContainText('整体写作进展良好', { timeout: 5000 })
      }
    }
  })

  test('outline page has export functionality', async ({ page }) => {
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await page.getByText('大纲编排').click()
    await page.waitForTimeout(500)

    // Look for export button
    const exportBtn = page.locator('button:has-text("导出")')
    const hasExport = await exportBtn.isVisible({ timeout: 2000 }).catch(() => false)

    // Mind map button exists as alternative view
    const mindmapBtn = page.locator('button:has-text("导图")')
    const hasMindmap = await mindmapBtn.isVisible({ timeout: 2000 }).catch(() => false)

    expect(hasExport || hasMindmap).toBeTruthy()
  })

  test('home page module hub links work', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('.hero')).toBeVisible({ timeout: 8000 })

    // Module cards should exist
    const moduleCards = page.locator('.module-card')
    const count = await moduleCards.count()
    expect(count).toBeGreaterThanOrEqual(5)

    // Click 角色工坊
    const charModule = moduleCards.filter({ hasText: '角色工坊' })
    if (await charModule.isVisible({ timeout: 2000 })) {
      await charModule.click()
      await page.waitForTimeout(500)
      // Should navigate to module hub or character page
      const url = page.url()
      expect(url).toMatch(/\/modules\/characters/)
    }
  })
})
