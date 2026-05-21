package com.youmo.api.controller;

import com.youmo.api.dto.request.MoveNodeRequest;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.enums.NodeStatus;
import com.youmo.core.service.ChapterStructureService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/outline")
@RequiredArgsConstructor
public class ChapterStructureController {

    private final ChapterStructureService chapterStructureService;

    @GetMapping
    public ApiResponse<List<ChapterStructure>> getTree(@PathVariable Long bookId) {
        return ApiResponse.ok(chapterStructureService.getTree(bookId));
    }

    @PostMapping("/node")
    public ApiResponse<ChapterStructure> createNode(@PathVariable Long bookId, @RequestBody ChapterStructure node) {
        return ApiResponse.ok(chapterStructureService.createNode(node));
    }

    @PutMapping("/{id}/move")
    public ApiResponse<Void> moveNode(@PathVariable Long id, @RequestBody MoveNodeRequest request) {
        chapterStructureService.moveNode(id, request.getNewParentId(), request.getNewSequence());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody NodeStatus status) {
        chapterStructureService.updateStatus(id, status);
        return ApiResponse.ok();
    }
}
