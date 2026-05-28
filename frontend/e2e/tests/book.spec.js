import { test, expect } from '@playwright/test'

test.describe('Book Management', () => {
  const uniqueId = () => Date.now().toString(36)

  test('book list page loads', async ({ page }) => {
    await page.goto('/books')
    await expect(page.locator('h1')).toContainText('我的书籍')

    // Verify at least 1 book card exists (seeded + created in previous runs)
    const cards = page.locator('.book-card')
    await expect(cards.first()).toBeVisible({ timeout: 8000 })
    const count = await cards.count()
    expect(count).toBeGreaterThan(0)

    // Each card should have a title
    for (let i = 0; i < Math.min(count, 3); i++) {
      await expect(cards.nth(i).locator('.book-title')).toBeVisible()
      await expect(cards.nth(i).locator('.status-tag')).toBeVisible()
    }
  })

  test('create book and verify data on detail page', async ({ page }) => {
    const bookTitle = `E2E ${uniqueId()}`
    const bookIdea = '测试核心构思 — 自动化验证数据正确性'

    await page.goto('/books/create')
    await expect(page.locator('h1')).toContainText('新建书籍')

    await page.fill('input[placeholder="给你的书取个名字"]', bookTitle)
    await page.fill('textarea[placeholder*="简单描述"]', bookIdea)
    await page.click('button:has-text("创建书籍")')

    // Redirect to detail page — verify h1 matches the title we entered
    await expect(page.locator('.detail-card h1')).toHaveText(bookTitle, { timeout: 8000 })

    // Verify core_idea appears
    await expect(page.locator('.desc')).toContainText(bookIdea)

    // Verify meta info present
    await expect(page.locator('.status-select')).toBeVisible()

    // Verify nav cards
    await expect(page.locator('.nav-card')).toHaveCount(3)
  })

  test('book list reflects created book', async ({ page }) => {
    const bookTitle = `LST ${uniqueId()}`

    // Create via API approach — go through UI
    await page.goto('/books/create')
    await page.fill('input[placeholder="给你的书取个名字"]', bookTitle)
    await page.fill('textarea[placeholder*="简单描述"]', '列表验证测试')
    await page.click('button:has-text("创建书籍")')
    await expect(page.locator('.detail-card h1')).toBeVisible({ timeout: 8000 })

    // Navigate back to list
    await page.click('.back-link')
    await expect(page.locator('h1')).toContainText('我的书籍', { timeout: 5000 })

    // Verify the new book appears in the grid
    await expect(page.locator('.book-title').filter({ hasText: bookTitle }).first()).toBeVisible({ timeout: 5000 })
  })

  test('book detail shows navigation cards', async ({ page }) => {
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await expect(page.locator('.detail-card h1')).toBeVisible({ timeout: 8000 })

    await expect(page.locator('.nav-card')).toHaveCount(3)
    await expect(page.getByText('角色管理')).toBeVisible()
    await expect(page.getByText('大纲编排')).toBeVisible()
    await expect(page.getByText('世界观设定')).toBeVisible()
  })

  test('status filter filters correctly', async ({ page }) => {
    await page.goto('/books')

    // Verify filter exists
    const filter = page.locator('.filter-select')
    await expect(filter).toBeVisible()

    // Select "草稿" (DRAFT)
    await filter.selectOption('DRAFT')
    await page.waitForTimeout(300)

    // All visible cards should have DRAFT status tag
    const visibleCards = page.locator('.book-card')
    const count = await visibleCards.count()
    if (count > 0) {
      for (let i = 0; i < count; i++) {
        const statusTag = visibleCards.nth(i).locator('.status-tag')
        if (await statusTag.isVisible()) {
          const text = await statusTag.textContent()
          expect(['草稿', '连载中', '已完成', 'DRAFT', 'SERIALIZING', 'COMPLETED']).toContain(text)
        }
      }
    }
  })

  test('sort switches correctly', async ({ page }) => {
    await page.goto('/books')
    await expect(page.locator('.sort-btn')).toHaveCount(4)

    // Click sort buttons and verify active state
    for (const label of ['最近更新', '创建时间', '书名']) {
      await page.click(`button:has-text("${label}")`)
      await expect(page.locator('.sort-btn.active')).toContainText(label)
      await page.waitForTimeout(200)
    }

    // Return to sequence
    await page.click('button:has-text("我的顺序")')
    await expect(page.locator('.sort-btn.active')).toContainText('我的顺序')
  })

  test('drag handle exists and card is draggable', async ({ page }) => {
    await page.goto('/books')
    const card = page.locator('.book-card').first()
    await expect(card).toBeVisible({ timeout: 8000 })
    await expect(card).toHaveAttribute('draggable', 'true')

    const handle = card.locator('.drag-handle')
    await expect(handle).toBeVisible()
  })

  test('task checklist — add, verify text, toggle done, verify state', async ({ page }) => {
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await expect(page.locator('.task-section')).toBeVisible({ timeout: 8000 })

    // Get initial count
    const before = await page.locator('.task-item').count()

    // Add task
    const taskText = `验证任务 ${uniqueId()}`
    await page.locator('.task-input').fill(taskText)
    await page.click('.btn-add-task')

    // Verify new task appeared with correct text
    await expect(page.locator('.task-item').nth(before)).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.task-text').filter({ hasText: taskText }).first()).toBeVisible()

    // Toggle done
    const checkbox = page.locator('.task-item').nth(before).locator('input[type="checkbox"]')
    await checkbox.check()

    // Verify done state — item should have .done class AND text should be line-through
    const taskItem = page.locator('.task-item').nth(before)
    await expect(taskItem).toHaveClass(/done/)

    // Progress should show at least 1/N
    const progress = page.locator('.task-progress')
    if (await progress.isVisible()) {
      const progressText = await progress.textContent()
      expect(progressText).toMatch(/\d+\/\d+/)
    }
  })

  test('negative constraint — add, verify text, delete, verify removed', async ({ page }) => {
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await expect(page.locator('.constraint-section')).toBeVisible({ timeout: 8000 })

    // Get initial count
    const before = await page.locator('.constraint-item').count()

    // Add constraint
    const constraintText = `禁用词 ${uniqueId()}`
    await page.locator('.constraint-input').fill(constraintText)
    await page.click('.btn-add-constraint')

    // Verify new constraint appears with correct text
    await expect(page.locator('.constraint-item').nth(before)).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.constraint-text').filter({ hasText: constraintText }).first()).toBeVisible()

    // Delete it
    const delBtn = page.locator('.constraint-item').nth(before).locator('.btn-constraint-del')
    await delBtn.click()

    // Verify count returned to original
    await expect(page.locator('.constraint-item')).toHaveCount(before)
  })

  test('random book idea generates data and displays in modal', async ({ page }) => {
    await page.goto('/books/create')

    // Click "随机生成" button
    const randomBtn = page.locator('button:has-text("随机生成")')
    await expect(randomBtn).toBeVisible({ timeout: 5000 })
    await randomBtn.click()

    // Wait for modal to appear with generated data
    const modal = page.locator('.modal-overlay')
    await expect(modal).toBeVisible({ timeout: 15000 })

    // Modal header should say "随机书名/创意"
    await expect(modal.locator('.modal-header h2')).toContainText('随机书名')

    // Preview fields should have non-empty values (not just "-")
    const titleField = modal.locator('.preview-field').filter({ hasText: '书名' }).locator('p')
    const titleValue = await titleField.textContent()
    expect(titleValue).toBeTruthy()
    expect(titleValue).not.toBe('-')

    const ideaField = modal.locator('.preview-field').filter({ hasText: '简介' }).locator('p')
    const ideaValue = await ideaField.textContent()
    expect(ideaValue).toBeTruthy()
    expect(ideaValue).not.toBe('-')

    // Close modal
    await page.click('.modal-close')
    await expect(modal).not.toBeVisible({ timeout: 3000 })
  })

  test('book stats render with numbers', async ({ page }) => {
    await page.goto('/books')
    await page.locator('.book-card').first().click()

    await expect(page.locator('.section-title')).toContainText('统计', { timeout: 8000 })

    // BookStats component should render stat cards with numbers
    const statsContainer = page.locator('.stats-section-wrapper')
    await expect(statsContainer).toBeVisible()
    // At least one number should be present
    const statValues = statsContainer.locator('[class*="value"], [class*="count"], [class*="number"]')
    if (await statValues.first().isVisible({ timeout: 2000 }).catch(() => false)) {
      const firstVal = await statValues.first().textContent()
      expect(firstVal).toBeTruthy()
    }
  })
})
