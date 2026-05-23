package com.youmo.api.controller;

import com.youmo.common.entity.Book;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.entity.Character;
import com.youmo.common.entity.WorldSetting;
import com.youmo.core.service.BookService;
import com.youmo.core.service.ChapterContentService;
import com.youmo.core.service.ChapterStructureService;
import com.youmo.core.service.CharacterService;
import com.youmo.core.service.WorldSettingService;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class ExportController {

    private final BookService bookService;
    private final ChapterStructureService structureService;
    private final ChapterContentService contentService;
    private final CharacterService characterService;
    private final WorldSettingService worldSettingService;

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportMd(@PathVariable Long id) {
        Book book = bookService.getById(id)
                .orElseThrow(() -> new RuntimeException("书籍不存在"));

        StringBuilder md = new StringBuilder();
        md.append("# ").append(book.getTitle()).append("\n\n");

        if (book.getCoreIdea() != null && !book.getCoreIdea().isBlank()) {
            md.append("> ").append(book.getCoreIdea()).append("\n\n");
        }
        md.append("---\n\n");

        // ── World Setting ──
        var wsOpt = worldSettingService.getByBookId(id);
        if (wsOpt.isPresent()) {
            WorldSetting ws = wsOpt.get();
            md.append("## 世界观设定\n\n");
            appendField(md, "时代", ws.getEra());
            appendField(md, "地理", ws.getGeography());
            appendField(md, "历史事件", ws.getHistoryEvents());
            appendField(md, "政治", ws.getPolitics());
            appendField(md, "经济", ws.getEconomy());
            appendField(md, "文化", ws.getCulture());
            appendField(md, "军事", ws.getMilitary());
            appendField(md, "核心规则", ws.getCoreRuleType());
            appendField(md, "规则概要", ws.getCoreRuleSummary());
            appendField(md, "补充设定", ws.getExtraAttributes());
            md.append("\n---\n\n");
        }

        // ── Characters ──
        List<Character> chars = characterService.listByBook(id);
        if (!chars.isEmpty()) {
            md.append("## 角色列表\n\n");
            for (Character c : chars) {
                md.append("### ").append(c.getName()).append("\n\n");
                appendField(md, "性别", c.getGender());
                appendField(md, "年龄", c.getAgeDescription());
                appendField(md, "外貌", c.getAppearance());
                appendField(md, "出身", c.getOrigin());
                appendField(md, "身份", c.getIdentity());
                md.append("\n");
            }
            md.append("---\n\n");
        }

        // ── Outline & Content ──
        List<ChapterStructure> nodes = structureService.getTree(id);
        Map<Long, List<ChapterStructure>> childrenByParent = nodes.stream()
                .filter(n -> n.getParentId() != null)
                .collect(Collectors.groupingBy(ChapterStructure::getParentId));

        List<ChapterStructure> roots = nodes.stream()
                .filter(n -> n.getParentId() == null)
                .sorted(Comparator.comparing(ChapterStructure::getSequence))
                .collect(Collectors.toList());

        md.append("## 正文\n\n");
        for (ChapterStructure volume : roots) {
            md.append("## ").append(volume.getTitle()).append("\n\n");
            List<ChapterStructure> chapters = childrenByParent.getOrDefault(volume.getId(), List.of())
                    .stream()
                    .sorted(Comparator.comparing(ChapterStructure::getSequence))
                    .collect(Collectors.toList());

            for (ChapterStructure chapter : chapters) {
                md.append("### ").append(chapter.getTitle()).append("\n\n");
                appendContent(md, chapter.getId());

                List<ChapterStructure> scenes = childrenByParent.getOrDefault(chapter.getId(), List.of())
                        .stream()
                        .sorted(Comparator.comparing(ChapterStructure::getSequence))
                        .collect(Collectors.toList());

                for (ChapterStructure scene : scenes) {
                    md.append("#### ").append(scene.getTitle()).append("\n\n");
                    appendContent(md, scene.getId());
                }
            }
        }

        byte[] bytes = md.toString().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(sanitizeFilename(book.getTitle()) + ".md", StandardCharsets.UTF_8)
                .build());

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private void appendContent(StringBuilder md, Long structureId) {
        var content = contentService.getLatest(structureId);
        if (content.isPresent() && content.get().getContent() != null
                && !content.get().getContent().isBlank()) {
            md.append(content.get().getContent().strip()).append("\n\n");
        }
    }

    private void appendField(StringBuilder md, String label, String value) {
        if (value != null && !value.isBlank()) {
            md.append("- **").append(label).append("**：").append(value.strip()).append("\n");
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
