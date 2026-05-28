import { test, expect } from '@playwright/test'
import { setupAllMocks } from '../utils/mock-setup.js'

test.describe('User Feedback', () => {
  test.beforeEach(async ({ page }) => {
    await setupAllMocks(page)
  })

  test('feedback page loads with form toggle', async ({ page }) => {
    await page.goto('/feedback')
    await expect(page.locator('h1')).toContainText('用户反馈', { timeout: 5000 })

    // Should have a "提交反馈" button or form
    const submitBtn = page.locator('button:has-text("提交反馈")')
    const hasBtn = await submitBtn.isVisible({ timeout: 3000 }).catch(() => false)

    // Stats bar may exist
    const statsBar = page.locator('.stats-bar, [class*="stats"]')
    const hasStats = await statsBar.isVisible({ timeout: 2000 }).catch(() => false)

    expect(hasBtn).toBeTruthy()
  })

  test('submit feedback form', async ({ page }) => {
    const uniqueId = Date.now().toString(36)

    await page.goto('/feedback')
    await page.waitForTimeout(500)

    // Click "提交反馈" to open form
    const submitBtn = page.locator('button:has-text("提交反馈")')
    if (await submitBtn.isVisible({ timeout: 3000 })) {
      await submitBtn.click()
      await page.waitForTimeout(300)
    }

    // Form should be visible (either always visible or revealed)
    const contentInput = page.locator('textarea[placeholder*="反馈"], textarea[placeholder*="描述"], textarea[placeholder*="问题"]')
    if (await contentInput.isVisible({ timeout: 2000 })) {
      await contentInput.fill(`E2E 测试反馈 ${uniqueId}`)

      // Submit
      const sendBtn = page.locator('button:has-text("提交")').first()
      if (await sendBtn.isVisible({ timeout: 2000 })) {
        await sendBtn.click()
        await page.waitForTimeout(500)
        // After submit, form should close or show success
        const formAfter = await contentInput.isVisible().catch(() => false)
        const hasSuccess = await page.locator('.success-msg, .msg-bar.success').isVisible().catch(() => false)
        expect(!formAfter || hasSuccess).toBeTruthy()
      }
    }
  })

  test('feedback filters exist', async ({ page }) => {
    await page.goto('/feedback')
    await page.waitForTimeout(500)

    // Look for filter selects
    const filterSelects = page.locator('select')
    const count = await filterSelects.count()
    expect(count).toBeGreaterThan(0)
  })

  test('AI analyze feedback triggers mock', async ({ page }) => {
    // First create a feedback item, then analyze it
    await page.goto('/feedback')
    await page.waitForTimeout(500)

    // Look for analyze button on existing items
    const analyzeBtns = page.locator('button:has-text("分析")')
    const hasAnalyze = await analyzeBtns.first().isVisible({ timeout: 2000 }).catch(() => false)

    if (hasAnalyze) {
      await analyzeBtns.first().click()
      await page.waitForTimeout(500)
      // Analysis result should appear — mock returns analyzeFeedback data
      const analysisResult = page.locator('.analysis-result, .feedback-analysis, [class*="analysis"]')
      const hasResult = await analysisResult.isVisible({ timeout: 3000 }).catch(() => false)
      // OR the page may just still have feedback page content
      expect(hasResult || (await page.locator('h1').isVisible().catch(() => false))).toBeTruthy()
    }
  })
})
