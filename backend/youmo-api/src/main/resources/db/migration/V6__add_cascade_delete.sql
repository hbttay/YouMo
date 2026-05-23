-- Add ON DELETE CASCADE to FK constraints. Uses DO blocks to skip non-existent tables.

DO $$ BEGIN
    ALTER TABLE world_setting DROP CONSTRAINT IF EXISTS fk_world_setting_book;
    ALTER TABLE world_setting ADD CONSTRAINT fk_world_setting_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE;
EXCEPTION WHEN undefined_table THEN END $$;

DO $$ BEGIN
    ALTER TABLE chapter_structure DROP CONSTRAINT IF EXISTS fk_structure_book;
    ALTER TABLE chapter_structure ADD CONSTRAINT fk_structure_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE;
EXCEPTION WHEN undefined_table THEN END $$;

DO $$ BEGIN
    ALTER TABLE chapter_structure DROP CONSTRAINT IF EXISTS fk_structure_parent;
    ALTER TABLE chapter_structure ADD CONSTRAINT fk_structure_parent FOREIGN KEY (parent_id) REFERENCES chapter_structure (id) ON DELETE CASCADE;
EXCEPTION WHEN undefined_table THEN END $$;

DO $$ BEGIN
    ALTER TABLE chapter_content DROP CONSTRAINT IF EXISTS fk_content_structure;
    ALTER TABLE chapter_content ADD CONSTRAINT fk_content_structure FOREIGN KEY (structure_id) REFERENCES chapter_structure (id) ON DELETE CASCADE;
EXCEPTION WHEN undefined_table THEN END $$;

DO $$ BEGIN
    ALTER TABLE character DROP CONSTRAINT IF EXISTS fk_character_book;
    ALTER TABLE character ADD CONSTRAINT fk_character_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE;
EXCEPTION WHEN undefined_table THEN END $$;
