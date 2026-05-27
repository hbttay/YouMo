-- V11: 伏笔管理表
CREATE TABLE foreshadowing (
    id                         BIGSERIAL    PRIMARY KEY,
    book_id                    BIGINT       NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    description                TEXT         NOT NULL,
    foreshadowing_type         VARCHAR(20)  NOT NULL DEFAULT 'EVENT',
    importance                 VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    status                     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    target_entity              VARCHAR(200),
    created_chapter_id         BIGINT       REFERENCES chapter_structure(id) ON DELETE SET NULL,
    planned_recycle_chapter_id BIGINT       REFERENCES chapter_structure(id) ON DELETE SET NULL,
    recycled_chapter_id        BIGINT       REFERENCES chapter_structure(id) ON DELETE SET NULL,
    created_at                 TIMESTAMPTZ  DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_foreshadowing_book_id ON foreshadowing(book_id);
CREATE INDEX idx_foreshadowing_book_status ON foreshadowing(book_id, status);

COMMENT ON TABLE  foreshadowing                          IS '伏笔管理';
COMMENT ON COLUMN foreshadowing.foreshadowing_type        IS 'ITEM/EVENT/CHARACTER/RELATIONSHIP/PLOT_TWIST';
COMMENT ON COLUMN foreshadowing.importance                IS 'HIGH/MEDIUM/LOW';
COMMENT ON COLUMN foreshadowing.status                    IS 'ACTIVE/RECYCLED/DROPPED';
