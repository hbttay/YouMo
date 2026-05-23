# 余墨（YouMo）

> AI 辅助长篇小说创作系统 — 从灵感到大纲，从人设到正文，一站式创作工具。

**作者**：hbttay

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.4 + Java 17 |
| ORM / SQL | Spring Data JPA + MyBatis |
| 数据库 | PostgreSQL 16 |
| 迁移工具 | Flyway |
| 前端框架 | Vue 3 |
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
└── docs/                 ← 开发文档
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
mvn compile -f pom.xml
mvn spring-boot:run -pl backend/youmo-api
# → http://localhost:8080
```

## License

MIT
