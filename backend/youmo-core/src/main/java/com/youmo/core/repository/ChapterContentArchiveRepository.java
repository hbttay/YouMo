package com.youmo.core.repository;

import com.youmo.common.entity.ChapterContentArchive;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChapterContentArchiveRepository extends JpaRepository<ChapterContentArchive, Long> {

    List<ChapterContentArchive> findByStructureIdOrderByVersionNumberDesc(Long structureId);
}
