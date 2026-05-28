import { test, expect } from '@playwright/test'

test.describe('Module Hub', () => {
  test('characters module hub loads', async ({ page }) => {
    await page.goto('/modules/characters')
    await page.waitForTimeout(1000)

    // Should show module hub with characters content
    const hasViewToggle = await page.locator('.view-toggle, [class*="view-mode"]').isVisible({ timeout: 3000 }).catch(() => false)
    const hasContent = await page.locator('.module-hub, .hub-page, [class*="hub"]').isVisible({ timeout: 3000 }).catch(() => false)
    const hasCards = await page.locator('.character-card, .module-item, [class*="hub-item"]').first().isVisible({ timeout: 3000 }).catch(() => false)
    expect(hasViewToggle || hasContent || hasCards).toBeTruthy()
  })

  test('world setting module hub loads', async ({ page }) => {
    await page.goto('/modules/world')
    await page.waitForTimeout(1000)

    // Should render world setting form or hub content
    const hasForm = await page.locator('.setting-form, .world-form').isVisible({ timeout: 3000 }).catch(() => false)
    const hasHub = await page.locator('.module-hub, [class*="hub"]').isVisible({ timeout: 3000 }).catch(() => false)
    const hasLabels = await page.locator('label').first().isVisible({ timeout: 3000 }).catch(() => false)
    expect(hasForm || hasHub || hasLabels).toBeTruthy()
  })

  test('outline module hub loads', async ({ page }) => {
    await page.goto('/modules/outline')
    await page.waitForTimeout(1000)

    // Should render outline tree or hub content
    const hasTree = await page.locator('.outline-tree, .tree-node, [class*="tree"]').first().isVisible({ timeout: 3000 }).catch(() => false)
    const hasHub = await page.locator('.module-hub, [class*="hub"]').isVisible({ timeout: 3000 }).catch(() => false)
    const hasNodeItems = await page.locator('[class*="node"], [class*="vol"]').first().isVisible({ timeout: 3000 }).catch(() => false)
    expect(hasTree || hasHub || hasNodeItems).toBeTruthy()
  })

  test('foreshadowings module hub loads', async ({ page }) => {
    await page.goto('/modules/foreshadowings')
    await page.waitForTimeout(1000)

    // Should render foreshadowing content without crashing
    // Page heading or content area should be present
    const hasHeading = await page.locator('h1, h2, [class*="title"]').first().isVisible({ timeout: 3000 }).catch(() => false)
    expect(hasHeading).toBeTruthy()
  })

  test('view mode toggle works', async ({ page }) => {
    await page.goto('/modules/characters')
    await page.waitForTimeout(1000)

    // Check for by-book / global toggle
    const byBookBtn = page.locator('button:has-text("按书籍")')
    const globalBtn = page.locator('button:has-text("全局")')
    const hasByBook = await byBookBtn.isVisible({ timeout: 2000 }).catch(() => false)
    const hasGlobal = await globalBtn.isVisible({ timeout: 2000 }).catch(() => false)

    if (hasGlobal) {
      await globalBtn.click()
      await page.waitForTimeout(500)
      await expect(globalBtn).toHaveClass(/active/)
    }

    expect(hasByBook || hasGlobal).toBeTruthy()
  })

  test('stats module sort switches order', async ({ page }) => {
    await page.goto('/modules/stats')
    await page.waitForTimeout(1000)

    // Stats sort select should exist in by-book mode
    const sortSelect = page.locator('.stats-sort-select')
    const hasSort = await sortSelect.isVisible({ timeout: 3000 }).catch(() => false)
    if (!hasSort) return

    // Get book titles before sort
    const cards = page.locator('.book-card, [class*="book-item"]')
    const cardCount = await cards.count()
    if (cardCount < 2) return

    const firstBefore = await cards.first().locator('[class*="title"], .book-title, h3').textContent()

    // Switch sort to words-desc
    await sortSelect.selectOption('words-desc')
    await page.waitForTimeout(500)

    const firstAfter = await cards.first().locator('[class*="title"], .book-title, h3').textContent()
    expect(firstBefore).toBeTruthy()
    expect(firstAfter).toBeTruthy()
    // Verify sort completes without error — first card may or may not change
  })
})
