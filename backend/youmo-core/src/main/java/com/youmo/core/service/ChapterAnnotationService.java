package com.youmo.core.service;

import com.youmo.common.entity.ChapterContentAnnotation;
import java.util.List;

public interface ChapterAnnotationService {
    List<ChapterContentAnnotation> listByStructure(Long structureId, String status);
    ChapterContentAnnotation create(ChapterContentAnnotation annotation);
    ChapterContentAnnotation resolve(Long id, String resolvedComment, Long userId);
    ChapterContentAnnotation reopen(Long id);
    void delete(Long id);
    void batchUpdate(List<Long> ids, String action, String resolvedComment, Long userId);
}
