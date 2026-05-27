ALTER TABLE book ADD COLUMN IF NOT EXISTS sequence INT NOT NULL DEFAULT 0;

-- Initialize sequence based on creation order for existing books
UPDATE book b SET sequence = sub.rn
FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY owner_id ORDER BY created_at) AS rn FROM book) sub
WHERE b.id = sub.id;
