-- ============================================================================
-- V1__init_schema.sql
-- 游墨（YouMo）MVP 核心表初始化
-- 版本：v1.4
-- 数据库：PostgreSQL 16
-- 创建顺序：users → book → world_setting → chapter_structure → character
--           → personality_version → character_detail → chapter_content
--           → chapter_summary → generation_log
-- ============================================================================

-- ============================================================================
-- 1. users — 用户账号
-- ============================================================================
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  DEFAULT NOW()
);

COMMENT ON TABLE  users                                  IS '用户账号';
COMMENT ON COLUMN users.email                            IS '邮箱（唯一）';
COMMENT ON COLUMN users.password_hash                    IS 'BCrypt 哈希';
COMMENT ON COLUMN users.status                           IS 'ACTIVE / FROZEN / DELETED';
COMMENT ON COLUMN users.created_at                       IS '创建时间';
COMMENT ON COLUMN users.updated_at                       IS '更新时间';

-- ============================================================================
-- 2. book — 书籍基本设定与元信息
-- ============================================================================
CREATE TABLE book (
    id                    BIGSERIAL    PRIMARY KEY,
    title                 VARCHAR(200) NOT NULL,
    theme                 TEXT,
    core_idea             TEXT,
    tone_labels           JSONB,
    one_sentence          TEXT,
    target_reader_profile VARCHAR(500),
    violence_level        SMALLINT     DEFAULT 3  CHECK (violence_level  >= 1 AND violence_level  <= 10),
    romance_level         SMALLINT     DEFAULT 1  CHECK (romance_level   >= 1 AND romance_level   <= 10),
    politics_level        SMALLINT     DEFAULT 1  CHECK (politics_level  >= 1 AND politics_level  <= 10),
    civility_level        SMALLINT     DEFAULT 5  CHECK (civility_level  >= 1 AND civility_level  <= 10),
    creation_mode         VARCHAR(20)  NOT NULL,
    character_mode        VARCHAR(20)  NOT NULL,
    length_type           VARCHAR(10)  NOT NULL,
    estimated_words       INT,
    is_public             BOOLEAN      DEFAULT FALSE,
    status                VARCHAR(20)  DEFAULT 'DRAFT',
    extra_attributes      JSONB,
    owner_id              BIGINT       NOT NULL,
    created_at            TIMESTAMPTZ  DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  DEFAULT NOW()
);

ALTER TABLE book
    ADD CONSTRAINT fk_book_owner FOREIGN KEY (owner_id) REFERENCES users (id);

CREATE INDEX idx_book_owner ON book (owner_id);

COMMENT ON TABLE  book                                    IS '书籍基本设定与元信息';
COMMENT ON COLUMN book.title                              IS '书名';
COMMENT ON COLUMN book.theme                              IS '主题陈述';
COMMENT ON COLUMN book.core_idea                          IS '核心理念';
COMMENT ON COLUMN book.tone_labels                        IS '基调标签数组（JSONB）';
COMMENT ON COLUMN book.one_sentence                       IS '一句话梗概';
COMMENT ON COLUMN book.target_reader_profile              IS '目标读者群体画像，自由描述';
COMMENT ON COLUMN book.violence_level                     IS '暴力程度（1=无，3=轻度，6=中度，10=重度）';
COMMENT ON COLUMN book.romance_level                      IS '情感描写程度（1=无，4=暧昧，7=露骨）';
COMMENT ON COLUMN book.politics_level                     IS '政治内容程度（1=不涉及，5=隐喻，10=直接）';
COMMENT ON COLUMN book.civility_level                     IS '文明用语气质（1=优雅，5=日常，10=恶劣）';
COMMENT ON COLUMN book.creation_mode                      IS 'LINEAR / DIVERGENT';
COMMENT ON COLUMN book.character_mode                     IS 'FIXED / INSPIRED';
COMMENT ON COLUMN book.length_type                        IS 'SHORT / MEDIUM / LONG';
COMMENT ON COLUMN book.estimated_words                    IS '预估字数';
COMMENT ON COLUMN book.is_public                          IS '是否公开分享';
COMMENT ON COLUMN book.status                             IS 'DRAFT / SERIALIZING / COMPLETED / ARCHIVED';
COMMENT ON COLUMN book.extra_attributes                   IS '扩展属性（自定义元数据）';
COMMENT ON COLUMN book.owner_id                           IS '所属用户（FK → users.id）';
COMMENT ON COLUMN book.created_at                         IS '创建时间';
COMMENT ON COLUMN book.updated_at                         IS '更新时间';

