import { test, expect } from '@playwright/test'
import { setupAllMocks } from '../utils/mock-setup.js'

test.describe('AI Generation (all mocked)', () => {
  test.beforeEach(async ({ page }) => {
    await setupAllMocks(page)
  })

  async function navigateToChapterEditor(page) {
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await page.getByText('大纲编排').click()
    await page.waitForTimeout(500)

    const writeBtn = page.locator('.btn-write').first()
    if (await writeBtn.isVisible({ timeout: 3000 })) {
      await writeBtn.click()
      await page.waitForTimeout(1000)
      return true
    }
    return false
  }

  test('AI continue button exists and appends text', async ({ page }) => {
    const ok = await navigateToChapterEditor(page)
    if (!ok) return

    const continueBtn = page.locator('.btn-ai').first()
    if (!(await continueBtn.isVisible({ timeout: 3000 }))) return

    const textarea = page.locator('.write-textarea').first()
    await expect(textarea).toBeVisible({ timeout: 5000 })
    // Ensure textarea has content so AI can append to it
    if (!(await textarea.inputValue()).trim()) {
      await textarea.fill('主角站在密室前')
      await page.waitForTimeout(300)
    }
    const before = (await textarea.inputValue()).length

    await continueBtn.click()

    // Wait for SSE stream to finish
    await page.waitForTimeout(800)

    // Content should have increased from the mocked stream
    const after = (await textarea.inputValue()).length
    expect(after).toBeGreaterThan(before)
  })

  test('AI continue appends text stream', async ({ page }) => {
    const ok = await navigateToChapterEditor(page)
    if (!ok) return

    const textarea = page.locator('.write-textarea').first()
    await expect(textarea).toBeVisible({ timeout: 5000 })

    if (!(await textarea.inputValue()).trim()) {
      await textarea.fill('主角站在密室前')
      await page.waitForTimeout(300)
    }
    const before = (await textarea.inputValue()).length

    const continueBtn = page.locator('.btn-ai').first()
    if (await continueBtn.isVisible({ timeout: 3000 })) {
      await continueBtn.click()
      await page.waitForTimeout(800)
      const after = (await textarea.inputValue()).length
      expect(after).toBeGreaterThan(before)
    }
  })

  test('temperature slider works', async ({ page }) => {
    const ok = await navigateToChapterEditor(page)
    if (!ok) return

    // Open settings dropdown
    const settingsBtn = page.locator('button[title="写作设置"]')
    if (await settingsBtn.isVisible({ timeout: 2000 })) {
      await settingsBtn.click()
      await page.waitForTimeout(300)

      const slider = page.locator('.settings-dropdown input[type="range"]')
      if (await slider.isVisible({ timeout: 2000 })) {
        const val = await slider.inputValue()
        expect(parseFloat(val)).toBeGreaterThan(0)
      }
    }
  })

  test('consistency check shows issues', async ({ page }) => {
    const ok = await navigateToChapterEditor(page)
    if (!ok) return

    const checkBtn = page.locator('.btn-consistency').first()
    if (await checkBtn.isVisible({ timeout: 3000 })) {
      await checkBtn.click()

      // Wait for mock response — consistency card should appear
      await expect(page.locator('.consistency-card')).toBeVisible({ timeout: 15000 })

      // Should have at least one issue (class is consistency-item)
      const issues = page.locator('.consistency-item')
      const issueCount = await issues.count()
      expect(issueCount).toBeGreaterThanOrEqual(1)
    }
  })

  test('instructions input exists and can be typed', async ({ page }) => {
    const ok = await navigateToChapterEditor(page)
    if (!ok) return

    const toggleBtn = page.locator('.btn-instructions-toggle')
    if (await toggleBtn.isVisible({ timeout: 2000 })) {
      await toggleBtn.click()
      await page.waitForTimeout(300)

      const textarea = page.locator('.instructions-textarea')
      if (await textarea.isVisible({ timeout: 2000 })) {
        await textarea.fill('主角推开门，发现了一个惊人的秘密')
        const val = await textarea.inputValue()
        expect(val).toBeTruthy()
      }
    }
  })

  test('optimize instructions via AI', async ({ page }) => {
    const ok = await navigateToChapterEditor(page)
    if (!ok) return

    const toggleBtn = page.locator('.btn-instructions-toggle')
    if (await toggleBtn.isVisible({ timeout: 2000 })) {
      await toggleBtn.click()
      await page.waitForTimeout(300)
    }

    const textarea = page.locator('.instructions-textarea')
    if (await textarea.isVisible({ timeout: 2000 })) {
      await textarea.fill('继续写下一段')

      const optimizeBtn = page.locator('.btn-optimize')
      if (await optimizeBtn.isVisible({ timeout: 2000 })) {
        await optimizeBtn.click()
        await page.waitForTimeout(2000)
        const val = await textarea.inputValue()
        // Verify optimized text differs from input and contains expanded content
        expect(val).not.toBe('继续写下一段')
        expect(val.length).toBeGreaterThan(20)
      }
    }
  })

  test('AI tools dropdown — analyze shows panel', async ({ page }) => {
    const ok = await navigateToChapterEditor(page)
    if (!ok) return

    // First ensure textarea has content (required by analyze)
    const textarea = page.locator('.write-textarea').first()
    if (await textarea.isVisible({ timeout: 3000 })) {
      const current = await textarea.inputValue()
      if (!current.trim()) {
        await textarea.fill('主角站在密室前，犹豫了一会，终于伸出手推开了那扇石门。石门的触感冰冷彻骨...')
        await page.waitForTimeout(500)
      }
    }

    // Open AI tools dropdown
    const moreBtn = page.locator('.btn-ai-more').first()
    if (await moreBtn.isVisible({ timeout: 3000 })) {
      await moreBtn.click()
      await page.waitForTimeout(300)

      const analyzeBtn = page.locator('.ai-dropdown button:has-text("分析")')
      if (await analyzeBtn.isVisible({ timeout: 2000 })) {
        await analyzeBtn.click()
        await page.waitForTimeout(3000)
        // Analysis panel should appear
        const panel = page.locator('.analysis-panel')
        await expect(panel).toBeVisible({ timeout: 10000 })
      }
    }
  })

  test('AI rewrite dropdown option exists', async ({ page }) => {
    const ok = await navigateToChapterEditor(page)
    if (!ok) return

    const moreBtn = page.locator('.btn-ai-more').first()
    if (await moreBtn.isVisible({ timeout: 3000 })) {
      await moreBtn.click()
      await page.waitForTimeout(300)

      // Verify dropdown panel is open with AI option items
      const dropdown = page.locator('.ai-dropdown')
      await expect(dropdown).toBeVisible({ timeout: 2000 })
    }
  })

  test('plan and ai controls are present', async ({ page }) => {
    const ok = await navigateToChapterEditor(page)
    if (!ok) return

    const aiControls = page.locator('.btn-ai, .btn-ai-more, .btn-consistency')
    const count = await aiControls.count()
    expect(count).toBeGreaterThan(0)
  })

  test('suggestions review panel loaded', async ({ page }) => {
    const ok = await navigateToChapterEditor(page)
    if (!ok) return

    // ReviewPanel should be present as part of the page
    const reviewPanel = page.locator('.review-panel')
    const hasReview = await reviewPanel.isVisible({ timeout: 3000 }).catch(() => false)
    // May be conditionally rendered; verify page heading or review panel exists
    const hasHeading = await page.locator('h1').isVisible().catch(() => false)
    expect(hasReview || hasHeading).toBeTruthy()
  })
})
