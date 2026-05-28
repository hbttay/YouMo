import { test, expect } from '@playwright/test'
import { setupAllMocks } from '../utils/mock-setup.js'

test.describe('Character Management', () => {
  const uniqueId = () => Date.now().toString(36)

  test.beforeEach(async ({ page }) => {
    await setupAllMocks(page)
    await page.goto('/books')
    await page.locator('.book-card').first().click()
    await page.getByText('角色管理').click()
    await page.waitForTimeout(500)
  })

  test('character list loads with cards', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('角色管理', { timeout: 5000 })

    // Should have at least the seeded character or empty state
    const hasCards = await page.locator('.character-card').first().isVisible({ timeout: 3000 }).catch(() => false)
    const hasEmpty = await page.locator('.empty-state').isVisible({ timeout: 3000 }).catch(() => false)
    expect(hasCards || hasEmpty).toBeTruthy()
  })

  test('create character via slide panel', async ({ page }) => {
    const charName = `E2E 角色 ${uniqueId()}`

    // If empty state, use its button; otherwise use header button
    const emptyBtn = page.locator('.empty-state .btn-primary')
    if (await emptyBtn.isVisible({ timeout: 2000 })) {
      await emptyBtn.click()
    } else {
      await page.locator('.section-header .btn-primary').filter({ hasText: '新建角色' }).click()
    }

    // Slide panel should open
    await expect(page.locator('.slide-panel')).toBeVisible({ timeout: 3000 })

    // Fill form
    await page.locator('.slide-panel input[placeholder="角色名"]').fill(charName)
    await page.locator('.slide-panel select').first().selectOption('女')

    // Submit
    await page.locator('.slide-panel button:has-text("创建角色")').click()

    // Panel should close after success
    await expect(page.locator('.slide-panel')).not.toBeVisible({ timeout: 5000 })

    // New character should appear in list
    await expect(page.locator('.char-name').filter({ hasText: charName }).first()).toBeVisible({ timeout: 5000 })
  })

  test('edit character via slide panel', async ({ page }) => {
    // Click first character card to expand
    const card = page.locator('.character-card').first()
    await expect(card).toBeVisible({ timeout: 5000 })
    await card.click()
    await page.waitForTimeout(300)

    // Click edit button
    const editBtn = card.locator('button:has-text("编辑")')
    await editBtn.click()

    // Slide panel opens
    await expect(page.locator('.slide-panel')).toBeVisible({ timeout: 3000 })

    // Change name
    const newName = `改 ${uniqueId()}`
    const nameInput = page.locator('.slide-panel input[placeholder="角色名"]')
    await nameInput.fill(newName)

    // Save
    await page.locator('.slide-panel button:has-text("保存修改")').click()

    // Panel closes
    await expect(page.locator('.slide-panel')).not.toBeVisible({ timeout: 5000 })

    // Updated name appears
    await expect(page.locator('.char-name').filter({ hasText: newName }).first()).toBeVisible({ timeout: 5000 })
  })

  test('delete character with confirmation', async ({ page }) => {
    const before = await page.locator('.character-card').count()

    const card = page.locator('.character-card').first()
    await card.click()
    await page.waitForTimeout(300)

    const delBtn = card.locator('button:has-text("删除")')
    await delBtn.click()

    // ModalConfirm should appear
    await expect(page.locator('.modal-overlay')).toBeVisible({ timeout: 3000 })

    // Confirm
    await page.locator('.modal-overlay button:has-text("确定")').click()

    // Card count should decrease
    await page.waitForTimeout(1000)
    const after = await page.locator('.character-card').count()
    expect(after).toBeLessThan(before)
  })

  test('view switching — grid, list, graph', async ({ page }) => {
    // Only test if there are characters
    const cards = page.locator('.character-card')
    if ((await cards.count()) === 0) return

    // Grid view (default)
    await expect(page.locator('.grid-view').first()).toBeVisible({ timeout: 3000 })

    // Switch to list
    await page.locator('.toggle-btn').nth(1).click()
    await page.waitForTimeout(300)
    await expect(page.locator('.list-view').first()).toBeVisible({ timeout: 3000 })

    // Switch to graph
    await page.locator('.toggle-btn').nth(2).click()
    await page.waitForTimeout(800)
    await expect(page.locator('.graph-section').first()).toBeVisible({ timeout: 5000 })
  })

  test('sort switching changes order', async ({ page }) => {
    const cards = page.locator('.character-card')
    if ((await cards.count()) < 2) return

    const firstBefore = await cards.first().locator('.char-name').textContent()

    // Switch sort
    await page.locator('.sort-select').selectOption('name-desc')
    await page.waitForTimeout(500)

    const firstAfter = await cards.first().locator('.char-name').textContent()
    // Name order should actually differ after sort switch
    expect(firstBefore).toBeTruthy()
    expect(firstAfter).toBeTruthy()
    expect(firstBefore).not.toBe(firstAfter)
  })

  test('AI random character generates data in preview', async ({ page }) => {
    // Need synopsis for random to work — seeded book has core_idea
    const randomBtn = page.locator('button:has-text("随机角色")')
    await expect(randomBtn).toBeVisible({ timeout: 3000 })

    // If generating is disabled, synopsis might be empty → skip
    if (await randomBtn.isDisabled()) return

    await randomBtn.click()

    // RandomPreviewModal should appear
    await expect(page.locator('.modal-panel')).toBeVisible({ timeout: 15000 })

    // Verify preview data — mock returns name '云汐', gender '女', depth 'L3'
    const previewName = page.locator('.modal-panel .preview-name')
    if (await previewName.isVisible({ timeout: 3000 })) {
      await expect(previewName).toContainText('云汐', { timeout: 3000 })
    }
    // Check that gender badge shows '女'
    const genderBadge = page.locator('.modal-panel .preview-gender, .modal-panel [class*="gender"]')
    if (await genderBadge.isVisible({ timeout: 1000 }).catch(() => false)) {
      await expect(genderBadge).toContainText('女', { timeout: 2000 })
    }

    // Close
    await page.locator('.modal-panel .modal-close').click()
    await expect(page.locator('.modal-panel')).not.toBeVisible({ timeout: 3000 })
  })

  test('character fission modal works', async ({ page }) => {
    const card = page.locator('.character-card').first()
    if (!(await card.isVisible({ timeout: 3000 }))) return

    await card.click()
    await page.waitForTimeout(300)

    const fissionBtn = card.locator('button:has-text("裂变")')
    await fissionBtn.click()

    // Fission modal should appear
    await expect(page.locator('.fission-modal')).toBeVisible({ timeout: 3000 })

    // Should show source character name
    await expect(page.locator('.fission-source')).toBeVisible()

    // Slider should exist
    await expect(page.locator('.fission-slider')).toBeVisible()

    // Close
    await page.locator('.fission-close').click()
    await expect(page.locator('.fission-modal')).not.toBeVisible({ timeout: 3000 })
  })

  test('depth filter select exists', async ({ page }) => {
    await expect(page.locator('.depth-select')).toBeVisible()
    const options = await page.locator('.depth-select option').count()
    expect(options).toBeGreaterThanOrEqual(4)
  })
})
