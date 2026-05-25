# Tab 协作同步

> 两个 Claude Code tab 并行时，每个 tab 开始工作前**必须先读此文件**。
> 完成后更新自己的状态行。不要改其他 tab 锁定的文件。

---

## 活动 Tab

| Tab | 角色 | 当前任务 | 锁定文件/目录 | 状态 |
|-----|------|---------|-------------|:--:|
| A | 分析/全栈 | P1 完成 | — | 🟢 空闲 |
| B | 工程实现 | — | — | 🟢 空闲 |

## 并行规则

1. **以文件为单位隔离** — 不同 tab 不碰同一个文件
2. **先读后做** — 每次接任务前读此文件，确认无冲突
3. **锁定声明** — 开始改文件前，先更新上表"锁定文件"列
4. **做完释放** — 改完更新"锁定文件"为 `—`，在日志区记一笔
5. **CLAUDE.md 写者优先** — 谁先声明谁写，另一个 defer

## P0 总览（全部完成 ✅）

| 序号 | 任务 | 状态 |
|------|------|:--:|
| 1 | SSE 流式续写 | ✅ |
| 2 | 提示词组装器 | ✅ |
| 3 | 编辑器体验（暗色/专注/打字机） | ✅ |
| 4 | 大纲点击跳转正文 | ✅ |
| 5 | OutlineEditor 交互优化（折叠/按钮/随机大纲） | ✅ |
| 6 | 随机生成流程统一（status check + preview + draft） | ✅ |
| 7 | 后端 prompt 修复（总纲、题材规则） | ✅ |

## P1 候选（全部完成 ✅）

| 序号 | 任务 | 竞品对标 |
|------|------|------|
| 1 | RAG 世界观检索（续写时注入设定） | Sudowrite lorebook | ✅ |
| 2 | 负向约束管理面板（用户自定义禁用词） | 无 | ✅ |
| 3 | 大纲思维导图视图 | 墨者 | ✅ |
| 4 | 角色关系图可视化 | Sudowrite | ✅ |
| 5 | 用户认证登录 | — | ✅ |
| 6 | 版本历史管理 | — | ✅ |

---

## 日志

### 2026-05-24
- 创建此文件，从 CLAUDE.md 拆出 Tab 协调信息
- **代码保护体系建立**：prompt 外部化（PromptConfig + youmo-prompts/）+ 脱敏（env vars）+ .gitignore + git-guard.md
- **按需加载规则**：CLAUDE.md 精简，git 操作读 git-guard.md，Tab 协作读 SYNC.md
- **4 commits 已推送**：后端 + 前端 + 文档 + 负向约束管理
- **P0 全部完成**：SSE 续写、提示词组装、编辑器体验、大纲优化、随机生成流程、负向约束

### 2026-05-24 (续)
- **P1-3 大纲思维导图视图完成**：MindMapView.vue（水平三列布局：卷→章→节，SVG 贝塞尔连接线）
- **P1-4 角色关系图可视化完成**：CharacterGraph.vue（圆形布局，性别着色，关系编辑在 CharacterList 滑出面板中）
- **P1-5 用户认证登录确认**：之前已完整实现（JWT + Spring Security + 前端登录/注册/路由守卫）
- **P1-6 版本历史管理优化完成**：V8 migration 创建 chapter_content_archive 归档表，save() 自动归档旧版本到冷表，getVersionHistory() 合并查询主表+归档表。冷热分离，主表只保留最新版本。
- **文档整理完成**：CLAUDE.md 更新（技术栈标注 MyBatis 用户主技 + JPA AI 实现）、进度确认清单重排、SYNC.md 标记全部完成
- **产品体验打磨**：LoadingSpinner 组件（6 页统一）、子页导航显示书名、页面过渡动画、响应式布局、表单错误态 CSS 变量
- **规划更新**：竞品对比文档更新评分（5.4→6.7）、THOUGHTS.md 收件箱迁移、Phase 2 具体任务规划

### 2026-05-24 (续2)
- **P1 RAG 完成**：ContextKeywordExtractor 分段关键词提取、PromptAssemblyService 相关性匹配 + token 预算控制、前端 AI 注入设置 UI（精简/标准/详细）
- **草稿箱统一**：DraftsDrawer.vue 可复用组件，CharacterList/WorldSetting/OutlineEditor 三个页面均加入草稿箱入口
- **Bug 修复**：CharacterList "应用"按钮（缺少 relationships: []）、端口 5173 冲突、V9 migration 补 BaseEntity 列
- **环境配置**：application-local.yml（gitignored）存储本地密钥，Spring profiles 管理
- **枚举本地化**：CharacterMode/CreationMode/LengthType 增加中文标签 + JsonCreator

### 2026-05-25 — Phase 2 启动

- **轻量一致性检查**：续写完成后自动用 DeepSeek 提取实体交叉比对前文，矛盾标黄提示
- **温度参数前端可调**：ChapterWrite 工具栏 🔥 滑块 0.3-2.0，默认 1.2
- **AI 中止优化**：AbortController 替代 window.location.reload()，停止生成不刷新页面
- **章节快速导航**：上一章/下一章按钮 + Ctrl+←→ 快捷键，基于大纲树计算兄弟顺序
- **大纲拖拽排序**：已有完整 HTML5 原生拖拽实现（确认无需新增）
- **文档更新**：竞品对比同步 Phase 2 进度，SYNC.md 日志更新
- **6 commits 已推送**：P1 收尾 + Phase 2 第一二批

### 2026-05-25 (续) — Phase 2 完成

- **智能改写完善**：前端 ChapterWrite 模式切换（续写/润色/扩写/缩写），后端 GenerationController 已有三种 rewrite prompt，确认打通
- **版本对比（双栏并排）**：ChapterWrite 版本面板加 compareA/compareB 选择 + computeDiff 行级 diff，DiffViewer 内联
- **流式中断恢复**：V10 migration 加 stream_buffer 列，后端每 200 字持久化 buffer，前端 onMounted 检测恢复/丢弃
- **E2E 冒烟测试**：SmokeTest 3 条用例（中文关键词提取、空输入、去重排序）全部通过
- **1 commit 已提交**：Phase 2 第三四批（11 files, +310/-15）
- Phase 2 全部 9 项任务完成
