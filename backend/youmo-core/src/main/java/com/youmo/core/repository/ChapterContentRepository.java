package com.youmo.core.repository;

import com.youmo.common.entity.ChapterContent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChapterContentRepository extends JpaRepository<ChapterContent, Long> {

    List<ChapterContent> findByStructureIdOrderByVersionNumberDesc(Long structureId);

    Optional<ChapterContent> findTopByStructureIdOrderByVersionNumberDesc(Long structureId);
}
