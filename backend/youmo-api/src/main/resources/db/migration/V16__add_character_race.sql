ALTER TABLE character ADD COLUMN IF NOT EXISTS race VARCHAR(50);

COMMENT ON COLUMN character.race IS '种族（人类/精灵/兽人/龙族/仙族/魔族/未知等）';
