package com.youmo.core.repository;

import com.youmo.common.entity.ChapterSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChapterSummaryRepository extends JpaRepository<ChapterSummary, Long> {

    Optional<ChapterSummary> findByStructureId(Long structureId);

    List<ChapterSummary> findByStructure_Book_Id(Long bookId);
}
