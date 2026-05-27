CREATE TABLE chapter_content_annotation (
    id                 BIGSERIAL PRIMARY KEY,
    structure_id       BIGINT NOT NULL REFERENCES chapter_structure(id) ON DELETE CASCADE,
    content_version_id BIGINT NOT NULL REFERENCES chapter_content(id) ON DELETE CASCADE,
    annotation_type    VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status             VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    char_offset_start  INT NOT NULL,
    char_offset_end    INT NOT NULL,
    anchor_text        TEXT NOT NULL,
    context_before     TEXT,
    context_after      TEXT,
    comment            TEXT NOT NULL,
    category           VARCHAR(50),
    severity           VARCHAR(10) DEFAULT 'INFO',
    resolved_comment   TEXT,
    resolved_by        BIGINT,
    resolved_at        TIMESTAMPTZ,
    batch_id           VARCHAR(36),
    created_by         BIGINT NOT NULL,
    created_at         TIMESTAMPTZ DEFAULT NOW(),
    updated_at         TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_annotation_structure ON chapter_content_annotation(structure_id);
CREATE INDEX idx_annotation_status    ON chapter_content_annotation(status);
