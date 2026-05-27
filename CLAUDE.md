# 余墨 (YouMo) — AI 辅助小说创作系统

## 🤖 当前会话 (2026-05-24，Tab A - 分析/全栈)

**P0 + P1 全部完成 ✅**

**Tab 协作：** 每次会话启动时，第一个动作必须是 Read `SYNC.md`，了解锁定的文件、任务分工和并行规则。不读不准动手。

## 项目速览

- **个人全栈项目**，目标：求职展示 + 深度学习
- 商业化意向，GitHub 公开仓库。核心算法 Phase 2-3 抽私有仓库，目前骨架阶段全公开
- 技术决策要解释 WHY（面试用）

## 当前阶段：Phase 1 + P1 全部完成 → Phase 2 待启动

**Phase 1 已完成：**
- 后端 11 张表链路：Entity → Repository → Service → Controller
- 前端 Vue 3 单页：书列表、创建书、书详情、角色管理（含关系图）、大纲编排（含思维导图）、世界观设定、写作编辑器（含版本历史）、用户认证
- 全部 CRUD + 认证登录（JWT + Spring Security）
- SSE 流式 AI 续写 + Prompt 组装器 + 负向约束管理

**P1 已全部完成：** RAG 世界观检索、负向约束面板、大纲思维导图、角色关系图、认证登录、版本历史归档

**Phase 2 待做：** 编辑器体验深化、AI 续写优化

## 技术栈

| 层 | 技术 | 备注 |
|------|------|------|
| 前端 | Vue 3 + Vite + Vue Router + Pinia + Axios | AI 辅助实现 |
| 后端 | Spring Boot + MyBatis（用户主技） + JPA/Hibernate（AI 实现） | 3.4.1 |
| 数据库 | PostgreSQL（AI 辅助） | 16 |
| 迁移 | Flyway（AI 辅助） | 10.22 |
| 构建 | Maven 多模块 | Java 17 |

## Maven 模块结构

```
youmo-parent (pom.xml 根)
├── backend/youmo-common   — Entity、枚举、BaseEntity、ApiResponse
├── backend/youmo-core     — Repository、Service
├── backend/youmo-api      — Controller、DTO、JacksonConfig
└── backend/youmo-generation — AI 服务（未启用）
```

## 快速命令

### 构建

```bash
# 构建全部后端（必须先 install，否则 spring-boot:run -pl 找不到兄弟模块）
mvn install -DskipTests

# 构建前端
cd frontend && npm run build
```

### 启动

```bash
# 启动后端 (端口 8080)
mvn spring-boot:run -pl backend/youmo-api

# 启动前端 (端口 5173，自动 proxy /api → 8080)
cd frontend && npm run dev
```

### 自动化等待时间（基于 Ryzen 7 5700X + 32GB + SSD）

| 操作 | 实际耗时 | 预算上限 | 说明 |
|------|---------|---------|------|
| Maven install (无测试) | ~15-25s | 30s | 改代码后需重构建 |
| Spring Boot 启动 | ~10-15s | 20s | 健康检查用 `curl localhost:8080` |
| Vite 构建 | ~500ms | 5s | 前端修改后验证编译 |
| Vite dev 启动 | ~2s | 5s | 几乎瞬间 |
| DB 操作 | <1s | 3s | 本地 Docker PG |
| DeepSeek API | 不定 | 90s | 一致性检查需 5 路并行 |

**规则**：
- `sleep` 不要超过实际所需，后端启动等 12s 即可，不要每步都 sleep 10
- Maven 构建指令加 `-q` 减少无关输出
- 并发执行无依赖的命令（如同时检查后端 + 前端状态）

### 测试

```bash
bash backend/test_api.sh
```

### 故障排查

```bash
# Git Bash 中文乱码 → 先切 UTF-8 代码页
/c/Windows/System32/chcp.com 65001 2>/dev/null

# 杀残留 Java 进程
/c/Windows/System32/taskkill.exe //F //IM java.exe
```

