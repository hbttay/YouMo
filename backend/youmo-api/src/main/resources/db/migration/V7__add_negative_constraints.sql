-- V7__add_negative_constraints.sql
-- 负向约束：用户自定义 AI 禁用词/短语，一行一条

ALTER TABLE book
    ADD COLUMN negative_constraints TEXT;

COMMENT ON COLUMN book.negative_constraints IS '负向约束（AI 续写禁用词/短语，换行分隔）';
