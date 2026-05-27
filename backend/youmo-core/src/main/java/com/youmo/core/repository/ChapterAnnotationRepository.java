package com.youmo.core.repository;

import com.youmo.common.entity.ChapterContentAnnotation;
import com.youmo.common.enums.AnnotationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterAnnotationRepository extends JpaRepository<ChapterContentAnnotation, Long> {
    List<ChapterContentAnnotation> findByStructureIdOrderByCreatedAtDesc(Long structureId);
    List<ChapterContentAnnotation> findByStructureIdAndStatusOrderByCreatedAtDesc(Long structureId, AnnotationStatus status);
    List<ChapterContentAnnotation> findByIdIn(List<Long> ids);
}
