# E2E 测试规范 & 优化规则

> 每次发现测试漏过的 bug，在此记录规则。所有新增/修改代码必须对照此清单。
> 此文件随项目持续演进，不冻结。

---

## 🚨 改代码前必查（防漏测）

**修改/新增 UI 代码后，在说"完成"之前，逐条确认：**

1. **[ ] 有弹窗/浮层？** → 写了 `max-height` CSS 没？E2E 有 `getComputedStyle(el).maxHeight` 断言没？
2. **[ ] 有标题/标签文本？** → 写了 `overflow: hidden; text-overflow: ellipsis; white-space: nowrap` 没？
3. **[ ] 有多步交互流程？** → E2E 覆盖了 触发→中间状态→结果 每一步没？
4. **[ ] 有排序/筛选？** → E2E 比较了排序前后值不同没？(`not.toBe()`)
5. **[ ] 有导航/跳转？** → E2E 验证了 URL 变化没？**验证了内容确实变了没？**
6. **[ ] 新增了 mock 数据？** → 已读前端解析代码确认字段名一致没？
7. **[ ] 用了 `page.waitForTimeout`？** → 后面跟了断言没？
8. **[ ] 断言里有 `typeof` / `toBeTruthy()` / `|| true` / `>= 0`？** → 替换成具体值没？
9. **[ ] 用了 `const x = route.params.xxx`？** → 同一组件内需要响应路由变化的变量必须用 `ref()` 或 `computed()`，不能是 const**

---

## 核心原则

1. **不只要测存在，要测行为** — `expect(el).toBeVisible()` 不够，要验证数据、排序、交互结果
2. **每个 UI 交互都要有确认** — 点击按钮后验证状态变化（URL、DOM、数据）
3. **CSS 布局不可忽略** — 弹窗/卡片必须验证 max-height/overflow，标题必须验证截断
4. **Mock 数据要真实** — 用具体的中文数据（'云汐'、'青云门'），不要 'test' / 'foo'

---

## 测试类型 & 触发条件

| 场景 | 最少测试 | 示例 |
|------|---------|------|
| 新增 CRUD 功能 | 创建→验证出现 + 编辑→验证更新 + 删除→验证消失 | `outline.spec.js` |
| 新增交互流程（多步操作） | 端到端：触发→中间状态→结果 | 一致性 检查→修正→接受→消失 |
| 新增排序/筛选 | 排序前取值 → 排序后比较值不同 | `module-hub.spec.js` |
| 新增弹窗/浮层 | 验证可见 + 验证 max-height + 验证关闭 | CSS computed style 断言 |
| 新增导航/跳转 | 点击→验证 URL 变化 | `page.url()` 断言 |
| 新增 AI 功能 | Mock SSE 流 → 验证内容插入 → 验证状态变化 | `ai-generation.spec.js` |

---

## CSS 断言清单

每个弹窗/卡片/可滚动区域必须验证：

```js
// 弹窗有高度限制
const maxH = await el.evaluate(e => getComputedStyle(e).maxHeight)
expect(maxH).not.toBe('none')

// 弹窗内容可滚动
const overflow = await el.evaluate(e => getComputedStyle(e).overflowY)
expect(['auto', 'scroll']).toContain(overflow)

// 标题不折行（超过 15 字符的标题）
const whiteSpace = await el.evaluate(e => getComputedStyle(e).whiteSpace)
const overflow = await el.evaluate(e => getComputedStyle(e).textOverflow)
expect(whiteSpace).toBe('nowrap')
expect(overflow).toBe('ellipsis')
```

---

## 已知漏测案例（持续更新）

### 2026-05-28 — Vue route params 用 const 导致导航失效

- **问题**：翻页箭头点击刷了 URL，但正文内容不变
- **根因**：`const structureId = Number(route.params.structureId)` — 从 route 提取一次后固化，`loadContent()` 始终读到初始值。同一组件内 navigation（`router.push` + watch）不会重建组件，const 值永远停留在初始路由
- **应测未测**：点击 nav-btn 后 URL 变化 + 页面标题/内容变化
- **规则**：**Vue 组件内需要响应路由变化的变量必须用 `ref()` 或 `computed()`，不能在 setup 顶层用 `const` 固化 route 的值**。所有导航按钮必须 E2E 验证点击后内容确实变了（不只是 URL 变了）

### 2026-05-28 — fixingIdx 时序 bug

- **问题**：AI 修正后接受修复，问题不从列表移除，重检复发
- **根因**：`fixingIdx.value = -1` 在 SSE onDone 回调中过早重置
- **应测未测**：一致性 检查→修正→接受→验证列表项消失
- **规则**：多步异步流程必须端到端测试每一步的状态变化

### 2026-05-28 — AI 修正弹窗无高度限制

- **问题**：inline-popup 只设 max-width，无 max-height，长文本撑满屏幕
- **应测未测**：弹窗 computed style max-height !== 'none'
- **规则**：所有弹窗/浮层必须验证 max-height 和 overflow 属性

### 2026-05-28 — 翻页箭头不工作

