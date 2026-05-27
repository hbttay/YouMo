-- V12: 角色关系管理表
CREATE TABLE character_relationship (
    id                  BIGSERIAL    PRIMARY KEY,
    book_id             BIGINT       NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    source_character_id BIGINT       NOT NULL REFERENCES character(id) ON DELETE CASCADE,
    target_character_id BIGINT       NOT NULL REFERENCES character(id) ON DELETE CASCADE,
    relationship_type   VARCHAR(50)  NOT NULL,
    description         TEXT,
    intimacy_level      SMALLINT     DEFAULT 1 CHECK (intimacy_level >= 1 AND intimacy_level <= 10),
    start_chapter_id    BIGINT       REFERENCES chapter_structure(id) ON DELETE SET NULL,
    end_chapter_id      BIGINT       REFERENCES chapter_structure(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ  DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_char_rel_book_id ON character_relationship(book_id);
CREATE INDEX idx_char_rel_source ON character_relationship(source_character_id);
CREATE INDEX idx_char_rel_target ON character_relationship(target_character_id);

COMMENT ON TABLE  character_relationship                  IS '角色关系';
COMMENT ON COLUMN character_relationship.relationship_type IS '父子/师徒/恋人/敌对/盟友等';
COMMENT ON COLUMN character_relationship.intimacy_level    IS '亲密度 1-10';
