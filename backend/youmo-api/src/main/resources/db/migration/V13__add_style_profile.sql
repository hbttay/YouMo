-- V13: 作者风格分析表
CREATE TABLE book_style_profile (
    id                       BIGSERIAL    PRIMARY KEY,
    book_id                  BIGINT       NOT NULL UNIQUE REFERENCES book(id) ON DELETE CASCADE,
    avg_sentence_length      DOUBLE PRECISION,
    dialogue_ratio           DOUBLE PRECISION,
    paragraph_style          VARCHAR(20),
    description_action_ratio DOUBLE PRECISION,
    vocabulary_richness      DOUBLE PRECISION,
    sentence_variety         DOUBLE PRECISION,
    chapter_opening_pattern  JSONB,
    tone_analysis            JSONB,
    writing_habits           JSONB,
    sample_chapter_count     INT,
    style_label              VARCHAR(100),
    created_at               TIMESTAMPTZ  DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  DEFAULT NOW()
);

COMMENT ON TABLE  book_style_profile                       IS '作者风格分析';
COMMENT ON COLUMN book_style_profile.avg_sentence_length    IS '平均句长（字数）';
COMMENT ON COLUMN book_style_profile.dialogue_ratio         IS '对话比例（0-1）';
COMMENT ON COLUMN book_style_profile.paragraph_style        IS 'SHORT/MEDIUM/LONG';
COMMENT ON COLUMN book_style_profile.description_action_ratio IS '描写与动作比例';
COMMENT ON COLUMN book_style_profile.vocabulary_richness    IS '词汇丰富度';
COMMENT ON COLUMN book_style_profile.style_label            IS '风格标签：细腻/简洁/热血/沉稳等';
