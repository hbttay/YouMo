# Git 提交保护

> 仓库公开用于面试展示。此文件在每次 git 提交/推送前读取。

## 被 .gitignore 保护（不会推送）

| 目录/文件 | 原因 |
|-----------|------|
| `youmo-prompts/` | AI prompt 工程（核心 IP） |
| `docs/` | 设计文档 BRD/PRD（商业机密） |
| `.claude/plans/` `.claude/projects/` `.claude/settings.local.json` | AI 会话记录 |
| `application-local.yml` `.env.local` | 本地敏感配置 |

## 已脱敏（会推送，但安全）

| 文件 | 处理方式 |
|------|---------|
| `application.yml` | 密钥改为 `${ENV_VAR}`，无默认值 |
| `application-local.yml.example` | 仅有字段名，无真实值 |
| `scripts/*.py` | API key 改为 `os.getenv("DEEPSEEK_API_KEY", "")` |
| `youmo-prompts-example/` | 仅说明格式，不含真实 prompt |
| `GenerationController.java` `RandomController.java` | 仅保留简化 fallback，真实 prompt 在 `.txt` 文件 |

## 提交前检查

```bash
# 1. 确认无敏感文件被追踪
git status

# 2. 确认无 API key 残留
grep -r "sk-" --include="*.java" --include="*.yml" --include="*.py" --include="*.json" . || echo "PASS"

# 3. 确认 application.yml 无硬编码密钥
grep -E "(password|secret|api-key):\s*[^${\s]" backend/youmo-api/src/main/resources/application.yml || echo "PASS"

# 4. 确认 youmo-prompts 目录被 gitignore
git check-ignore backend/youmo-api/src/main/resources/youmo-prompts/continue.txt || echo "FAIL: prompts not ignored!"
```
