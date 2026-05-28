package com.youmo.api.controller;

import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.Book;
import com.youmo.common.entity.CharacterRelationship;
import com.youmo.core.service.BookService;
import com.youmo.core.service.CharacterRelationshipService;
import java.util.List;
import java.util.Map;
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

@Slf4j
@RestController
@RequestMapping("/api/books/{bookId}/character-relationships")
@RequiredArgsConstructor
public class CharacterRelationshipController {

    private final CharacterRelationshipService relationshipService;
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
    public ApiResponse<List<CharacterRelationship>> list(@PathVariable Long bookId) {
        assertOwnership(bookId);
        return ApiResponse.ok(relationshipService.listByBook(bookId));
    }

    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> graph(@PathVariable Long bookId) {
        assertOwnership(bookId);
        return ApiResponse.ok(relationshipService.getGraph(bookId));
    }

    @PostMapping
    public ApiResponse<CharacterRelationship> create(@PathVariable Long bookId,
                                                     @RequestBody CharacterRelationship relationship) {
        assertOwnership(bookId);
        Book book = bookService.getById(bookId).orElseThrow();
        relationship.setBook(book);
        CharacterRelationship created = relationshipService.create(relationship);
        log.info("Relationship created: bookId={}, id={}", bookId, created.getId());
        return ApiResponse.ok(created);
    }

    @PutMapping("/{id}")
    public ApiResponse<CharacterRelationship> update(@PathVariable Long bookId,
                                                     @PathVariable Long id,
                                                     @RequestBody CharacterRelationship update) {
        assertOwnership(bookId);
        CharacterRelationship updated = relationshipService.update(id, update);
        log.info("Relationship updated: bookId={}, id={}", bookId, id);
        return ApiResponse.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long bookId, @PathVariable Long id) {
        assertOwnership(bookId);
        relationshipService.delete(id);
        log.info("Relationship deleted: bookId={}, id={}", bookId, id);
        return ApiResponse.ok(null);
    }
}
