package com.youmo.api.controller;

import com.youmo.api.dto.response.SearchResult;
import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.ChapterContent;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.enums.NodeType;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.repository.ChapterContentRepository;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.repository.CharacterRepository;
import com.youmo.core.repository.WorldSettingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/books")
public class SearchController {

    private final ChapterContentRepository contentRepo;
    private final ChapterStructureRepository structureRepo;
    private final CharacterRepository characterRepo;
    private final WorldSettingRepository worldSettingRepo;
    private final BookRepository bookRepo;

    public SearchController(ChapterContentRepository contentRepo,
                            ChapterStructureRepository structureRepo,
                            CharacterRepository characterRepo,
                            WorldSettingRepository worldSettingRepo,
                            BookRepository bookRepo) {
        this.contentRepo = contentRepo;
        this.structureRepo = structureRepo;
        this.characterRepo = characterRepo;
        this.worldSettingRepo = worldSettingRepo;
        this.bookRepo = bookRepo;
    }

    @GetMapping("/{bookId}/search")
    public ApiResponse<SearchResult> search(@PathVariable Long bookId,
                                             @RequestParam String q) {
        Long userId = SecurityUtil.getCurrentUserId();
        var book = bookRepo.findById(bookId).orElse(null);
        if (book == null || book.getOwner() == null
                || !book.getOwner().getId().equals(userId)) {
            throw new BusinessException(403, "无权访问此书");
        }

        if (q == null || q.isBlank() || q.length() < 2) {
            return ApiResponse.fail(400, "搜索关键词至少2个字");
        }

        String keyword = q.strip();
        List<SearchResult.SearchMatch> matches = new ArrayList<>();

        // 1. Search chapter content
        var structures = structureRepo.findByBookIdOrderBySequenceAsc(bookId);
        Map<Long, ChapterStructure> structureMap = new java.util.LinkedHashMap<>();
        List<Long> chapterIds = new ArrayList<>();
        for (var s : structures) {
            structureMap.put(s.getId(), s);
            if (s.getNodeType() == NodeType.CHAPTER || s.getNodeType() == NodeType.SCENE) {
                chapterIds.add(s.getId());
            }
        }

        if (!chapterIds.isEmpty()) {
            var contents = contentRepo.findLatestByStructureIds(chapterIds);
            for (var c : contents) {
                if (c.getContent() == null || c.getContent().isBlank()) continue;
                String text = c.getContent();
                int idx = text.indexOf(keyword);
                if (idx == -1) {
                    // Case-insensitive fallback
                    idx = text.toLowerCase().indexOf(keyword.toLowerCase());
                }
                if (idx >= 0) {
                    Long sid = c.getStructure() != null ? c.getStructure().getId() : null;
                    ChapterStructure st = sid != null ? structureMap.get(sid) : null;
                    String title = st != null && st.getTitle() != null ? st.getTitle() : "未命名";
                    String nodeType = st != null && st.getNodeType() != null ? st.getNodeType().name() : "SCENE";
                    String snippet = buildSnippet(text, idx, keyword.length());
                    matches.add(new SearchResult.SearchMatch("chapter", title, snippet, sid, nodeType, null));
                }
            }
        }

        // 2. Search chapter/volume titles in outline
        for (var s : structures) {
            if (s.getTitle() != null && s.getTitle().contains(keyword)) {
                matches.add(new SearchResult.SearchMatch("outline",
                    s.getTitle(), "大纲节点匹配",
                    s.getId(), s.getNodeType() != null ? s.getNodeType().name() : null, null));
            }
        }

        // 3. Search characters
        var characters = characterRepo.findByBookId(bookId);
        for (var ch : characters) {
            boolean nameMatch = ch.getName() != null && ch.getName().contains(keyword);
            boolean identityMatch = ch.getIdentity() != null && ch.getIdentity().contains(keyword);
            if (nameMatch || identityMatch) {
                String snippet = ch.getIdentity() != null ? ch.getIdentity() : "";
                if (ch.getAppearance() != null) snippet += " · " + ch.getAppearance();
                if (snippet.length() > 100) snippet = snippet.substring(0, 97) + "...";
                matches.add(new SearchResult.SearchMatch("character",
                    ch.getName(), snippet, null, null, ch.getId()));
            }
        }

        // 4. Search world setting
        var worldOpt = worldSettingRepo.findByBookId(bookId);
        worldOpt.ifPresent(w -> {
            checkWorldField(w.getEra(), "时代背景", keyword, matches);
            checkWorldField(w.getGeography(), "地理环境", keyword, matches);
            checkWorldField(w.getPolitics(), "政治格局", keyword, matches);
            checkWorldField(w.getCulture(), "文化特色", keyword, matches);
            checkWorldField(w.getCoreRuleSummary(), "核心规则", keyword, matches);
            checkWorldField(w.getHistoryEvents(), "历史事件", keyword, matches);
        });

        // Limit results
        List<SearchResult.SearchMatch> limited = matches.size() > 30
            ? new ArrayList<>(matches.subList(0, 30)) : matches;

        SearchResult result = new SearchResult();
        result.setQuery(keyword);
        result.setMatches(limited);
        result.setTotalMatches(matches.size());
        return ApiResponse.ok(result);
    }

    private String buildSnippet(String text, int matchIdx, int kwLen) {
        int start = Math.max(0, matchIdx - 40);
        int end = Math.min(text.length(), matchIdx + kwLen + 40);
        StringBuilder sb = new StringBuilder();
        if (start > 0) sb.append("...");
        sb.append(text, start, end);
        if (end < text.length()) sb.append("...");
        String snippet = sb.toString();
        if (snippet.length() > 150) snippet = snippet.substring(0, 147) + "...";
        return snippet;
    }

    private void checkWorldField(String value, String label, String keyword,
                                  List<SearchResult.SearchMatch> matches) {
        if (value != null && value.contains(keyword)) {
            matches.add(new SearchResult.SearchMatch("world",
                label, value.length() > 120 ? value.substring(0, 117) + "..." : value,
                null, null, null));
        }
    }
}
