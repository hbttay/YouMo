import zipfile, os, shutil

project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
outputs = [
    os.path.join(project_root, '许炜_简历_AI应用开发.docx'),
    r'C:\Users\HBTTAY\Desktop\找工作\许炜_简历_AI应用开发.docx',
]

tmp = os.path.join(project_root, '_resume_tmp')
if os.path.exists(tmp):
    shutil.rmtree(tmp)
os.makedirs(os.path.join(tmp, '_rels'))
os.makedirs(os.path.join(tmp, 'word'))

DARK = '1a1a1a'
GRAY = '555555'
LIGHT = '888888'
BRAND = '5b3cc4'
FONT = '微软雅黑'

def st(text, size=21, bold=False, color=DARK):
    b = '<w:b/>' if bold else ''
    return f'<w:r><w:rPr><w:rFonts w:ascii="{FONT}" w:hAnsi="{FONT}" w:eastAsia="{FONT}" w:cs="{FONT}"/>{b}<w:sz w:val="{size}"/><w:color w:val="{color}"/></w:rPr><w:t>{text}</w:t></w:r>'

def section_header(title):
    return (
        f'<w:p><w:pPr><w:spacing w:before="100" w:after="20" w:line="320" w:lineRule="auto"/>'
        f'<w:pBdr><w:bottom w:val="single" w:sz="4" w:space="3" w:color="{BRAND}"/></w:pBdr></w:pPr>'
        f'{st(title, 24, bold=True, color=DARK)}'
        f'</w:p>'
    )

def bullet(text, size=20, color=DARK):
    return (
        f'<w:p><w:pPr><w:ind w:left="300" w:hanging="180"/><w:spacing w:before="0" w:after="10" w:line="300" w:lineRule="auto"/></w:pPr>'
        f'{st("•", size, bold=True, color=BRAND)}'
        f'<w:r><w:rPr><w:sz w:val="{size}"/><w:color w:val="{color}"/></w:rPr><w:t xml:space="preserve">  {text}</w:t></w:r>'
        f'</w:p>'
    )

doc = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
doc += '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><w:body>'

# ===== HEADER — 3 lines, education integrated =====
doc += (
    f'<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:before="0" w:after="0" w:line="360" w:lineRule="auto"/></w:pPr>'
    f'{st("许 炜", 36, bold=True)}'
    f'{st("    ", 16)}'
    f'{st("AI 应用开发 / Java 后端", 24, color=GRAY)}'
    f'</w:p>'
    f'<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:before="0" w:after="0" w:line="300" w:lineRule="auto"/></w:pPr>'
    f'{st("广州航海学院 · 计算机科学与技术 · 本科 · 2024 届", 20, color=GRAY)}'
    f'{st("  |  ", 20, color=GRAY)}'
    f'{st("优秀毕业生 · 挑战杯二等奖 · 互联网+铜奖 · 英语四级", 20, color=GRAY)}'
    f'</w:p>'
    f'<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:before="0" w:after="0" w:line="300" w:lineRule="auto"/></w:pPr>'
    f'{st("15915905527  |  211124010@qq.com  |  广州", 20)}'
    f'</w:p>'
)

doc += (
    f'<w:p><w:pPr><w:spacing w:before="10" w:after="10" w:line="300" w:lineRule="auto"/>'
    f'<w:pBdr><w:bottom w:val="single" w:sz="4" w:space="1" w:color="cccccc"/></w:pBdr></w:pPr></w:p>'
)

# ===== SKILLS =====
doc += section_header("技术能力")
for txt in [
    "后端：Java（Spring Boot、MyBatis、Maven 多模块）、RESTful API、JWT + Spring Security 认证",
    "数据库：PostgreSQL（含 pgvector 向量扩展 + HNSW 索引）、MySQL，具备索引设计与慢查询优化意识",
    "AI 工程：RAG 混合检索（关键词匹配 + pgvector 语义检索）、Prompt Engineering（六层动态组装、Temperature 调优、负向约束）、DeepSeek API 集成（SSE 流式、Plan-then-Execute）",
    "工具链：Claude Code（主力 AI 开发工具）、Git、Postman、Linux 基础命令，Vue 3 可读可改",
]:
    doc += bullet(txt)

# ===== PROJECT =====
doc += section_header("项目经验")

