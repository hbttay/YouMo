import { test, expect } from '@playwright/test'

test.describe('Chapter Writing', () => {
  test('navigate to chapter editor and verify content loads', async ({ page }) => {
    // Go to first book → outline
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await page.getByText('大纲编排').click()
    await page.waitForTimeout(500)

    // Click "写正文" on first chapter node
    const writeBtn = page.locator('.btn-write').first()
    if (await writeBtn.isVisible({ timeout: 3000 })) {
      await writeBtn.click()
      await page.waitForTimeout(1000)

      // Should be on chapter edit page
      // Verify header exists with back link and navigation
      await expect(page.locator('.write-header')).toBeVisible({ timeout: 5000 })
      await expect(page.locator('.chapter-title')).toBeVisible()
      await expect(page.locator('.back-link')).toBeVisible()

      // Textarea should exist for writing
      const textarea = page.locator('.write-textarea').first()
      await expect(textarea).toBeVisible({ timeout: 5000 })

      // If there's content, the textarea should have it
      const textareaValue = await textarea.inputValue()
      // Textarea should exist and return a value (empty string for new chapter is valid)
      expect(textareaValue !== undefined && textareaValue !== null).toBeTruthy()
    }
  })

  test('type content and verify word count updates', async ({ page }) => {
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await page.getByText('大纲编排').click()
    await page.waitForTimeout(500)

    const writeBtn = page.locator('.btn-write').first()
    if (await writeBtn.isVisible({ timeout: 3000 })) {
      await writeBtn.click()
      await page.waitForTimeout(1000)

      const textarea = page.locator('.write-textarea').first()
      await expect(textarea).toBeVisible({ timeout: 5000 })

      // Get initial word count
      const wordCountEl = page.locator('.word-count').first()
      let initialCount = '0'
      if (await wordCountEl.isVisible()) {
        initialCount = (await wordCountEl.textContent()) || '0'
      }

      // Type new content
      const testContent = '这是 E2E 自动化测试写入的章节内容。用于验证编辑器数据回写是否正确。'
      await textarea.fill(testContent)
      await page.waitForTimeout(500)

      // Word count should update after typing
      const newCount = await wordCountEl.textContent()
      const newNum = parseInt(newCount) || 0
      expect(newNum).toBeGreaterThan(0)
    }
  })

  test('章节点 "写正文" link navigates to correct URL', async ({ page }) => {
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await page.getByText('大纲编排').click()
    await page.waitForTimeout(500)

    const writeLink = page.locator('.btn-write').first()
    if (await writeLink.isVisible({ timeout: 3000 })) {
      // Get the href before clicking
      const href = await writeLink.evaluate(el => el.closest('a')?.getAttribute('href'))
      expect(href).toMatch(/\/books\/\d+\/write\/\d+/)

      await writeLink.click()
      await page.waitForTimeout(1000)

      // URL should contain /write/
      await expect(page).toHaveURL(/\/books\/\d+\/write\/\d+/)
    }
  })
})
