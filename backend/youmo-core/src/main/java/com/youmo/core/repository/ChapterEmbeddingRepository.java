package com.youmo.core.repository;

import com.youmo.common.entity.ChapterEmbedding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChapterEmbeddingRepository extends JpaRepository<ChapterEmbedding, Long> {

    List<ChapterEmbedding> findByBookIdOrderByCreatedAtDesc(Long bookId);

    boolean existsBySummaryId(Long summaryId);

    @Query(value = """
        SELECT ce.*, 1 - (ce.embedding <=> CAST(:queryVector AS vector)) AS similarity
        FROM chapter_embedding ce
        WHERE ce.book_id = :bookId
        ORDER BY ce.embedding <=> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ChapterEmbedding> findSimilarByBook(
        @Param("bookId") Long bookId,
        @Param("queryVector") String queryVector,
        @Param("limit") int limit
    );
}
