-- V15: Enable pgvector and create chapter embedding table
-- Requires: CREATE EXTENSION vector (install pgvector first if not available)
CREATE EXTENSION IF NOT EXISTS vector;

-- Chapter embedding: store compressed chapter summaries as vectors
CREATE TABLE chapter_embedding (
    id BIGSERIAL PRIMARY KEY,
    summary_id BIGINT NOT NULL REFERENCES chapter_summary(id) ON DELETE CASCADE,
    book_id BIGINT NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    structure_id BIGINT NOT NULL REFERENCES chapter_structure(id) ON DELETE CASCADE,
    content_text TEXT NOT NULL,
    embedding vector(1536),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chapter_embedding_summary UNIQUE (summary_id)
);

CREATE INDEX idx_chapter_embedding_book ON chapter_embedding(book_id);
-- HNSW index for fast approximate nearest neighbor search
CREATE INDEX idx_chapter_embedding_hnsw ON chapter_embedding USING hnsw (embedding vector_cosine_ops);
