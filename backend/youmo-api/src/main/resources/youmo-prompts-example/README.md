# Prompt 配置说明

此目录对应 `backend/youmo-api/src/main/resources/youmo-prompts/`（在 .gitignore 中，不上传公开仓库）。

## 文件格式

每个 `.txt` 文件包含一个完整的 system prompt，由 `PromptConfig` 在启动时从 classpath 加载。

## 文件列表

| 文件 | 用途 |
|------|------|
| `continue.txt` | SSE 流式续写的 system prompt |
| `polish.txt` | 润色改写 prompt |
| `expand.txt` | 扩写 prompt |
| `summarize.txt` | 缩写 prompt |
| `assemble.txt` | PromptAssemblyService 的基础 prompt（注入角色/世界观前的模板） |
| `random-book-idea.txt` | 随机书名/创意 prompt |
| `random-character.txt` | 随机角色 prompt |
| `random-world.txt` | 随机世界观 prompt |
| `random-outline.txt` | 随机大纲 prompt |

## 使用方式

1. 复制 `youmo-prompts-example/` 中的示例文件到 `youmo-prompts/`
2. 编辑 `.txt` 文件，填入实际调优后的 prompt
3. 重启后端，`PromptConfig` 自动加载

## 公开仓库只有 fallback

公开 clone 的仓库没有 `youmo-prompts/` 目录，系统使用 Java 代码中的 fallback 默认值（基础功能可用，但缺少精细调优）。