- **问题**：◂ ▸ 按钮点击无反应；title 属性快捷键写错
- **应测未测**：点击 nav-btn 后 URL 变化
- **规则**：所有导航按钮必须验证点击后的 URL/路由变化

### 2026-05-28 — 章节标题折行

- **问题**：12 字标题折成 3 行
- **应测未测**：标题 white-space / text-overflow 样式断言
- **规则**：标题/标签类元素验证 overflow 截断属性

### 2026-05-24 — 弱断言

- **问题**：多个测试用 `expect(true).toBeTruthy()` 占位，不验证实际数据
- **修复**：替换为具体数据断言（mock 内容、排序前后值对比）
- **规则**：禁止 `expect(true).toBeTruthy()` 占位断言

---

## Mock 数据规范

```js
// ✅ 正确：真实中文数据
const MOCK = {
  previewName: '云汐',
  gender: '女',
  description: '青云门掌门之女，性格清冷',
}

// ❌ 错误：无意义的占位数据
const MOCK = {
  name: 'test',
  description: 'foo bar',
}
```

### Mock 格式必须与前端解析逻辑一致

**每次新增/修改 mock 数据前，必须 Read 前端解析代码确认字段名和数据结构。** 不匹配会导致功能跑通但数据不显示。

本案教训：mock 用 `issues: [{ category }]`，前端 `handleConsistencyResult` 期望 `character_issues: [{ type }]`（按类型分组），tab 过滤用 `i.type` 而非 `i.category`——字段名对不上，tab 切换无声失效。

```js
// ✅ 正确：snake_case 分组格式，字段名对齐前端代码
consistencyCheck: {
  data: {
    character_issues: [{ entity: '云汐', description: '...', type: 'character', severity: 'medium' }],
    timeline_issues: [...],
  }
}

// ❌ 错误：平铺 issues，用 category 而非 type
consistencyCheck: {
  data: {
    issues: [{ category: 'character', ... }]
  }
}
```

---

## 元素选择器导航规则

| 目标 | 正确选择器 | 错误选择器 |
|------|-----------|-----------|
| 进入章节写作页 | `a[href*="/write/"]` | `.node-title`（只在大纲树中选中节点，不跳转） |
| 点击导航按钮 | `.nav-btn:has-text("▸")` + 先 `isDisabled()` | 直接 `click()` |
| 检查弹窗样式 | `.inline-popup` + `evaluate(getComputedStyle)` | 只检查 `.isVisible()` |

---

---

## 假测试反模式（看起来在测，实际没测）

| 反模式 | 问题 | 正确写法 |
|--------|------|---------|
| `expect(typeof x).toBe('string')` | 不验证值，`null`/`undefined`/空串全通过 | `expect(x).toBe('云汐')` |
| `expect(hasX \|\| true).toBeTruthy()` | 永远 true，`hasX=false` 也通过 | 分别 `expect(hasX).toBe(true)` + `expect(hasY).toBe(true)` |
| `expect(bodyText).toBeTruthy()` | 空页面也有 `<body>` 文本 | 检查具体元素或内容：`expect(page.locator('h1')).toContainText('大纲')` |
| `page.waitForTimeout(500)` 后无断言 | 不验证任何行为完成 | `await expect(el).toBeVisible({ timeout: 5000 })` |
| mock 字段名和前端代码对不上 | test 通过但数据静默丢失 | 写 mock 前 Read 前端解析函数，确认字段名 |
| `.node-title` 点击进写作页 | 不导航，只在树中选中 | `a[href*="/write/"]` 点击 |
| `.nav-btn` 直接 `click()` | 不检查 disabled | 先 `isDisabled()` 再 `click()` |

---

## 测试文件组织

| 规则 | 说明 |
|------|------|
| 新功能 → 新 spec | 独立功能模块（如一致性检查）新建 `xxx.spec.js` |
| 已有功能追加 | 在已有 spec 文件末尾加 test（如 outline 导航/截断） |
| 跨页面流程 | 放最相关的功能文件 |
| mock/setup 变化 | 先看 `mock-setup.js` 和 `ai-responses.js`，改全局 mock 还是 test 内 `page.route()` 覆盖 |
| 文件上限 | 单文件超 10 条 test 考虑拆分 |

---

## 写完测试前自查清单

1. Mock 字段名和数据结构**已读前端代码确认**（不是猜的）
2. 选择器用 `has-text` 或 `href` 匹配，**不用 CSS class 猜测导航行为**
3. 有弹窗 → 有 max-height computed style 断言
4. 有排序/筛选 → 排序前后值对比（不是 `typeof x === 'string'`）
5. 有多步流程 → 每一步的状态变化都有断言
6. 无 `expect(true).toBeTruthy()` 占位

---

## 新增测试文件模板

```js
import { test, expect } from '@playwright/test'
import { setupAllMocks } from '../utils/mock-setup.js'

test.describe('功能名', () => {
  test.beforeEach(async ({ page }) => {
    await setupAllMocks(page)
    await page.goto('/目标路径')
    await page.waitForTimeout(500)
  })

  test('核心流程：操作A → 状态B → 结果C', async ({ page }) => {
    // 1. 初始状态断言
    // 2. 触发操作
    // 3. 中间状态断言
    // 4. 结束状态断言
  })
})
```

