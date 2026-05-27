package com.youmo.api.controller;

import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.ChapterContent;
import com.youmo.common.entity.ChapterContentAnnotation;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.core.repository.ChapterContentRepository;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.service.ChapterAnnotationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chapters/{structureId}/annotations")
@RequiredArgsConstructor
public class ChapterAnnotationController {

    private final ChapterAnnotationService annotationService;
    private final ChapterStructureRepository structureRepository;
    private final ChapterContentRepository contentRepository;

    private void assertStructureOwnership(Long structureId) {
        Long userId = SecurityUtil.getCurrentUserId();
        ChapterStructure structure = structureRepository.findById(structureId)
            .orElseThrow(() -> new BusinessException(404, "章节不存在"));
        if (structure.getBook() == null || structure.getBook().getOwner() == null
            || !structure.getBook().getOwner().getId().equals(userId)) {
            throw new BusinessException(403, "无权访问此内容");
        }
    }

    @GetMapping
    public ApiResponse<List<ChapterContentAnnotation>> list(
            @PathVariable Long structureId,
            @RequestParam(required = false) String status) {
        assertStructureOwnership(structureId);
        return ApiResponse.ok(annotationService.listByStructure(structureId, status));
    }

    @PostMapping
    public ApiResponse<ChapterContentAnnotation> create(
            @PathVariable Long structureId,
            @RequestBody ChapterContentAnnotation annotation) {
        assertStructureOwnership(structureId);
        annotation.setStructure(structureRepository.getReferenceById(structureId));
        if (annotation.getContentVersion() == null || annotation.getContentVersion().getId() == null) {
            ChapterContent latest = contentRepository.findTopByStructureIdOrderByVersionNumberDesc(structureId)
                .orElseThrow(() -> new BusinessException(400, "章节没有已保存的内容版本"));
            annotation.setContentVersion(latest);
        }
        annotation.setCreatedBy(SecurityUtil.getCurrentUserId());
        return ApiResponse.ok(annotationService.create(annotation));
    }

    @PutMapping("/{id}/resolve")
    public ApiResponse<ChapterContentAnnotation> resolve(
            @PathVariable Long structureId,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        assertStructureOwnership(structureId);
        return ApiResponse.ok(annotationService.resolve(
            id, body.getOrDefault("resolvedComment", ""), SecurityUtil.getCurrentUserId()));
    }

    @PutMapping("/{id}/reopen")
    public ApiResponse<ChapterContentAnnotation> reopen(
            @PathVariable Long structureId,
            @PathVariable Long id) {
        assertStructureOwnership(structureId);
        return ApiResponse.ok(annotationService.reopen(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long structureId,
            @PathVariable Long id) {
        assertStructureOwnership(structureId);
        annotationService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/batch")
    public ApiResponse<Void> batchUpdate(
            @PathVariable Long structureId,
            @RequestBody Map<String, Object> body) {
        assertStructureOwnership(structureId);
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("ids");
        List<Long> ids = rawIds.stream().map(Integer::longValue).toList();
        String action = (String) body.getOrDefault("action", "resolve");
        String comment = (String) body.getOrDefault("resolvedComment", "");
        annotationService.batchUpdate(ids, action, comment, SecurityUtil.getCurrentUserId());
        return ApiResponse.ok();
    }
}
