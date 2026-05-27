package com.youmo.api.controller;

import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.ChapterContent;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.entity.ChapterSummary;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.service.ChapterAnalysisService;
import com.youmo.core.service.ChapterContentService;
import com.youmo.core.service.ConsistencyCheckService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChapterContentController {

    private final ChapterContentService chapterContentService;
    private final ChapterStructureRepository chapterStructureRepository;
    private final ChapterAnalysisService chapterAnalysisService;
    private final ConsistencyCheckService consistencyCheckService;

    /** Verify the current user owns the book that contains this structure */
    private void assertStructureOwnership(Long structureId) {
        Long userId = SecurityUtil.getCurrentUserId();
        ChapterStructure structure = chapterStructureRepository.findById(structureId)
            .orElseThrow(() -> new BusinessException(404, "章节不存在"));
        if (structure.getBook() == null || structure.getBook().getOwner() == null
            || !structure.getBook().getOwner().getId().equals(userId)) {
            throw new BusinessException(403, "无权访问此内容");
        }
    }

    @GetMapping("/api/chapters/{structureId}/content")
    public ApiResponse<ChapterContent> getLatest(@PathVariable Long structureId) {
        assertStructureOwnership(structureId);
        return chapterContentService.getLatest(structureId)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(404, "章节内容不存在"));
    }

    @PostMapping("/api/chapters/{structureId}/content")
    public ApiResponse<ChapterContent> save(@PathVariable Long structureId, @RequestBody ChapterContent content) {
        assertStructureOwnership(structureId);
        content.setStructure(chapterStructureRepository.getReferenceById(structureId));
        var saved = chapterContentService.save(content);
        log.info("Chapter content saved: structureId={}, status={}, words={}", structureId, saved.getStatus(), saved.getWordCount());
        return ApiResponse.ok(saved);
    }

    @GetMapping("/api/chapters/{structureId}/content/versions")
    public ApiResponse<List<ChapterContent>> getVersions(@PathVariable Long structureId) {
        assertStructureOwnership(structureId);
        return ApiResponse.ok(chapterContentService.getVersionHistory(structureId));
    }

    @PostMapping("/api/chapters/{structureId}/analyze")
    public ApiResponse<ChapterSummary> analyze(@PathVariable Long structureId) {
        assertStructureOwnership(structureId);
        String content = chapterContentService.getLatest(structureId)
            .map(ChapterContent::getContent)
            .orElse("");
        ChapterStructure structure = chapterStructureRepository.findById(structureId)
            .orElseThrow(() -> new RuntimeException("章节不存在"));
        Long bookId = structure.getBook() != null ? structure.getBook().getId() : null;
        return ApiResponse.ok(chapterAnalysisService.analyze(bookId, structureId, content));
    }

    @PostMapping("/api/chapters/{structureId}/consistency-check")
    public ApiResponse<ConsistencyCheckService.ConsistencyReport> checkConsistency(
            @PathVariable Long structureId) {
        assertStructureOwnership(structureId);
        String content = chapterContentService.getLatest(structureId)
            .map(ChapterContent::getContent)
            .orElse("");
        if (content.isBlank()) {
            return ApiResponse.ok(new ConsistencyCheckService.ConsistencyReport(
                List.of(), List.of(), List.of(), List.of(), List.of()));
        }
        int split = Math.min(content.length() / 2, 2000);
        String before = content.substring(0, split);
        String after = content.substring(split);
        var report = consistencyCheckService.checkAll(before, after);
        log.info("Consistency check done: structureId={}, issues={}", structureId, report.allIssues().size());
        return ApiResponse.ok(report);
    }

}
