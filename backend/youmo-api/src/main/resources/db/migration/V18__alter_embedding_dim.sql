-- V18: Change embedding dimension from 1536 (DeepSeek) to 512 (BGE-small-zh)
-- Drop and recreate HNSW index since vector dimension is part of index definition
DROP INDEX IF EXISTS idx_chapter_embedding_hnsw;
ALTER TABLE chapter_embedding ALTER COLUMN embedding TYPE vector(512);
CREATE INDEX idx_chapter_embedding_hnsw ON chapter_embedding USING hnsw (embedding vector_cosine_ops);