---

## Playwright 调试速查

```bash
npx playwright test consistency.spec.js    # 单个文件
npx playwright test -g "dismiss all"       # 单个测试
npx playwright test --ui                   # UI 模式（最直观）
npx playwright test --headed               # 有头浏览器
npx playwright test --headed --slowmo=500  # 慢放
npx playwright test --last-failed          # 只跑失败的
npx playwright show-report                 # 查看报告
```

**失败排查顺序**：`test-results/` 截图 → `npx playwright show-report` → `error-context.md`

---

## 常用断言速查

```js
await expect(el).toBeVisible({ timeout: 5000 })
await expect(el).toContainText('云汐')
await expect(el).toHaveText('精确匹配')
await expect(page).toHaveURL(/\/write\//)
await expect(input).toHaveValue('云汐')
expect(await page.locator('.card').count()).toBeGreaterThan(0)
const maxH = await el.evaluate(e => getComputedStyle(e).maxHeight)
expect(await btn.isDisabled()).toBe(true)
if (!(await el.isVisible())) return // 元素不存在则跳过断言
```

---

## 测试覆盖地图

> 每次新增功能后更新此表，防止出现测试盲区。

| 模块 | 基本渲染 | CRUD | 排序/筛选 | AI 交互 | CSS 约束 | 错误状态 |
|------|---------|------|----------|---------|---------|---------|
| 书籍管理 | ✅ | ✅ | — | — | — | — |
| 角色管理 | ✅ | ✅ | ✅ | ✅ | — | — |
| 大纲编排 | ✅ | ✅ | ✅ | — | ✅ | — |
| 章节写作 | ✅ | — | — | ✅ | ✅ | — |
| 模块 Hub | ✅ | — | ✅ | — | — | — |
| 世界观 | ✅ | ✅ | — | ✅ | — | — |
| 伏笔 | ✅ | — | — | — | — | — |
| 用户中心 | ✅ | — | — | — | — | ✅ |
| 一致性检查 | ✅ | — | — | ✅ | ✅ | — |
| 登录/注册 | ✅ | — | — | — | — | ✅ |

**盲区**：章节写作缺 CRUD（保存/加载/定稿流程）、角色管理缺 CSS 约束测试、大纲编排缺 AI 交互测试。

---

## 测试标签（用于 test.describe 第一行注释）

```js
// @smoke — 核心流程，每次提交前必跑
// @ai   — 触发 AI mock，适合开发时跑
// @slow — 超 5s 的测试，CI 可选跑
```

运行方式：
```bash
npx playwright test -g "@smoke"
```

---

## 测试环境速查

```bash
# 前提：Docker PostgreSQL 已运行（端口 5433）

# 1. 后端（需 local profile）
cd backend/youmo-api
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 2. 前端 dev server（E2E 依赖，端口 5173）
cd frontend && npm run dev

# 3. 运行测试
cd frontend && npx playwright test

# 4. 只跑核心流程（smoke）
npx playwright test -g "@smoke"
```

**常见失败 + 快速修复：**

| 症状 | 可能原因 | 修复 |
|------|---------|------|
| `backend_down` / 401 | 后端未启动或启动 profiles 不对 | `mvn spring-boot:run -pl backend/youmo-api -Dspring-boot.run.profiles=local` |
| `frontend_down` / 000 | dev server 没跑 | `cd frontend && npm run dev` |
| `SCRAM authentication` | PG 密码端口不对 | 确认用 `local` profile（端口 5433，密码 root） |
| `storageState` 失效 | global-setup 失败 | 手动跑一次 `node e2e/global-setup.js` |
| mock 不生效 | `setupAllMocks` 未调用 | `beforeEach` 加 `await setupAllMocks(page)` |
| AI 测试超时 | mock-rewrite SSE 耗时过长 | 检查 `chunkDelay` 默认值 40ms 是否合理 |

---

## 版本记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-05-28 | v1.0 | 初始版本：核心原则 + 5 条漏测案例 + CSS 断言清单 + Mock 规范 |
| 2026-05-28 | v1.1 | 新增：Mock 格式验证规则 + 元素选择器导航规则 + 写测试前自查清单 |
| 2026-05-28 | v1.2 | 新增：假测试反模式表 + 测试文件组织规则 |
| 2026-05-28 | v1.3 | 新增：Playwright 调试速查 + 常用断言速查 |
| 2026-05-28 | v1.6 | 审计 17 个测试文件，修复 8 个文件 18 处反模式，99/99 E2E 全过 |
| 2026-05-28 | v2.0 | 🚨 新增改代码前必查 8 条清单 — 直接防止同类 bug 漏过 |
| 2026-05-28 | v2.1 | 新增 nav 箭头漏测案例：Vue route params 用 const 导致导航失效 + 第 9 条必查项 |