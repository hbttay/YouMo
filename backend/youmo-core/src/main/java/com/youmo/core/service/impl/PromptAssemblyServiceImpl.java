package com.youmo.core.service.impl;

import com.youmo.common.entity.Book;
import com.youmo.common.entity.Character;
import com.youmo.common.entity.CharacterDetail;
import com.youmo.common.entity.WorldSetting;
import com.youmo.common.entity.ChapterEmbedding;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.repository.BookStyleProfileRepository;
import com.youmo.core.repository.CharacterDetailRepository;
import com.youmo.core.repository.CharacterRepository;
import com.youmo.core.repository.WorldSettingRepository;
import com.youmo.core.service.ChapterEmbeddingService;
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
    private final CharacterDetailRepository characterDetailRepository;
    private final WorldSettingRepository worldSettingRepository;
    private final BookRepository bookRepository;
    private final BookStyleProfileRepository bookStyleProfileRepository;
    private final ContextKeywordExtractor extractor;
    private final ChapterEmbeddingService chapterEmbeddingService;

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
            // Sort by depth_level: L3 first, L0 last
            List<Character> sorted = characters.stream()
                .sorted((a, b) -> {
                    int da = a.getDepthLevel() != null ? depthOrdinal(a.getDepthLevel().name()) : 0;
                    int db = b.getDepthLevel() != null ? depthOrdinal(b.getDepthLevel().name()) : 0;
                    return db - da;
                })
                .limit(MAX_CHARACTERS)
                .toList();

            for (Character c : sorted) {
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

                // Inject personality for L2+ characters
                String dl = c.getDepthLevel() != null ? c.getDepthLevel().name() : "L1";
                if (("L2".equals(dl) || "L3".equals(dl)) && sb.length() < 4000) {
                    characterDetailRepository.findByCharacterId(c.getId())
                        .ifPresent(d -> appendPersonality(sb, d, book.getTheme()));
                }
            }
        }

        // ── World settings (relevance-filtered) ──
        Optional<WorldSetting> worldOpt = worldSettingRepository.findByBookId(bookId);
        worldOpt.ifPresent(ws -> {
            List<ContextKeywordExtractor.FieldScore> ranked = extractor.rankFields(context);

            // Determine injection level from book config
            String level = parseInjectionLevel(book.getExtraAttributes());
            ContextKeywordExtractor.LevelConfig config = ContextKeywordExtractor.getLevelConfig(level);

            // Read field weights from book config
            Map<String, Integer> fieldWeights = parseFieldWeights(book.getExtraAttributes());

            // Apply weights to ranking scores
            List<ContextKeywordExtractor.FieldScore> weighted = ranked.stream()
                .map(fs -> {
                    int w = fieldWeights.getOrDefault(fs.field(), 5);
                    return new ContextKeywordExtractor.FieldScore(fs.field(), fs.label(), fs.score() * w);
                })
                .sorted((a, b) -> b.score() - a.score())
                .toList();

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
                for (ContextKeywordExtractor.FieldScore fs : weighted) {
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

        // ── Vector search: similar chapter summaries ──
        if (context != null && !context.isBlank()) {
            try {
                List<ChapterEmbedding> similar = chapterEmbeddingService.findSimilar(bookId, context, 3);
                if (!similar.isEmpty()) {
                    sb.append("\n相关前文摘要（向量检索）：\n");
                    for (ChapterEmbedding e : similar) {
                        String text = e.getContentText();
                        if (text.length() > 300) text = text.substring(0, 297) + "...";
                        sb.append("- ").append(text).append("\n");
                    }
                }
            } catch (Exception e) {
                log.debug("Vector retrieval skipped: {}", e.getMessage());
            }
        }

        // ── Style profile (if analyzed) ──
        bookStyleProfileRepository.findByBookId(bookId).ifPresent(sp -> {
            if (sp.getStyleLabel() != null && !sp.getStyleLabel().isBlank()) {
                sb.append("\n作者风格标签：").append(sp.getStyleLabel()).append("\n");
            }
            if (sp.getChapterOpeningPattern() != null && !sp.getChapterOpeningPattern().isBlank()) {
                try {
                    var pattern = FIELD_WEIGHT_MAPPER.readTree(sp.getChapterOpeningPattern());
                    String text = pattern.isTextual() ? pattern.asText() : pattern.toString();
                    if (!text.isBlank() && text.length() < 100) {
                        sb.append("开篇习惯：").append(text).append("\n");
                    }
                } catch (Exception ignored) {}
            }
            if (sp.getWritingHabits() != null && !sp.getWritingHabits().isBlank()) {
                try {
                    var habits = FIELD_WEIGHT_MAPPER.readTree(sp.getWritingHabits());
                    sb.append("写作习惯：");
                    for (var h : habits) {
                        sb.append(h.asText()).append("；");
                    }
                    sb.append("\n");
                } catch (Exception ignored) {}
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

    private static int depthOrdinal(String level) {
        return switch (level) {
            case "L3" -> 3;
            case "L2" -> 2;
            case "L0" -> 0;
            default -> 1;
        };
    }

    private void appendPersonality(StringBuilder sb, CharacterDetail d, String theme) {
        StringBuilder chunk = new StringBuilder();
        if (d.getTalkativeness() != null && !d.getTalkativeness().isBlank())
            chunk.append("说话量：").append(d.getTalkativeness()).append("；");
        if (d.getSentenceStyle() != null && !d.getSentenceStyle().isBlank())
            chunk.append("句式风格：").append(d.getSentenceStyle()).append("；");
        if (d.getWordPreference() != null && !d.getWordPreference().isBlank())
            chunk.append("用词偏好：").append(d.getWordPreference()).append("；");
        if (d.getEmotionExpression() != null && !d.getEmotionExpression().isBlank())
            chunk.append("情感表达：").append(d.getEmotionExpression()).append("；");
        if (d.getActionStyle() != null && !d.getActionStyle().isBlank())
            chunk.append("行动风格：").append(d.getActionStyle()).append("；");
        if (d.getCoreDesire() != null && !d.getCoreDesire().isBlank())
            chunk.append("核心欲望：").append(d.getCoreDesire()).append("；");
        if (d.getDeepFear() != null && !d.getDeepFear().isBlank())
            chunk.append("深层恐惧：").append(d.getDeepFear()).append("；");
        if (d.getSurfaceGoal() != null && !d.getSurfaceGoal().isBlank())
            chunk.append("表层目标：").append(d.getSurfaceGoal()).append("；");
        if (d.getBottomLine() != null && !d.getBottomLine().isBlank())
            chunk.append("底线：").append(d.getBottomLine()).append("；");

        // Genre-specific personality dimensions from extra_attributes
        String genre = detectGenre(theme);
        String genrePersonality = buildGenrePersonality(d.getExtraAttributes(), genre);
        if (!genrePersonality.isBlank()) {
            chunk.append(genrePersonality);
        }

        if (chunk.isEmpty()) return;
        String text = chunk.toString();
        if (text.length() > 500) text = text.substring(0, 497) + "...";
        sb.append("  人格：").append(text).append("\n");
    }

    private String detectGenre(String theme) {
        if (theme == null || theme.isBlank()) return "general";
        String t = theme.toLowerCase();
        if (t.contains("玄幻") || t.contains("奇幻") || t.contains("魔幻") || t.contains("异界")) return "fantasy";
        if (t.contains("仙侠") || t.contains("修真") || t.contains("修仙") || t.contains("洪荒")) return "xianxia";
        if (t.contains("科幻") || t.contains("未来") || t.contains("星际") || t.contains("赛博")) return "scifi";
        if (t.contains("言情") || t.contains("恋爱") || t.contains("都市") || t.contains("现代")) return "romance";
        if (t.contains("悬疑") || t.contains("恐怖") || t.contains("灵异") || t.contains("惊悚")) return "suspense";
        if (t.contains("历史") || t.contains("古代") || t.contains("穿越")) return "historical";
        if (t.contains("武侠") || t.contains("江湖")) return "wuxia";
        return "general";
    }

    private String buildGenrePersonality(String extraAttributesJson, String genre) {
        if (extraAttributesJson == null || extraAttributesJson.isBlank()) return "";
        try {
            var root = FIELD_WEIGHT_MAPPER.readTree(extraAttributesJson);
            var gp = root.get("genre_personality");
            if (gp == null) return "";
            var g = gp.get(genre);
            if (g == null) return "";
            StringBuilder sb = new StringBuilder();
            var fields = g.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String val = entry.getValue().asText();
                if (val != null && !val.isBlank()) {
                    sb.append(entry.getKey()).append("：").append(val).append("；");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String parseInjectionLevel(String extraAttributesJson) {
        Map<String, String> attrs = extractor.parseExtraAttributes(extraAttributesJson);
        return attrs.getOrDefault("ai_injection_level", "STANDARD");
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper FIELD_WEIGHT_MAPPER
        = new com.fasterxml.jackson.databind.ObjectMapper();

    private Map<String, Integer> parseFieldWeights(String extraAttributesJson) {
        Map<String, Integer> defaults = Map.of(
            "era", 5, "geography", 5, "history_events", 5,
            "politics", 5, "economy", 5, "culture", 5,
            "military", 5, "core_rule", 5
        );
        if (extraAttributesJson == null || extraAttributesJson.isBlank()) return defaults;
        try {
            var root = FIELD_WEIGHT_MAPPER.readTree(extraAttributesJson);
            var weightsNode = root.get("ai_field_weights");
            if (weightsNode == null) return defaults;
            Map<String, Integer> result = new java.util.HashMap<>(defaults);
            var fields = weightsNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (entry.getValue().isInt()) {
                    int w = entry.getValue().asInt();
                    result.put(entry.getKey(), Math.max(0, Math.min(10, w)));
                }
            }
            return result;
        } catch (Exception e) {
            return defaults;
        }
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
