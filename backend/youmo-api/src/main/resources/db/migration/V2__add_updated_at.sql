-- V2__add_updated_at.sql
-- 为 V1 中仅有 created_at 的四张表补充 updated_at 列

ALTER TABLE personality_version ADD COLUMN updated_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE character_detail    ADD COLUMN updated_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE chapter_summary     ADD COLUMN updated_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE generation_log      ADD COLUMN updated_at TIMESTAMPTZ DEFAULT NOW();
