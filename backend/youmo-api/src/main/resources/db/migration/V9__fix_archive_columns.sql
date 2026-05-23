-- V9__fix_archive_columns.sql
-- V8 漏了 BaseEntity 的 created_at / updated_at，补上

ALTER TABLE chapter_content_archive
    ADD COLUMN created_at TIMESTAMPTZ DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMPTZ DEFAULT NOW();
