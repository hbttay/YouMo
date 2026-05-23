package com.youmo.api.controller;

import com.youmo.api.dto.request.CreateOutlineNodeRequest;
import com.youmo.api.dto.request.MoveNodeRequest;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.enums.NodeStatus;
import com.youmo.common.enums.NodeType;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.service.ChapterStructureService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final BookRepository bookRepository;
    private final ChapterStructureRepository chapterStructureRepository;

    @GetMapping
    public ApiResponse<List<ChapterStructure>> getTree(@PathVariable Long bookId) {
        return ApiResponse.ok(chapterStructureService.getTree(bookId));
    }

    @PostMapping("/node")
    public ApiResponse<ChapterStructure> createNode(@PathVariable Long bookId,
                                                     @RequestBody CreateOutlineNodeRequest req) {
        ChapterStructure node = new ChapterStructure();
        node.setBook(bookRepository.getReferenceById(bookId));
        node.setTitle(req.getTitle());
        node.setNodeType(req.getNodeType() != null ? NodeType.valueOf(req.getNodeType()) : NodeType.VOLUME);
        node.setSequence(req.getSequence() != null ? req.getSequence() : 0);
        node.setWritingGoal(req.getWritingGoal());
        if (req.getParentId() != null) {
            node.setParent(chapterStructureRepository.getReferenceById(req.getParentId()));
        }
        return ApiResponse.ok(chapterStructureService.createNode(node));
    }

    @PutMapping("/{id}/move")
    public ApiResponse<Void> moveNode(@PathVariable Long id, @RequestBody MoveNodeRequest request) {
        chapterStructureService.moveNode(id, request.getNewParentId(), request.getNewSequence());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    public ApiResponse<ChapterStructure> updateNode(@PathVariable Long id,
                                                     @RequestBody CreateOutlineNodeRequest req) {
        ChapterStructure updates = new ChapterStructure();
        updates.setTitle(req.getTitle());
        updates.setNodeType(req.getNodeType() != null ? NodeType.valueOf(req.getNodeType()) : null);
        updates.setSequence(req.getSequence());
        updates.setWritingGoal(req.getWritingGoal());
        return ApiResponse.ok(chapterStructureService.updateNode(id, updates));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNode(@PathVariable Long id) {
        chapterStructureService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody NodeStatus status) {
        chapterStructureService.updateStatus(id, status);
        return ApiResponse.ok();
    }
}
