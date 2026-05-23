# 余墨（YouMo）

> AI 辅助长篇小说创作系统 — 从灵感到大纲，从人设到正文，一站式创作工具。

**作者**：hbttay | **阶段**：Phase 1 完成 → Phase 2 进行中

## 已实现功能

- 作品管理（创建/编辑/删除）+ AI 随机生成书名简介
- 角色管理系统（结构化角色卡 + 随机生成）
- 大纲编排（卷/章/节树形结构 + 拖拽排序 + 随机生成）
- 世界观设定（9 大维度 + 自定义字段）
- AI 续写（SSE 流式输出 + 提示词组装 + 角色/世界观注入）
- AI 改写（润色/扩写/缩写）
- 编辑器（暗色模式/专注模式/打字机滚动/版本历史）
- 负向约束（AI 禁用词自定义）
- 章节导出 Markdown
- 用户认证（注册/登录/JWT）

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.4 + Java 17 |
| ORM | Spring Data JPA |
| 数据库 | PostgreSQL 16 |
| 迁移工具 | Flyway |
| 前端框架 | Vue 3 + Vite |
| 状态管理 | Pinia |
| 构建工具 | Maven 多模块 |

## 项目结构

```
YouMo/
├── backend/
│   ├── youmo-common/     ← Entity · 枚举 · 基础类
│   ├── youmo-core/       ← Repository · Service · 业务逻辑
│   ├── youmo-api/        ← Controller · DTO · 配置 · 启动入口
│   └── youmo-generation/ ← AI 生成服务
├── frontend/             ← Vue 3 前端
└── docs/                 ← 设计文档（不公开）
```

## 架构

```
Controller → Service → Repository → Entity
   HTTP       业务逻辑     数据访问     数据模型
```

- DTO 隔离 Entity 与前端，不直接暴露数据模型
- 统一响应格式 `{ code, message, data }`
- 全局异常拦截，业务异常统一处理

## 本地运行

```bash
# 需要 JDK 17+ / Maven 3.8+ / PostgreSQL 16+

createdb youmo_dev
mvn install -DskipTests
mvn spring-boot:run -pl backend/youmo-api
# → http://localhost:8080

cd frontend && npm run dev
# → http://localhost:5173
```

## License

MIT
