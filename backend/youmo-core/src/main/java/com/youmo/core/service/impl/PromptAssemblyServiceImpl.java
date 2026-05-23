package com.youmo.core.service.impl;

import com.youmo.common.entity.Book;
import com.youmo.common.entity.Character;
import com.youmo.common.entity.WorldSetting;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.repository.CharacterRepository;
import com.youmo.core.repository.WorldSettingRepository;
import com.youmo.core.service.PromptAssemblyService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptAssemblyServiceImpl implements PromptAssemblyService {

    private final CharacterRepository characterRepository;
    private final WorldSettingRepository worldSettingRepository;
    private final BookRepository bookRepository;

    private static final int MAX_CHARACTERS = 3;

    @Override
    public String buildContinuePrompt(Long bookId, String basePrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append(basePrompt != null ? basePrompt : "");

        // ── Book style info ──
        Book book = bookRepository.findById(bookId).orElse(null);
        if (book != null) {
            if (book.getCoreIdea() != null && !book.getCoreIdea().isBlank()) {
                sb.append("\n作品核心想法：").append(book.getCoreIdea().strip()).append("\n");
            }
            if (book.getOneSentence() != null && !book.getOneSentence().isBlank()) {
                sb.append("一句话梗概：").append(book.getOneSentence().strip()).append("\n");
            }
            if (book.getToneLabels() != null && !book.getToneLabels().isBlank()) {
                sb.append("文风标签：").append(book.getToneLabels().strip()).append("\n");
            }
        }

        // ── Character cards ──
        List<Character> characters = characterRepository.findByBookId(bookId);
        if (!characters.isEmpty()) {
            sb.append("\n已知角色：\n");
            characters.stream()
                .limit(MAX_CHARACTERS)
                .forEach(c -> {
                    sb.append("- ").append(c.getName());
                    if (c.getGender() != null && !c.getGender().isBlank()) {
                        sb.append("（").append(c.getGender()).append("）");
                    }
                    if (c.getIdentity() != null && !c.getIdentity().isBlank()) {
                        sb.append("，").append(c.getIdentity());
                    }
                    if (c.getAgeDescription() != null && !c.getAgeDescription().isBlank()) {
                        sb.append("，").append(c.getAgeDescription());
                    }
                    if (c.getAppearance() != null && !c.getAppearance().isBlank()) {
                        sb.append("，外貌：").append(c.getAppearance());
                    }
                    sb.append("\n");
                });
        }

        // ── World settings ──
        Optional<WorldSetting> worldOpt = worldSettingRepository.findByBookId(bookId);
        worldOpt.ifPresent(ws -> {
            sb.append("\n世界观设定：\n");
            if (ws.getEra() != null && !ws.getEra().isBlank()) {
                sb.append("- 时代：").append(ws.getEra().strip()).append("\n");
            }
            if (ws.getGeography() != null && !ws.getGeography().isBlank()) {
                sb.append("- 地理：").append(ws.getGeography().strip()).append("\n");
            }
            if (ws.getCoreRuleType() != null && !ws.getCoreRuleType().isBlank()) {
                sb.append("- 核心规则类型：").append(ws.getCoreRuleType().strip());
                if (ws.getCoreRuleSummary() != null && !ws.getCoreRuleSummary().isBlank()) {
                    sb.append("（").append(ws.getCoreRuleSummary().strip()).append("）");
                }
                sb.append("\n");
            }
            if (ws.getCulture() != null && !ws.getCulture().isBlank()) {
                sb.append("- 文化：").append(ws.getCulture().strip()).append("\n");
            }
            if (ws.getPolitics() != null && !ws.getPolitics().isBlank()) {
                sb.append("- 政治：").append(ws.getPolitics().strip()).append("\n");
            }
        });

        // ── Negative constraints (banned words/phrases) ──
        if (book != null && book.getNegativeConstraints() != null && !book.getNegativeConstraints().isBlank()) {
            sb.append("\n严禁使用以下词汇或句式（违者视为失败）：\n");
            for (String line : book.getNegativeConstraints().split("\\R")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty()) {
                    sb.append("- ").append(trimmed).append("\n");
                }
            }
        }

        log.debug("Assembled prompt for book {}: {} chars", bookId, sb.length());
        return sb.toString();
    }
}
