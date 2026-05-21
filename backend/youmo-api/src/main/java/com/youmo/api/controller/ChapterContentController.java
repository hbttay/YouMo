package com.youmo.api.controller;

import com.youmo.common.base.ApiResponse;
import com.youmo.common.entity.ChapterContent;
import com.youmo.core.service.ChapterContentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chapters/{structureId}/content")
@RequiredArgsConstructor
public class ChapterContentController {

    private final ChapterContentService chapterContentService;

    @GetMapping
    public ApiResponse<ChapterContent> getLatest(@PathVariable Long structureId) {
        return chapterContentService.getLatest(structureId)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(404, "章节内容不存在"));
    }

    @PostMapping
    public ApiResponse<ChapterContent> save(@PathVariable Long structureId, @RequestBody ChapterContent content) {
        return ApiResponse.ok(chapterContentService.save(content));
    }

    @GetMapping("/versions")
    public ApiResponse<List<ChapterContent>> getVersions(@PathVariable Long structureId) {
        return ApiResponse.ok(chapterContentService.getVersionHistory(structureId));
    }
}
