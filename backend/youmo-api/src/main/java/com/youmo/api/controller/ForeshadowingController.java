package com.youmo.api.controller;

import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.Book;
import com.youmo.common.entity.Foreshadowing;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.service.BookService;
import com.youmo.core.service.ChapterContentService;
import com.youmo.core.service.ForeshadowingService;
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
@RequestMapping("/api/books/{bookId}/foreshadowings")
@RequiredArgsConstructor
public class ForeshadowingController {

    private final ForeshadowingService foreshadowingService;
    private final BookService bookService;
    private final ChapterContentService chapterContentService;
    private final ChapterStructureRepository chapterStructureRepository;

    private void assertOwnership(Long bookId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Book book = bookService.getById(bookId)
            .orElseThrow(() -> new BusinessException(404, "书籍不存在"));
        if (book.getOwner() == null || !book.getOwner().getId().equals(userId)) {
            throw new BusinessException(403, "无权访问此书");
        }
    }

    @GetMapping
    public ApiResponse<List<Foreshadowing>> list(@PathVariable Long bookId) {
        assertOwnership(bookId);
        return ApiResponse.ok(foreshadowingService.listByBook(bookId));
    }

    @PostMapping
    public ApiResponse<Foreshadowing> create(@PathVariable Long bookId,
                                             @RequestBody Foreshadowing foreshadowing) {
        assertOwnership(bookId);
        Book book = bookService.getById(bookId).orElseThrow();
        foreshadowing.setBook(book);
        return ApiResponse.ok(foreshadowingService.create(foreshadowing));
    }

    @PutMapping("/{id}")
    public ApiResponse<Foreshadowing> update(@PathVariable Long bookId,
                                             @PathVariable Long id,
                                             @RequestBody Foreshadowing update) {
        assertOwnership(bookId);
        return ApiResponse.ok(foreshadowingService.update(id, update));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long bookId, @PathVariable Long id) {
        assertOwnership(bookId);
        foreshadowingService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/scan/{chapterStructureId}")
    public ApiResponse<Map<String, List<Foreshadowing>>> scanChapter(
            @PathVariable Long bookId,
            @PathVariable Long chapterStructureId) {
        assertOwnership(bookId);
        var chapter = chapterStructureRepository.findById(chapterStructureId)
            .orElseThrow(() -> new BusinessException(404, "章节不存在"));
        if (chapter.getBook() == null || !chapter.getBook().getId().equals(bookId)) {
            throw new BusinessException(403, "章节不属于此书");
        }
        String content = chapterContentService.getLatest(chapterStructureId)
            .map(c -> c.getContent())
            .orElse("");
        return ApiResponse.ok(foreshadowingService.scanChapter(bookId, chapterStructureId, content));
    }
}