## 关键约定 (新代码必须遵守)

### JSON 字段命名 — snake_case
- **Jackson 全局配置 `PropertyNamingStrategies.SNAKE_CASE`**，Java 多单词字段自动转 snake_case
- 前端表单字段必须用 snake_case：`age_description`、`node_type`、`parent_id`、`core_rule_type`
- DTO 不需要 `@JsonProperty`，除非字段名和 JSON 名不同
- **这是历史 bug 根源，Phase 0-1 反复踩坑**

### 前端状态管理
- `useRequest(fn)` composable 统一处理 loading/error（`@/composables/useRequest.js`）
- 不同 API 函数用独立实例：`const { execute: createExec } = useRequest(createCharacter)`
- 不能多个操作共用同一个 execute（会调错 API）

### 后端分层
- Controller 只做参数校验 + 调用 Service，不写业务逻辑
- Entity 不直接暴露给前端，用 DTO（`request/` + `response/`）
- Service 接口 + Impl 分离（面试考点：面向接口编程）

### 前端 UI 一致性（新页面必读）
- **写任何新页面之前，先读完已有的同类型页面**，确认现用设计规范再动笔
- 品牌色统一 `#5b3cc4`（hover `#4a2fa8`），返回链接必须用品牌色，禁用灰色 `#666`
- 同类型交互必须一致：确认弹窗用 `ModalConfirm.vue`、编辑用滑出面板（角色）/ 行内编辑（大纲）
- 写作者术语：卷/章/节 用 `添加章` 不用 `子节点`，角色用 `男/女` 不用 `Male/Female`
- 文本框 rows 默认 4，三行内容不应出现滚动条

## 按需加载规则

CLAUDE.md 保持精简。以下内容**只在相关操作时才读**：
- **Git 提交/推送** → 先读 `.claude/git-guard.md`（保护清单 + 检查流程）
- **Tab 协作** → 读 `SYNC.md`
- **想法/灵感** → 读 `THOUGHTS.md`

高频文件（每次会话必读）：本文件 + `~/.claude/CLAUDE.md`。其他按需。
- **Git 推送节奏**：每个 Phase 收尾时统一推送一次，不要频繁 push。中间 commit 只留在本地。

### 方案锁定（防手贱）
- **用户已确认的技术方案，后续追问不等于推翻。** 用户问"XX有什么影响""XX可不可以升级"只是在了解情况，不是在改变决策。除非用户明确说"换""改""用X方案"，否则继续执行原方案。

## 已知问题

| 问题 | 状态 | 处理 |
|------|------|------|
| Git Bash 中文乱码 | 已知 | `chcp 65001` 先切 UTF-8 代码页 |
| Flyway checksum 不匹配 | 已规避 | `validate-on-migrate: false`（V1 结构未变） |
| `mvn spring-boot:run -pl` 找不到模块 | 已知 | 先 `mvn install -DskipTests` |
| Git Bash curl 传中文 JSON 乱码 | 已知 | 写入文件后用 `-d @file` 传 |

## DeepSeek API 备忘（2026-05-22 调通）

| 项目 | 说明 |
|------|------|
| Key | 有官方 key，测试用 |
| 非流式 | 偶尔 401 `Authentication Fails (governor)`，重试可恢复 |
| 流式 SSE | 稳定可用，Phase 2 前端用这个 |
| 模型 | `deepseek-chat` |
| 中文续写 | 文风准确，修仙/玄幻风格到位 |
| 测试脚本 | `scripts/test_deepseek.py` |
| 成本参考 | 输入 ¥1/M tok，输出 ¥2/M tok（官方价），第三方更低 |

## 降 AI 味策略（Phase 2 持续优化）

AI 续写常见问题：高频套话（"一股...气息"、"心头一震"）、结尾过于工整、节奏缺乏变化。
辅助作家而非替代作家，目标是"给出可用的初稿让作者改"。

