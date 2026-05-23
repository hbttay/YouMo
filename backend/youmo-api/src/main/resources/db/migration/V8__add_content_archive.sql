-- V8__add_content_archive.sql
-- 章节正文历史版本归档表（冷热分离）

CREATE TABLE chapter_content_archive (
    id                  BIGSERIAL    PRIMARY KEY,
    original_content_id BIGINT,
    structure_id        BIGINT       NOT NULL,
    version_number      INT          NOT NULL,
    content             TEXT         NOT NULL,
    word_count          INT,
    source              VARCHAR(20),
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    archived_at         TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_archive_structure ON chapter_content_archive (structure_id);

COMMENT ON TABLE  chapter_content_archive                  IS '章节正文历史版本归档（冷数据）';
COMMENT ON COLUMN chapter_content_archive.original_content_id IS '归档前在 chapter_content 中的 id';
COMMENT ON COLUMN chapter_content_archive.structure_id     IS '关联大纲节点';
COMMENT ON COLUMN chapter_content_archive.version_number   IS '版本号';
COMMENT ON COLUMN chapter_content_archive.content          IS '正文全文（归档时转为 FULL 存储）';
COMMENT ON COLUMN chapter_content_archive.archived_at      IS '归档时间';
