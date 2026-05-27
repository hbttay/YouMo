package com.youmo.core.service.impl;

import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.ChapterContentAnnotation;
import com.youmo.common.enums.AnnotationStatus;
import com.youmo.core.repository.ChapterAnnotationRepository;
import com.youmo.core.service.ChapterAnnotationService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChapterAnnotationServiceImpl implements ChapterAnnotationService {

    private final ChapterAnnotationRepository repository;

    @Override
    public List<ChapterContentAnnotation> listByStructure(Long structureId, String status) {
        if (status != null && !status.isBlank()) {
            return repository.findByStructureIdAndStatusOrderByCreatedAtDesc(
                structureId, AnnotationStatus.valueOf(status.toUpperCase()));
        }
        return repository.findByStructureIdOrderByCreatedAtDesc(structureId);
    }

    @Override
    @Transactional
    public ChapterContentAnnotation create(ChapterContentAnnotation annotation) {
        return repository.save(annotation);
    }

    @Override
    @Transactional
    public ChapterContentAnnotation resolve(Long id, String resolvedComment, Long userId) {
        ChapterContentAnnotation a = repository.findById(id)
            .orElseThrow(() -> new BusinessException(404, "批注不存在"));
        a.setStatus(AnnotationStatus.RESOLVED);
        a.setResolvedComment(resolvedComment);
        a.setResolvedBy(userId);
        a.setResolvedAt(Instant.now());
        return repository.save(a);
    }

    @Override
    @Transactional
    public ChapterContentAnnotation reopen(Long id) {
        ChapterContentAnnotation a = repository.findById(id)
            .orElseThrow(() -> new BusinessException(404, "批注不存在"));
        a.setStatus(AnnotationStatus.OPEN);
        a.setResolvedComment(null);
        a.setResolvedBy(null);
        a.setResolvedAt(null);
        return repository.save(a);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException(404, "批注不存在");
        }
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public void batchUpdate(List<Long> ids, String action, String resolvedComment, Long userId) {
        List<ChapterContentAnnotation> annotations = repository.findByIdIn(ids);
        AnnotationStatus targetStatus = "dismiss".equals(action)
            ? AnnotationStatus.DISMISSED : AnnotationStatus.RESOLVED;
        for (ChapterContentAnnotation a : annotations) {
            a.setStatus(targetStatus);
            if (targetStatus == AnnotationStatus.RESOLVED) {
                a.setResolvedComment(resolvedComment);
                a.setResolvedBy(userId);
                a.setResolvedAt(Instant.now());
            }
        }
        repository.saveAll(annotations);
    }
}
