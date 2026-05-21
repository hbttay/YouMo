-- V3__alter_depth_level.sql
-- 将 character.depth_level 从 CHAR(2) 改为 VARCHAR(2)，对齐 JPA @Enumerated(STRING)

ALTER TABLE character ALTER COLUMN depth_level TYPE VARCHAR(2);
