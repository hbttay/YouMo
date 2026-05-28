package com.youmo.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youmo.api.dto.response.BookStatsResponse;
import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.ChapterContent;
import com.youmo.common.enums.ContentSource;
import com.youmo.common.enums.DepthLevel;
import com.youmo.common.enums.NodeStatus;
import com.youmo.common.enums.NodeType;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.repository.ChapterContentRepository;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.repository.CharacterRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/books")
public class StatsController {

    private final ChapterStructureRepository structureRepo;
    private final ChapterContentRepository contentRepo;
    private final CharacterRepository characterRepo;
    private final BookRepository bookRepo;
    private final ObjectMapper objectMapper;

    public StatsController(ChapterStructureRepository structureRepo,
                           ChapterContentRepository contentRepo,
                           CharacterRepository characterRepo,
                           BookRepository bookRepo,
                           ObjectMapper objectMapper) {
        this.structureRepo = structureRepo;
        this.contentRepo = contentRepo;
        this.characterRepo = characterRepo;
        this.bookRepo = bookRepo;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{bookId}/stats")
    public ApiResponse<BookStatsResponse> getStats(@PathVariable Long bookId) {
        Long userId = SecurityUtil.getCurrentUserId();
        var book = bookRepo.findById(bookId).orElse(null);
        if (book == null || book.getOwner() == null
                || !book.getOwner().getId().equals(userId)) {
            throw new BusinessException(403, "无权访问此书");
        }

        var structures = structureRepo.findByBookIdOrderBySequenceAsc(bookId);

        List<Long> chapterIds = new ArrayList<>();
        int volumeCount = 0, chapterCount = 0, completedChapters = 0;
        Map<String, Integer> nodeStatusBreakdown = new LinkedHashMap<>();
        for (NodeStatus s : NodeStatus.values()) nodeStatusBreakdown.put(s.name(), 0);

        for (var s : structures) {
            if (s.getNodeType() == NodeType.VOLUME) {
                volumeCount++;
            } else if (s.getNodeType() == NodeType.CHAPTER) {
                chapterCount++;
                chapterIds.add(s.getId());
                if (s.getStatus() == NodeStatus.COMPLETED) completedChapters++;
            }
            nodeStatusBreakdown.merge(s.getStatus() != null ? s.getStatus().name() : "DRAFT", 1, Integer::sum);
        }

        // Batch fetch latest content
        Map<Long, ChapterContent> contentByStructureId = new LinkedHashMap<>();
        if (!chapterIds.isEmpty()) {
            for (var c : contentRepo.findLatestByStructureIds(chapterIds)) {
                if (c.getStructure() != null) contentByStructureId.put(c.getStructure().getId(), c);
            }
        }

        int totalWords = 0;
        Map<String, Integer> sourceBreakdown = new LinkedHashMap<>();
        for (ContentSource cs : ContentSource.values()) sourceBreakdown.put(cs.name(), 0);
        Map<LocalDate, Integer> dailyWords = new LinkedHashMap<>();

        List<BookStatsResponse.ChapterWordStat> chapterWordCounts = new ArrayList<>();
        for (var s : structures) {
            if (s.getNodeType() == NodeType.CHAPTER) {
                var c = contentByStructureId.get(s.getId());
                int wc = c != null && c.getWordCount() != null ? c.getWordCount() : 0;
                totalWords += wc;
                chapterWordCounts.add(new BookStatsResponse.ChapterWordStat(
                    s.getTitle() != null ? s.getTitle() : "未命名", wc));
                if (c != null) {
                    if (c.getSource() != null) sourceBreakdown.merge(c.getSource().name(), 1, Integer::sum);
                    if (c.getCreatedAt() != null) {
                        LocalDate d = c.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
                        dailyWords.merge(d, wc, Integer::sum);
                    }
                }
            }
        }

        // Volume word counts (aggregate children)
        Map<Long, List<com.youmo.common.entity.ChapterStructure>> childrenByParent = new LinkedHashMap<>();
        for (var s : structures) {
            if (s.getParent() != null) {
                childrenByParent.computeIfAbsent(s.getParent().getId(), k -> new ArrayList<>()).add(s);
            }
        }
        List<BookStatsResponse.VolumeWordStat> volumeWordCounts = new ArrayList<>();
        for (var s : structures) {
            if (s.getNodeType() != NodeType.VOLUME) continue;
            var children = childrenByParent.getOrDefault(s.getId(), List.of());
            int vw = 0, vc = 0, vcc = 0;
            for (var ch : children) {
                if (ch.getNodeType() == NodeType.CHAPTER) {
                    vc++;
                    int cw = 0;
                    var ct = contentByStructureId.get(ch.getId());
                    if (ct != null && ct.getWordCount() != null) cw = ct.getWordCount();
                    vw += cw;
                    if (ch.getStatus() == NodeStatus.COMPLETED) vcc++;
                }
            }
            volumeWordCounts.add(new BookStatsResponse.VolumeWordStat(
                s.getTitle() != null ? s.getTitle() : "未命名", vw, vc, vcc));
        }

        // Characters
        var characters = characterRepo.findByBookId(bookId);
        int characterCount = characters.size();
        Map<String, Integer> charactersByDepth = new LinkedHashMap<>();
        List<BookStatsResponse.CharacterAppearanceStat> charAppearances = new ArrayList<>();
        for (var ch : characters) {
            DepthLevel dl = ch.getDepthLevel() != null ? ch.getDepthLevel() : DepthLevel.L1;
            charactersByDepth.merge(dl.name(), 1, Integer::sum);
            int appearCount = parseJsonArraySize(ch.getAppearChapters());
            if (appearCount > 0) {
                charAppearances.add(new BookStatsResponse.CharacterAppearanceStat(
                    ch.getName(), appearCount));
            }
        }
        charAppearances.sort((a, b) -> Integer.compare(b.getChapterCount(), a.getChapterCount()));

        // Daily activity sorted
        List<BookStatsResponse.DailyWordStat> dailyActivity = dailyWords.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new BookStatsResponse.DailyWordStat(
                e.getKey().format(DateTimeFormatter.ISO_LOCAL_DATE), e.getValue()))
            .collect(Collectors.toList());

        var resp = new BookStatsResponse();
        resp.setTotalWords(totalWords);
        resp.setVolumeCount(volumeCount);
        resp.setChapterCount(chapterCount);
        resp.setCompletedChapters(completedChapters);
        resp.setCharacterCount(characterCount);
        resp.setCharactersByDepth(charactersByDepth);
        resp.setNodeStatusBreakdown(nodeStatusBreakdown);
        resp.setSourceBreakdown(sourceBreakdown);
        resp.setChapterWordCounts(chapterWordCounts);
        resp.setVolumeWordCounts(volumeWordCounts);
        resp.setCharacterAppearances(charAppearances);
        resp.setDailyActivity(dailyActivity);
        return ApiResponse.ok(resp);
    }

    private int parseJsonArraySize(String json) {
        if (json == null || json.isBlank() || json.equals("null")) return 0;
        try {
            var list = objectMapper.readValue(json, new TypeReference<List<?>>() {});
            return list.size();
        } catch (Exception e) {
            return 0;
        }
    }
}
