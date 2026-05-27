package com.youmo.api.controller;

import com.youmo.api.dto.request.CreateOutlineNodeRequest;
import com.youmo.api.dto.request.MoveNodeRequest;
import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.Book;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.enums.NodeStatus;
import com.youmo.common.enums.NodeType;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.service.BookService;
import com.youmo.core.service.ChapterStructureService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/api/books/{bookId}/outline")
@RequiredArgsConstructor
public class ChapterStructureController {

    private final ChapterStructureService chapterStructureService;
    private final BookRepository bookRepository;
    private final ChapterStructureRepository chapterStructureRepository;
    private final BookService bookService;

    private void assertOwnership(Long bookId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Book book = bookService.getById(bookId)
            .orElseThrow(() -> new BusinessException(404, "书籍不存在"));
        if (book.getOwner() == null || !book.getOwner().getId().equals(userId)) {
            throw new BusinessException(403, "无权访问此书");
        }
    }

    @GetMapping
    public ApiResponse<List<ChapterStructure>> getTree(@PathVariable Long bookId) {
        assertOwnership(bookId);
        return ApiResponse.ok(chapterStructureService.getTree(bookId));
    }

    @PostMapping("/node")
    public ApiResponse<ChapterStructure> createNode(@PathVariable Long bookId,
                                                     @RequestBody CreateOutlineNodeRequest req) {
        assertOwnership(bookId);
        ChapterStructure node = new ChapterStructure();
        node.setBook(bookRepository.getReferenceById(bookId));
        node.setTitle(req.getTitle());
        node.setNodeType(req.getNodeType() != null ? NodeType.valueOf(req.getNodeType()) : NodeType.VOLUME);
        node.setSequence(req.getSequence() != null ? req.getSequence() : 0);
        node.setWritingGoal(req.getWritingGoal());
        if (req.getParentId() != null) {
            node.setParent(chapterStructureRepository.getReferenceById(req.getParentId()));
        }
        var created = chapterStructureService.createNode(node);
        log.info("Outline node created: id={}, type={}, title={}, bookId={}", created.getId(), req.getNodeType(), req.getTitle(), bookId);
        return ApiResponse.ok(created);
    }

    @PutMapping("/{id}/move")
    public ApiResponse<Void> moveNode(@PathVariable Long bookId, @PathVariable Long id, @RequestBody MoveNodeRequest request) {
        assertOwnership(bookId);
        chapterStructureService.moveNode(id, request.getNewParentId(), request.getNewSequence());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    public ApiResponse<ChapterStructure> updateNode(@PathVariable Long bookId, @PathVariable Long id,
                                                     @RequestBody CreateOutlineNodeRequest req) {
        assertOwnership(bookId);
        ChapterStructure updates = new ChapterStructure();
        updates.setTitle(req.getTitle());
        updates.setNodeType(req.getNodeType() != null ? NodeType.valueOf(req.getNodeType()) : null);
        updates.setSequence(req.getSequence());
        updates.setWritingGoal(req.getWritingGoal());
        return ApiResponse.ok(chapterStructureService.updateNode(id, updates));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNode(@PathVariable Long bookId, @PathVariable Long id) {
        assertOwnership(bookId);
        chapterStructureService.delete(id);
        log.info("Outline node deleted: id={}, bookId={}", id, bookId);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long bookId, @PathVariable Long id, @RequestBody NodeStatus status) {
        assertOwnership(bookId);
        chapterStructureService.updateStatus(id, status);
        return ApiResponse.ok();
    }
}