| 策略 | 说明 | Phase |
|------|------|-------|
| 提高 temperature | 0.8-1.2（实验验证：0.3平淡/0.8跳脱/1.2最佳） | P2 |
| 负向约束 | prompt 列禁用词清单（"心头一震""一股"等），有效减少套路 | P2 |
| 作者风格样本 | 前文片段作 few-shot，AI 模仿作者节奏（单段样本不够，需多段） | P2 |
| 动作 > 心理 | prompt 约束"多写动作和对话，少写内心独白" | P2 |
| 刻意不结尾 | prompt 要求"在中间截断，不写收束句" | P2 |
| 作者审改面板 | 前端 hover 显示 AI 建议，作者点击采纳/拒绝 | P3 |

## 文档索引

### 项目设计
- `docs/设计文档/1.技术选型方案.md`
- `docs/设计文档/2.余墨项目商业需求文档（BRD）.md`
- `docs/设计文档/3.余墨产品需求文档（PRD）.md`
- `docs/设计文档/4.数据模型设计文档（v1.4）.md`
- `docs/设计文档/5.余墨进度确认清单.md`

### 面试准备
- `docs/知识库/` — 模块化知识库：01-RAG → 02-Prompt → 03-LLM → 04-Agent → 05-定位 → 06-话术 → 07-技术栈（每个知识点一个文件，按需加载）
- `docs/面试知识体系.md` — 合并版参考（求职核心参考）
- `scripts/gen_resume.py` — 简历生成脚本（唯一源，桌面那份是副本，不改），输出双份：项目根 + 桌面

### 会话协作
- `THOUGHTS.md` — 想法捕获（收件箱 → 准备做 → 先放着 → 已完成）
- 个人知识库 → 全局 `~/.claude/CLAUDE.md`（跨项目共享）
- 项目规范 → 本文件 `CLAUDE.md`（仅项目相关）

## 日志与留痕

- 按 Phase 拆分：`docs/logs/Phase_N_日志.md`
- 会话结束追加 `## Session #N` 节（完成事项、错误修复、架构调整、遗留问题）
- 写入方式：先 Write 到 `docs/logs/_new_entry.md`，再 Bash `cat` 拼接，删临时文件（避免 Read 旧日志消耗 token）

## Python 环境（OCR / 脚本）

| 项目 | 说明 |
|------|------|
| Python 3.7 | `C:\Users\HBTTAY\AppData\Local\Programs\Python\Python37\python.exe`（系统原有，不动） |
| Python 3.12 | `C:\Users\HBTTAY\AppData\Local\Programs\Python\Python312\python.exe`（OCR 用） |
| pip 镜像 | 清华源 `https://pypi.tuna.tsinghua.edu.cn/simple`（SSL 问题用镜像） |
| EasyOCR | Python 3.12 安装，中文 `ch_sim` + 英文 `en`，**gpu=False**（用 CPU，省电） |
| PyMuPDF | Python 3.12 安装（fitz） |
| OCR 脚本 | `scripts/pdf_ocr.py`，用法：`python scripts/pdf_ocr.py <PDF路径> [输出路径]` |
| 功率限制 | OCR 逐页 + 间隔 3s 散热，每 3 页歇 10s。DPI=250（保证识别率，不降画质）。Ryzen 7 5700X 满载易过热关机 |

```bash
# 用 Python 3.12 跑 OCR
C:\Users\HBTTAY\AppData\Local\Programs\Python\Python312\python.exe scripts/pdf_ocr.py "白皮书.pdf"
```

## 想法管理

- 所有新想法/功能/优化方向 → 实时写入 `THOUGHTS.md` 收件箱（30 秒内，不加判断）
- 每天结束时从收件箱挑 1-2 个移到"准备做"
- 不要靠脑子记想法——天机 + 截空的组合必丢
- Claude Code 在讨论中产生的 idea 也主动往里写