doc += (
    f'<w:p><w:pPr><w:spacing w:before="40" w:after="0" w:line="300" w:lineRule="auto"/></w:pPr>'
    f'{st("余墨 — AI 辅助小说创作系统", 22, bold=True)}'
    f'{st("  |  独立全栈开发  |  2026.05", 20, color=LIGHT)}'
    f'</w:p>'
    f'<w:p><w:pPr><w:spacing w:before="0" w:after="0" w:line="300" w:lineRule="auto"/></w:pPr>'
    f'{st("Spring Boot + MyBatis + Vue 3 + PostgreSQL + DeepSeek API", 20, color=LIGHT)}'
    f'</w:p>'
    f'<w:p><w:pPr><w:spacing w:before="0" w:after="15" w:line="300" w:lineRule="auto"/></w:pPr>'
    f'{st("GitHub: github.com/hbttay/YouMo", 18, color=GRAY)}'
    f'</w:p>'
)
for txt in [
    "系统架构：Maven 四模块分层（common/core/api/generation），14 张表数据模型，设计原则包括时间版本化（所有属性带生效/失效章节）、角色 L0-L3 分级注入控制上下文长度、大字段分离 + 冷热归档优化查询、JSONB 开放扩展无需改表即可扩展自定义属性",
    "RAG 混合检索：双线并行——关键词线（从前文提取关键词 → 与世界观设定字段相关性打分 → 按维度权重排序） + 向量线（章节摘要 Embedding 1536 维 → pgvector + HNSW 索引余弦检索历史章节），两条线结果合并注入 System Prompt。全程自主编码未用 LangChain。生成过程每 200 字异步写库做断线恢复，线程池隔离不阻塞主流程",
    "Prompt 组装器：六层动态组装——作品层 → 角色卡（genre detection 自动识别 7 种小说类型匹配人格维度）→ 世界观动态检索（关键词打分 × 维度权重 × 三级注入控制）→ pgvector 语义前文召回 → 作者风格 → 负向约束词库 → 字符预算控制",
    "生成模式双轨：直接流式续写（SSE） + Plan-then-Execute（AI 先生成结构化写作计划→用户审批→再流式执行），解决纯流式生成不可控问题。支持润色/扩写/缩写/纠错四种内联 AI 编辑，选中文本弹窗 diff 对比后采纳或拒绝",
    "五层质量控制链：用户设定 → 系统约束（Prompt 组装器 + 上下文管理）→ AI 执行 → 系统校验（5 维度并发一致性检查：角色/时间线/世界观/伏笔/文风）→ 用户审查（版本对比/回滚），AI 永远在约束框架内执行，最终控制权归用户",
    "AI-Native 研发：核心模块（RAG Pipeline、Prompt 组装器、JWT 认证、一致性检查）100% 自主编码，CRUD 与前端由 Claude Code 辅助生成后逐一 Review，2 周全栈交付",
]:
    doc += bullet(txt)

# ===== WORK =====
doc += section_header("工作经历")

doc += (
    f'<w:p><w:pPr><w:spacing w:before="40" w:after="0" w:line="300" w:lineRule="auto"/></w:pPr>'
    f'{st("运营组长", 22, bold=True)}'
    f'{st("  |  广州兴雪电子商务有限公司  |  2024.12 - 2025.03", 20, color=LIGHT)}'
    f'</w:p>'
)
for txt in [
    "Python 开发：参与影音分析平台推流技术对接，研究推流算法参数调优，独立编写 Python 数据采集脚本实现直播流实时监控与异常告警",
    "团队管理：带领 5 人运营团队，从 0 搭建培训体系与标准化 SOP，设计数据驱动考核模型",
    "业务成果：主导美客多平台 0→1 启动，2 个月做到黄金等级，月销 3 万美金（日单量 200+，利润率 50%）",
]:
    doc += bullet(txt)

doc += (
    f'<w:p><w:pPr><w:spacing w:before="60" w:after="0" w:line="300" w:lineRule="auto"/></w:pPr>'
    f'{st("沃尔玛运营专员", 22, bold=True)}'
    f'{st("  |  广州徠徕科技有限公司  |  2024.06 - 2024.10", 20, color=LIGHT)}'
    f'</w:p>'
)
for txt in [
    "平台运营：负责沃尔玛美国站店铺日常运营，包括 Listing 优化、库存管理、广告投放与竞品监控，维持店铺评分稳定",
    "自动化脚本：独立编写 Python 脚本对接内部系统接口，实现订单与库存数据自动同步入库，替代每日手动 Excel 报表操作",
    "流程优化：梳理运营标准化流程，建立新品上架 SOP 与异常处理机制，减少重复性沟通成本",
]:
    doc += bullet(txt)

doc += '</w:body></w:document>'

# --- write files ---
with open(os.path.join(tmp, 'word', 'styles.xml'), 'w', encoding='utf-8') as f:
    f.write('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="微软雅黑" w:hAnsi="微软雅黑" w:eastAsia="微软雅黑" w:cs="微软雅黑"/><w:sz w:val="21"/></w:rPr></w:rPrDefault></w:docDefaults></w:styles>')

with open(os.path.join(tmp, 'word', 'document.xml'), 'w', encoding='utf-8') as f:
    f.write(doc)

with open(os.path.join(tmp, '[Content_Types].xml'), 'w', encoding='utf-8') as f:
    f.write('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>')

with open(os.path.join(tmp, '_rels', '.rels'), 'w', encoding='utf-8') as f:
    f.write('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')

for output in outputs:
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as zf:
        zf.write(os.path.join(tmp, '[Content_Types].xml'), '[Content_Types].xml')
        zf.write(os.path.join(tmp, '_rels', '.rels'), '_rels/.rels')
        zf.write(os.path.join(tmp, 'word', 'document.xml'), 'word/document.xml')
        zf.write(os.path.join(tmp, 'word', 'styles.xml'), 'word/styles.xml')

shutil.rmtree(tmp)
print(f'Done → {outputs[0]}')
