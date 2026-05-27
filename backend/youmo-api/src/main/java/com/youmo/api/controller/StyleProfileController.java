package com.youmo.api.controller;

import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.Book;
import com.youmo.common.entity.BookStyleProfile;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.core.repository.ChapterContentRepository;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.service.BookService;
import com.youmo.core.service.StyleProfileService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/books/{bookId}/style-profile")
@RequiredArgsConstructor
public class StyleProfileController {

    private final StyleProfileService styleProfileService;
    private final BookService bookService;
    private final ChapterStructureRepository chapterStructureRepository;
    private final ChapterContentRepository chapterContentRepository;

    private void assertOwnership(Long bookId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Book book = bookService.getById(bookId)
            .orElseThrow(() -> new BusinessException(404, "书籍不存在"));
        if (book.getOwner() == null || !book.getOwner().getId().equals(userId)) {
            throw new BusinessException(403, "无权访问此书");
        }
    }

    @GetMapping
    public ApiResponse<BookStyleProfile> get(@PathVariable Long bookId) {
        assertOwnership(bookId);
        return ApiResponse.ok(styleProfileService.getOrCreate(bookId));
    }

    @PostMapping("/analyze")
    public ApiResponse<BookStyleProfile> analyze(@PathVariable Long bookId) {
        assertOwnership(bookId);

        // Collect sample chapter texts (up to 5 chapters with content)
        List<ChapterStructure> structures = chapterStructureRepository.findByBookIdOrderBySequenceAsc(bookId);
        List<String> samples = new ArrayList<>();
        for (ChapterStructure cs : structures) {
            if (samples.size() >= 5) break;
            var content = chapterContentRepository.findTopByStructureIdOrderByVersionNumberDesc(cs.getId());
            content.ifPresent(c -> {
                if (c.getContent() != null && !c.getContent().isBlank()) {
                    samples.add(c.getContent());
                }
            });
        }

        if (samples.isEmpty()) {
            return ApiResponse.fail(400, "没有可分析的章节内容，请先写一些正文");
        }

        return ApiResponse.ok(styleProfileService.analyze(bookId, samples));
    }
}
