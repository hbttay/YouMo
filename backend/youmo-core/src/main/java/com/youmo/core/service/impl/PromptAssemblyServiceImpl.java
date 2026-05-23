package com.youmo.core.service.impl;

import com.youmo.common.entity.Book;
import com.youmo.common.entity.Character;
import com.youmo.common.entity.WorldSetting;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.repository.CharacterRepository;
import com.youmo.core.repository.WorldSettingRepository;
import com.youmo.core.service.ContextKeywordExtractor;
import com.youmo.core.service.PromptAssemblyService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ContextKeywordExtractor extractor;

    private static final int MAX_CHARACTERS = 3;

    @Override
    public String buildContinuePrompt(Long bookId, String basePrompt, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append(basePrompt != null ? basePrompt : "");

        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null) {
            log.debug("Book {} not found, returning base prompt only", bookId);
            return sb.toString();
        }

        // ── Book style info ──
        if (book.getCoreIdea() != null && !book.getCoreIdea().isBlank()) {
            sb.append("\n作品核心想法：").append(book.getCoreIdea().strip()).append("\n");
        }
        if (book.getOneSentence() != null && !book.getOneSentence().isBlank()) {
            sb.append("一句话梗概：").append(book.getOneSentence().strip()).append("\n");
        }
        if (book.getToneLabels() != null && !book.getToneLabels().isBlank()) {
            sb.append("文风标签：").append(book.getToneLabels().strip()).append("\n");
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

        // ── World settings (relevance-filtered) ──
        Optional<WorldSetting> worldOpt = worldSettingRepository.findByBookId(bookId);
        worldOpt.ifPresent(ws -> {
            List<ContextKeywordExtractor.FieldScore> ranked = extractor.rankFields(context);

            // Determine injection level from book config
            String level = parseInjectionLevel(book.getExtraAttributes());
            ContextKeywordExtractor.LevelConfig config = ContextKeywordExtractor.getLevelConfig(level);

            // Collect all available world fields
            Map<String, String> allFields = collectWorldFields(ws);

            // Parse custom fields from extra_attributes
            Map<String, String> customFields = extractor.parseExtraAttributes(ws.getExtraAttributes());

            // Rank custom fields against context too
            List<ContextKeywordExtractor.FieldScore> customScores = rankCustomFields(customFields, context);

            if (!allFields.isEmpty() || !customFields.isEmpty()) {
                sb.append("\n世界观设定：\n");
                int charsInjected = 0;

                // Inject ranked fixed fields up to limit
                for (ContextKeywordExtractor.FieldScore fs : ranked) {
                    if (charsInjected >= config.maxChars()) break;
                    String content = allFields.get(fs.field());
                    if (content != null) {
                        String line = "- " + fs.label() + "：" + content + "\n";
                        sb.append(line);
                        charsInjected += line.length();
                    }
                }

                // Inject custom fields (up to half of remaining budget)
                int customBudget = Math.max(50, (config.maxChars() - charsInjected) / 2);
                for (ContextKeywordExtractor.FieldScore fs : customScores) {
                    if (charsInjected >= config.maxChars() || customBudget <= 0) break;
                    String content = customFields.get(fs.field());
                    if (content != null) {
                        String line = "- " + fs.field() + "：" + content + "\n";
                        sb.append(line);
                        charsInjected += line.length();
                        customBudget -= line.length();
                    }
                }

                log.debug("World setting injected: {} chars at level {}", charsInjected, level);
            }
        });

        // ── Negative constraints ──
        if (book.getNegativeConstraints() != null && !book.getNegativeConstraints().isBlank()) {
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

    // ── helpers ──

    private Map<String, String> collectWorldFields(WorldSetting ws) {
        Map<String, String> fields = new LinkedHashMap<>();
        putIf(fields, "era", ws.getEra());
        putIf(fields, "geography", ws.getGeography());
        putIf(fields, "history_events", ws.getHistoryEvents());
        putIf(fields, "politics", ws.getPolitics());
        putIf(fields, "economy", ws.getEconomy());
        putIf(fields, "military", ws.getMilitary());
        putIf(fields, "culture", ws.getCulture());
        putIf(fields, "core_rule", combine(ws.getCoreRuleType(), ws.getCoreRuleSummary()));
        return fields;
    }

    private void putIf(Map<String, String> m, String key, String val) {
        if (val != null && !val.isBlank()) m.put(key, val.strip());
    }

    private String combine(String type, String summary) {
        if (type == null || type.isBlank()) return summary;
        if (summary == null || summary.isBlank()) return type;
        return type + "（" + summary + "）";
    }

    private String parseInjectionLevel(String extraAttributesJson) {
        Map<String, String> attrs = extractor.parseExtraAttributes(extraAttributesJson);
        return attrs.getOrDefault("ai_injection_level", "STANDARD");
    }

    private List<ContextKeywordExtractor.FieldScore> rankCustomFields(
            Map<String, String> customFields, String context) {
        // Custom fields: score based on whether their key or value contains context keywords
        java.util.Set<String> ctxKw = extractor.extractKeywords(context);
        return customFields.keySet().stream()
            .map(key -> {
                int score = 0;
                for (String kw : ctxKw) {
                    if (key.contains(kw) || customFields.get(key).contains(kw)) score++;
                }
                return new ContextKeywordExtractor.FieldScore(key, key, score);
            })
            .filter(fs -> fs.score() > 0)
            .sorted((a, b) -> b.score() - a.score())
            .toList();
    }
}
