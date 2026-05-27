package com.youmo.core.repository;

import com.youmo.common.entity.ChapterContent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ChapterContentRepository extends JpaRepository<ChapterContent, Long> {

    List<ChapterContent> findByStructureIdOrderByVersionNumberDesc(Long structureId);

    Optional<ChapterContent> findTopByStructureIdOrderByVersionNumberDesc(Long structureId);

    @Query("SELECT c FROM ChapterContent c WHERE c.structure.id IN :structureIds " +
           "AND c.versionNumber = (SELECT MAX(c2.versionNumber) FROM ChapterContent c2 WHERE c2.structure.id = c.structure.id)")
    List<ChapterContent> findLatestByStructureIds(Collection<Long> structureIds);
}
