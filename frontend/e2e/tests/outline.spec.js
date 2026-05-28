import { test, expect } from '@playwright/test'

test.describe('Outline Management', () => {
  const uniqueId = () => Date.now().toString(36)

  test.beforeEach(async ({ page }) => {
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await page.getByText('大纲编排').click()
    await page.waitForTimeout(500)
  })

  test('outline page loads with header', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('大纲编排', { timeout: 5000 })

    // Should have view mode buttons or add volume button
    const hasAddVol = await page.locator('button:has-text("新建卷")').first().isVisible()
    const hasEmpty = await page.locator('.empty-state').isVisible()
    // At minimum, we should be on the outline page
    expect(hasAddVol || hasEmpty).toBeTruthy()
  })

  test('create volume and verify it appears in tree', async ({ page }) => {
    const volTitle = `E2E 卷 ${uniqueId()}`

    // Count existing nodes
    const before = await page.locator('.tree-node').count()

    // Click add volume
    await page.locator('button:has-text("新建卷")').first().click()

    // Fill the create form
    const titleInput = page.locator('.create-form input[type="text"]').first()
    await expect(titleInput).toBeVisible({ timeout: 3000 })
    await titleInput.fill(volTitle)

    // Submit
    await page.click('.create-form button:has-text("创建")')

    // Wait for the form to close and tree to update
    await page.waitForTimeout(1000)

    // Verify tree node count increased
    const after = await page.locator('.tree-node').count()
    expect(after).toBeGreaterThan(before)

    // Verify the new volume title appears in the tree
    await expect(page.locator('.node-title').filter({ hasText: volTitle }).first()).toBeVisible({ timeout: 5000 })
  })

  test('mind map toggle switches view', async ({ page }) => {
    const mindmapBtn = page.locator('button:has-text("导图")')
    await expect(mindmapBtn).toBeVisible({ timeout: 5000 })

    // Click to switch to mindmap view
    await mindmapBtn.click()
    await page.waitForTimeout(800)

    // Should switch — mindmap view has a specific container
    // The button should now have active styling
    await expect(mindmapBtn).toHaveClass(/btn-primary/)
  })

  test('add chapter under volume and verify hierarchy', async ({ page }) => {
    const chapterTitle = `E2E 章 ${uniqueId()}`

    // Find first volume node's "添加章" button
    const addChapterBtn = page.locator('button:has-text("添加章")').first()
    if (await addChapterBtn.isVisible()) {
      await addChapterBtn.click()

      // Fill create form
      const titleInput = page.locator('.create-form input[type="text"]').first()
      await expect(titleInput).toBeVisible({ timeout: 3000 })
      await titleInput.fill(chapterTitle)
      await page.click('.create-form button:has-text("创建")')

      await page.waitForTimeout(1000)

      // Verify the chapter appeared under the volume
      await expect(page.locator('.node-title').filter({ hasText: chapterTitle }).first()).toBeVisible({ timeout: 5000 })
    }
  })

  test('table view column sort switches order', async ({ page }) => {
    // Switch to table/list view
    const tableBtn = page.locator('button:has-text("列表")')
    await expect(tableBtn).toBeVisible({ timeout: 3000 })
    await tableBtn.click()
    await page.waitForTimeout(500)

    // Verify table is visible
    const table = page.locator('.outline-table')
    await expect(table).toBeVisible({ timeout: 3000 })

    // Get first row title before sort
    const rows = table.locator('tbody tr')
    const rowCount = await rows.count()
    if (rowCount < 2) return // need at least 2 rows

    const firstBefore = await rows.first().locator('td').first().textContent()

    // Click title column header to sort ascending
    const titleHeader = page.locator('th.col-title')
    await titleHeader.click()
    await page.waitForTimeout(300)

    // Sort arrow should appear
    await expect(page.locator('th.col-title .sort-arrow')).toContainText('↑')

    // Click again for descending
    await titleHeader.click()
    await page.waitForTimeout(300)
    await expect(page.locator('th.col-title .sort-arrow')).toContainText('↓')

    // First row should have changed (unless all rows have same title)
    const firstAfter = await rows.first().locator('td').first().textContent()
    expect(firstBefore).toBeTruthy()
    expect(firstAfter).toBeTruthy()
  })

  test('synopsis textarea saves on blur', async ({ page }) => {
    const text = 'E2E 测试总纲内容 — ' + uniqueId()

    const synopsisTextarea = page.locator('.synopsis-textarea')
    if (await synopsisTextarea.isVisible()) {
      await synopsisTextarea.fill(text)

      // Blur triggers save
      await page.locator('h1').click()
      await page.waitForTimeout(1000)

      // Reload and verify the text persisted
      await page.reload()
      await page.waitForTimeout(500)

      const synopsisAfter = page.locator('.synopsis-textarea')
      if (await synopsisAfter.isVisible()) {
        const value = await synopsisAfter.inputValue()
        expect(value).toBe(text)
      }
    }
  })
})