-- ============================================================================
-- 3. world_setting — 世界观总体框架
-- ============================================================================
CREATE TABLE world_setting (
    id                BIGSERIAL   PRIMARY KEY,
    book_id           BIGINT      NOT NULL UNIQUE,
    era               TEXT,
    geography         TEXT,
    history_events    JSONB,
    politics          TEXT,
    economy           TEXT,
    culture           TEXT,
    military          TEXT,
    core_rule_type    VARCHAR(50),
    core_rule_summary TEXT,
    extra_attributes  JSONB,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE world_setting
    ADD CONSTRAINT fk_world_setting_book FOREIGN KEY (book_id) REFERENCES book (id);

COMMENT ON TABLE  world_setting                            IS '世界观总体框架（一对一关联 book）';
COMMENT ON COLUMN world_setting.book_id                    IS '所属书（一对一，FK → book.id）';
COMMENT ON COLUMN world_setting.era                        IS '时代背景';
COMMENT ON COLUMN world_setting.geography                  IS '地理概况';
COMMENT ON COLUMN world_setting.history_events             IS '历史事件列表（JSONB）';
COMMENT ON COLUMN world_setting.politics                   IS '政治体制';
COMMENT ON COLUMN world_setting.economy                    IS '经济基础';
COMMENT ON COLUMN world_setting.culture                    IS '文化特征';
COMMENT ON COLUMN world_setting.military                   IS '军事格局';
COMMENT ON COLUMN world_setting.core_rule_type             IS '魔法/科技/异能/无/自定义';
COMMENT ON COLUMN world_setting.core_rule_summary          IS '核心规则简述';
COMMENT ON COLUMN world_setting.extra_attributes           IS '扩展属性';
COMMENT ON COLUMN world_setting.created_at                 IS '创建时间';
COMMENT ON COLUMN world_setting.updated_at                 IS '更新时间';

-- ============================================================================
-- 4. chapter_structure — 大纲树状结构
-- ============================================================================
CREATE TABLE chapter_structure (
    id                BIGSERIAL    PRIMARY KEY,
    book_id           BIGINT       NOT NULL,
    parent_id         BIGINT,
    node_type         VARCHAR(20)  NOT NULL,
    sequence          INT          NOT NULL,
    title             VARCHAR(200),
    writing_goal      TEXT,
    key_events        JSONB,
    status            VARCHAR(20)  DEFAULT 'DRAFT',
    is_important_plot BOOLEAN      DEFAULT FALSE,
    version           INT          DEFAULT 1,
    extra_attributes  JSONB,
    created_at        TIMESTAMPTZ  DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  DEFAULT NOW()
);

ALTER TABLE chapter_structure
    ADD CONSTRAINT fk_structure_book   FOREIGN KEY (book_id)   REFERENCES book (id),
    ADD CONSTRAINT fk_structure_parent FOREIGN KEY (parent_id) REFERENCES chapter_structure (id);

CREATE INDEX idx_structure_book   ON chapter_structure (book_id);
CREATE INDEX idx_structure_parent ON chapter_structure (parent_id);

COMMENT ON TABLE  chapter_structure                        IS '大纲树状结构';
COMMENT ON COLUMN chapter_structure.book_id                IS '所属书（FK → book.id）';
COMMENT ON COLUMN chapter_structure.parent_id              IS '父节点（自引用 FK，NULL=根节点）';
COMMENT ON COLUMN chapter_structure.node_type              IS 'VOLUME / CHAPTER / SCENE';
COMMENT ON COLUMN chapter_structure.sequence               IS '排序号';
COMMENT ON COLUMN chapter_structure.title                  IS '标题';
COMMENT ON COLUMN chapter_structure.writing_goal           IS '写作目标';
COMMENT ON COLUMN chapter_structure.key_events             IS '关键事件列表（JSONB）';
COMMENT ON COLUMN chapter_structure.status                 IS 'DRAFT / WRITING / REVISION / COMPLETED';
COMMENT ON COLUMN chapter_structure.is_important_plot      IS '是否重要剧情（永久保留）';
COMMENT ON COLUMN chapter_structure.version                IS '大纲版本号';
COMMENT ON COLUMN chapter_structure.extra_attributes       IS '扩展属性';
COMMENT ON COLUMN chapter_structure.created_at             IS '创建时间';
COMMENT ON COLUMN chapter_structure.updated_at             IS '更新时间';

-- ============================================================================
-- 5. character — 角色基础骨架
-- ============================================================================
-- 注：category_template_id 暂不加 FK 约束，category_template 表在后续迁移中创建
CREATE TABLE character (
    id                   BIGSERIAL    PRIMARY KEY,
    book_id              BIGINT       NOT NULL,
    name                 VARCHAR(100) NOT NULL,
    gender               VARCHAR(50),
    age_description      VARCHAR(100),
    appearance           TEXT,
    origin               VARCHAR(200),
    identity             VARCHAR(200),
    category_template_id BIGINT,
    depth_level          CHAR(2)      NOT NULL DEFAULT 'L1'
                                      CHECK (depth_level IN ('L0', 'L1', 'L2', 'L3')),
    is_auto_created      BOOLEAN      DEFAULT FALSE,
    is_archived          BOOLEAN      DEFAULT FALSE,
    appear_chapters      JSONB,
    extra_attributes     JSONB,
    created_at           TIMESTAMPTZ  DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  DEFAULT NOW()
);

ALTER TABLE character
    ADD CONSTRAINT fk_character_book FOREIGN KEY (book_id) REFERENCES book (id);

CREATE INDEX idx_character_book  ON character (book_id);
CREATE INDEX idx_character_depth ON character (depth_level);

COMMENT ON TABLE  character                                IS '角色基础骨架';
COMMENT ON COLUMN character.book_id                        IS '所属书（FK → book.id）';
COMMENT ON COLUMN character.name                           IS '姓名';
COMMENT ON COLUMN character.gender                         IS '自由文本（男/女/雌雄同体/无性别…）';
COMMENT ON COLUMN character.age_description                IS '年龄描述';
COMMENT ON COLUMN character.appearance                     IS '外貌特征';
COMMENT ON COLUMN character.origin                         IS '出身';
COMMENT ON COLUMN character.identity                       IS '身份/职业';
COMMENT ON COLUMN character.category_template_id           IS '所属类别（FK → category_template.id，后续迁移添加）';
COMMENT ON COLUMN character.depth_level                    IS 'L0 / L1 / L2 / L3';
COMMENT ON COLUMN character.is_auto_created                IS '是否系统自动创建';
COMMENT ON COLUMN character.is_archived                    IS '是否已归档';
COMMENT ON COLUMN character.appear_chapters                IS '出场章节列表（JSONB）';
COMMENT ON COLUMN character.extra_attributes               IS '扩展属性（种族特异特征、触发任务清单等）';
COMMENT ON COLUMN character.created_at                     IS '创建时间';
COMMENT ON COLUMN character.updated_at                     IS '更新时间';

-- ============================================================================
-- 6. personality_version — 角色性格版本
-- ============================================================================
CREATE TABLE personality_version (
    id                BIGSERIAL    PRIMARY KEY,
    character_id      BIGINT       NOT NULL,
    tags              JSONB        NOT NULL,
    enneagram         VARCHAR(50),
    archetype         VARCHAR(50),
    effective_chapter INT          NOT NULL,
    expire_chapter    INT,
    change_reason     VARCHAR(500),
    status            VARCHAR(20)  DEFAULT 'DRAFT',
    source            VARCHAR(20)  DEFAULT 'MANUAL',
    extra_attributes  JSONB,
    created_at        TIMESTAMPTZ  DEFAULT NOW()
);

ALTER TABLE personality_version
    ADD CONSTRAINT fk_personality_char FOREIGN KEY (character_id) REFERENCES character (id);

CREATE INDEX idx_personality_char      ON personality_version (character_id);
CREATE INDEX idx_personality_effective ON personality_version (effective_chapter, expire_chapter);

COMMENT ON TABLE  personality_version                      IS '角色性格版本（时间版本化）';
COMMENT ON COLUMN personality_version.character_id         IS '所属角色（FK → character.id）';
COMMENT ON COLUMN personality_version.tags                 IS '标签数组+强度（JSONB）';
COMMENT ON COLUMN personality_version.enneagram            IS '九型人格/复合侧翼（如 8w7, 4w5, 3/8）';
COMMENT ON COLUMN personality_version.archetype            IS '叙事原型';
COMMENT ON COLUMN personality_version.effective_chapter    IS '生效章节';
COMMENT ON COLUMN personality_version.expire_chapter       IS '失效章节（NULL=永远有效）';
COMMENT ON COLUMN personality_version.change_reason        IS '变化原因';
COMMENT ON COLUMN personality_version.status               IS 'DRAFT / APPLIED / DISCARDED';
COMMENT ON COLUMN personality_version.source               IS 'MANUAL / AI_SUGGESTED / ROLLBACK';
COMMENT ON COLUMN personality_version.extra_attributes     IS '扩展属性';
COMMENT ON COLUMN personality_version.created_at           IS '创建时间';

-- ============================================================================
-- 7. character_detail — 能力/动机/风格主表
-- ============================================================================
CREATE TABLE character_detail (
    id                 BIGSERIAL    PRIMARY KEY,
    character_id       BIGINT       NOT NULL,
    skill_tree         JSONB,
    talents            JSONB,
    weaknesses         JSONB,
    growth_curve       JSONB,
    core_desire        VARCHAR(500),
    surface_goal       VARCHAR(500),
    deep_fear          VARCHAR(500),
    bottom_line        VARCHAR(500),
    value_ranking      JSONB,
    talkativeness      VARCHAR(20),
    sentence_style     VARCHAR(20),
    word_preference    VARCHAR(20),
    emotion_expression VARCHAR(20),
    action_style       VARCHAR(20),
    effective_chapter  INT          NOT NULL,
    expire_chapter     INT,
    version_number     INT          DEFAULT 1,
    extra_attributes   JSONB,
    created_at         TIMESTAMPTZ  DEFAULT NOW()
);

ALTER TABLE character_detail
    ADD CONSTRAINT fk_detail_char FOREIGN KEY (character_id) REFERENCES character (id);

CREATE INDEX idx_detail_char ON character_detail (character_id);

COMMENT ON TABLE  character_detail                         IS '能力/动机/风格主表（时间版本化）';
COMMENT ON COLUMN character_detail.character_id            IS '所属角色（FK → character.id）';
COMMENT ON COLUMN character_detail.skill_tree              IS '技能树（JSONB）';
COMMENT ON COLUMN character_detail.talents                 IS '天赋列表（JSONB）';
COMMENT ON COLUMN character_detail.weaknesses              IS '弱点列表（JSONB）';
COMMENT ON COLUMN character_detail.growth_curve            IS '成长曲线数据（JSONB）';
COMMENT ON COLUMN character_detail.core_desire             IS '核心欲望';
COMMENT ON COLUMN character_detail.surface_goal            IS '表层目标';
COMMENT ON COLUMN character_detail.deep_fear               IS '深层恐惧';
COMMENT ON COLUMN character_detail.bottom_line             IS '底线/不可触碰之事';
COMMENT ON COLUMN character_detail.value_ranking           IS '价值观排序（JSONB）';
COMMENT ON COLUMN character_detail.talkativeness           IS '寡言/正常/话痨';
COMMENT ON COLUMN character_detail.sentence_style          IS '短句/混合/长句';
COMMENT ON COLUMN character_detail.word_preference         IS '朴实/华丽/专业/俚语';
COMMENT ON COLUMN character_detail.emotion_expression      IS '内敛/直白/夸张/阴阳怪气';
COMMENT ON COLUMN character_detail.action_style            IS '谋定后动/先打再说/随机应变/依赖他人';
COMMENT ON COLUMN character_detail.effective_chapter       IS '生效章节';
COMMENT ON COLUMN character_detail.expire_chapter          IS '失效章节（NULL=永远有效）';
COMMENT ON COLUMN character_detail.version_number          IS '版本号';
COMMENT ON COLUMN character_detail.extra_attributes        IS '扩展属性';
COMMENT ON COLUMN character_detail.created_at              IS '创建时间';

-- ============================================================================
-- 8. chapter_content — 章节正文（当前版本）
-- ============================================================================
CREATE TABLE chapter_content (
    id              BIGSERIAL    PRIMARY KEY,
    structure_id    BIGINT       NOT NULL,
    version_number  INT          NOT NULL,
    content         TEXT         NOT NULL,
    word_count      INT,
    source          VARCHAR(20),
    storage_type    VARCHAR(10)  DEFAULT 'FULL',
    base_version_id BIGINT,
    diff_data       TEXT,
    created_at      TIMESTAMPTZ  DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  DEFAULT NOW()
);

ALTER TABLE chapter_content
    ADD CONSTRAINT fk_content_structure    FOREIGN KEY (structure_id)    REFERENCES chapter_structure (id),
    ADD CONSTRAINT fk_content_base_version FOREIGN KEY (base_version_id) REFERENCES chapter_content (id);

CREATE INDEX idx_content_structure ON chapter_content (structure_id);

COMMENT ON TABLE  chapter_content                          IS '章节正文（当前版本，旧版归档至 archive 表）';
COMMENT ON COLUMN chapter_content.structure_id             IS '关联大纲节点（场景级，FK → chapter_structure.id）';
COMMENT ON COLUMN chapter_content.version_number           IS '版本号';
COMMENT ON COLUMN chapter_content.content                  IS '正文内容';
COMMENT ON COLUMN chapter_content.word_count               IS '字数';
COMMENT ON COLUMN chapter_content.source                   IS 'AI_GENERATED / USER_EDITED / AI_REWRITTEN';
COMMENT ON COLUMN chapter_content.storage_type             IS 'MVP 预留，暂固定为 FULL，后续支持 FULL / DIFF';
COMMENT ON COLUMN chapter_content.base_version_id          IS 'MVP 预留，DIFF 时指向基准版本（自引用 FK）';
COMMENT ON COLUMN chapter_content.diff_data                IS 'MVP 预留，DIFF 存储的差异数据';
COMMENT ON COLUMN chapter_content.created_at               IS '创建时间';
COMMENT ON COLUMN chapter_content.updated_at               IS '更新时间';

-- ============================================================================
-- 9. chapter_summary — 章节结构化概要
-- ============================================================================
CREATE TABLE chapter_summary (
    id                      BIGSERIAL    PRIMARY KEY,
    structure_id            BIGINT       NOT NULL,
    summary_version         INT          NOT NULL,
    core_events             JSONB,
    appearing_characters    JSONB,
    character_state_changes JSONB,
    new_foreshadowings      JSONB,
    recycled_foreshadowings JSONB,
    emotion_curve_point     JSONB,
    key_scenes              JSONB,
    world_elements          JSONB,
    summary_type            VARCHAR(10)  DEFAULT 'SHORT',
    is_permanent            BOOLEAN      DEFAULT FALSE,
    extra_attributes        JSONB,
    created_at              TIMESTAMPTZ  DEFAULT NOW()
);

ALTER TABLE chapter_summary
    ADD CONSTRAINT fk_summary_structure FOREIGN KEY (structure_id) REFERENCES chapter_structure (id);

CREATE INDEX idx_summary_structure ON chapter_summary (structure_id);
CREATE INDEX idx_summary_permanent ON chapter_summary (is_permanent) WHERE is_permanent = TRUE;

COMMENT ON TABLE  chapter_summary                          IS '章节结构化概要';
COMMENT ON COLUMN chapter_summary.structure_id             IS '关联大纲节点（FK → chapter_structure.id）';
COMMENT ON COLUMN chapter_summary.summary_version          IS '概要版本';
COMMENT ON COLUMN chapter_summary.core_events              IS '核心事件（3-5句，JSONB）';
COMMENT ON COLUMN chapter_summary.appearing_characters     IS '出场人物及动作（JSONB）';
COMMENT ON COLUMN chapter_summary.character_state_changes  IS '角色状态变化（JSONB）';
COMMENT ON COLUMN chapter_summary.new_foreshadowings       IS '新埋伏笔 ID 列表（JSONB）';
COMMENT ON COLUMN chapter_summary.recycled_foreshadowings  IS '已回收伏笔 ID 列表（JSONB）';
COMMENT ON COLUMN chapter_summary.emotion_curve_point      IS '冲突等级/情感浓度/节奏（JSONB）';
COMMENT ON COLUMN chapter_summary.key_scenes               IS '关键场景标记（JSONB）';
COMMENT ON COLUMN chapter_summary.world_elements           IS '涉及的世界观要素（JSONB）';
COMMENT ON COLUMN chapter_summary.summary_type             IS 'SHORT / LONG';
COMMENT ON COLUMN chapter_summary.is_permanent             IS '是否永久保留';
COMMENT ON COLUMN chapter_summary.extra_attributes         IS '扩展属性';
COMMENT ON COLUMN chapter_summary.created_at               IS '创建时间';

-- ============================================================================
-- 10. generation_log — AI 调用记录
-- ============================================================================
CREATE TABLE generation_log (
    id              BIGSERIAL    PRIMARY KEY,
    structure_id    BIGINT,
    prompt_snapshot TEXT,
    model           VARCHAR(50),
    input_tokens    INT,
    output_tokens   INT,
    cost            DECIMAL(8,4),
    duration_ms     INT,
    success         BOOLEAN      DEFAULT TRUE,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  DEFAULT NOW()
);

ALTER TABLE generation_log
    ADD CONSTRAINT fk_log_structure FOREIGN KEY (structure_id) REFERENCES chapter_structure (id);

CREATE INDEX idx_log_time ON generation_log (created_at);

COMMENT ON TABLE  generation_log                           IS 'AI 调用记录';
COMMENT ON COLUMN generation_log.structure_id              IS '关联章节（可选，FK → chapter_structure.id）';
COMMENT ON COLUMN generation_log.prompt_snapshot           IS '提示词快照（摘要）';
COMMENT ON COLUMN generation_log.model                     IS '使用模型';
COMMENT ON COLUMN generation_log.input_tokens              IS '输入 token 数';
COMMENT ON COLUMN generation_log.output_tokens             IS '输出 token 数';
COMMENT ON COLUMN generation_log.cost                      IS '费用（元）';
COMMENT ON COLUMN generation_log.duration_ms               IS '耗时（毫秒）';
COMMENT ON COLUMN generation_log.success                   IS '是否成功';
COMMENT ON COLUMN generation_log.error_message             IS '错误信息';
COMMENT ON COLUMN generation_log.created_at                IS '创建时间';
