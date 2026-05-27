package com.youmo.api.controller;

import com.youmo.api.dto.response.BookStatsResponse;
import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.ChapterContent;
import com.youmo.common.enums.DepthLevel;
import com.youmo.common.enums.NodeStatus;
import com.youmo.common.enums.NodeType;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.repository.ChapterContentRepository;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.repository.CharacterRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class StatsController {

    private final ChapterStructureRepository structureRepo;
    private final ChapterContentRepository contentRepo;
    private final CharacterRepository characterRepo;
    private final BookRepository bookRepo;

    public StatsController(ChapterStructureRepository structureRepo,
                           ChapterContentRepository contentRepo,
                           CharacterRepository characterRepo,
                           BookRepository bookRepo) {
        this.structureRepo = structureRepo;
        this.contentRepo = contentRepo;
        this.characterRepo = characterRepo;
        this.bookRepo = bookRepo;
    }

    @GetMapping("/{bookId}/stats")
    public ApiResponse<BookStatsResponse> getStats(@PathVariable Long bookId) {
        // Ownership check
        Long userId = SecurityUtil.getCurrentUserId();
        var book = bookRepo.findById(bookId).orElse(null);
        if (book == null || book.getOwner() == null
                || !book.getOwner().getId().equals(userId)) {
            throw new BusinessException(403, "无权访问此书");
        }

        var structures = structureRepo.findByBookIdOrderBySequenceAsc(bookId);

        // Collect chapter IDs for batch fetch
        List<Long> chapterIds = new ArrayList<>();
        int volumeCount = 0;
        int chapterCount = 0;
        int completedChapters = 0;

        for (var s : structures) {
            if (s.getNodeType() == NodeType.VOLUME) {
                volumeCount++;
            } else if (s.getNodeType() == NodeType.CHAPTER) {
                chapterCount++;
                chapterIds.add(s.getId());
                if (s.getStatus() == NodeStatus.COMPLETED) {
                    completedChapters++;
                }
            }
        }

        // Batch fetch latest content word counts
        Map<Long, Integer> wordCountMap = new LinkedHashMap<>();
        if (!chapterIds.isEmpty()) {
            List<ChapterContent> contents = contentRepo.findLatestByStructureIds(chapterIds);
            for (ChapterContent c : contents) {
                if (c.getStructure() != null) {
                    wordCountMap.put(c.getStructure().getId(),
                        c.getWordCount() != null ? c.getWordCount() : 0);
                }
            }
        }

        int totalWords = 0;
        List<BookStatsResponse.ChapterWordStat> chapterWordCounts = new ArrayList<>();
        for (var s : structures) {
            if (s.getNodeType() == NodeType.CHAPTER) {
                int wc = wordCountMap.getOrDefault(s.getId(), 0);
                totalWords += wc;
                chapterWordCounts.add(new BookStatsResponse.ChapterWordStat(
                    s.getTitle() != null ? s.getTitle() : "未命名", wc));
            }
        }

        var characters = characterRepo.findByBookId(bookId);
        int characterCount = characters.size();
        Map<String, Integer> charactersByDepth = new LinkedHashMap<>();
        for (var c : characters) {
            DepthLevel dl = c.getDepthLevel() != null ? c.getDepthLevel() : DepthLevel.L1;
            charactersByDepth.merge(dl.name(), 1, Integer::sum);
        }

        return ApiResponse.ok(new BookStatsResponse(totalWords, volumeCount, chapterCount,
            completedChapters, characterCount, charactersByDepth, chapterWordCounts));
    }
}
