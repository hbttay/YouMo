package com.youmo.core.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Extracts keywords from novel context and matches them against
 * world-setting fields to decide which fields are relevant for injection.
 */
@Slf4j
@Component
public class ContextKeywordExtractor {

    // ── Field → trigger keywords mapping ──
    private static final Map<String, List<String>> FIELD_KEYWORDS = new LinkedHashMap<>();
    static {
        FIELD_KEYWORDS.put("era", List.of("年代", "世纪", "古代", "现代", "未来", "修仙时代", "末世", "上古", "太古", "远古", "朝代", "年间"));
        FIELD_KEYWORDS.put("geography", List.of("山脉", "河流", "沙漠", "森林", "城池", "大陆", "海域", "洞穴", "山谷", "平原", "冰原", "沼泽", "岛屿", "峡谷"));
        FIELD_KEYWORDS.put("history_events", List.of("大战", "事变", "灾难", "王朝", "陨落", "崛起", "封印", "浩劫", "入侵", "覆灭", "统一", "分裂", "叛乱"));
        FIELD_KEYWORDS.put("politics", List.of("帝国", "王国", "皇族", "朝廷", "权力", "势力", "门派", "联盟", "皇帝", "国王", "宗主", "长老", "官府", "诸侯"));
        FIELD_KEYWORDS.put("economy", List.of("货币", "灵石", "交易", "商队", "资源", "灵矿", "拍卖行", "金币", "银两", "商会", "钱庄", "贸易", "灵草"));
        FIELD_KEYWORDS.put("military", List.of("军队", "战争", "武器", "剑", "功法", "阵法", "防线", "攻城", "骑兵", "步兵", "弓箭", "铠甲", "出征", "血战", "厮杀"));
        FIELD_KEYWORDS.put("culture", List.of("信仰", "宗教", "祭祀", "节日", "习俗", "禁忌", "礼仪", "神殿", "庙宇", "僧侣", "道士", "巫术", "图腾"));
        FIELD_KEYWORDS.put("core_rule", List.of("修炼", "等级", "境界", "筑基", "金丹", "元婴", "渡劫", "功法", "灵气", "丹田", "神识", "法术", "神通", "飞升"));
    }

    private static final Pattern CHINESE_PATTERN = Pattern.compile("[一-鿿]{2,4}");
    private static final Pattern SEGMENT_SPLIT = Pattern.compile("[\n。，,；;！!？?…]+");

    public record FieldScore(String field, String label, int score) {}

    /**
     * Rank world-setting fields by relevance to the given context.
     * Returns fields sorted by score (highest first).
     */
    public List<FieldScore> rankFields(String context) {
        if (context == null || context.isBlank()) {
            return List.of();
        }

        Set<String> contextKeywords = extractKeywords(context);
        if (contextKeywords.isEmpty()) {
            return List.of();
        }

        List<FieldScore> scores = new ArrayList<>();
        for (var entry : FIELD_KEYWORDS.entrySet()) {
            int score = 0;
            for (String kw : entry.getValue()) {
                if (contextKeywords.contains(kw)) {
                    score += 2; // exact match
                } else {
                    // substring match (e.g. "筑基丹" contains "筑基" doesn't work,
                    // but context keyword "筑基" matching trigger word "筑基" works)
                    for (String ck : contextKeywords) {
                        if (kw.contains(ck) || ck.contains(kw)) {
                            score += 1;
                        }
                    }
                }
            }
            if (score > 0) {
                scores.add(new FieldScore(entry.getKey(), fieldLabel(entry.getKey()), score));
            }
        }

        scores.sort((a, b) -> b.score() - a.score());
        return scores;
    }

    /**
     * Extract Chinese keywords from text via segment → ngram → deduplicate.
     */
    public Set<String> extractKeywords(String text) {
        Set<String> result = new HashSet<>();
        String[] segments = SEGMENT_SPLIT.split(text);
        for (String seg : segments) {
            seg = seg.strip();
            if (seg.isEmpty()) continue;
            var matcher = CHINESE_PATTERN.matcher(seg);
            while (matcher.find()) {
                result.add(matcher.group());
            }
        }
        return result;
    }

    // Pattern for "key":"value" pairs in flat JSON objects
    private static final Pattern JSON_KV = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /**
     * Parse extra_attributes JSONB as a flat map of custom field → content.
     * Uses simple regex — avoids pulling in Jackson as a dependency for youmo-core.
     */
    public Map<String, String> parseExtraAttributes(String extraAttributesJson) {
        if (extraAttributesJson == null || extraAttributesJson.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        var matcher = JSON_KV.matcher(extraAttributesJson);
        while (matcher.find()) {
            String key = matcher.group(1);
            String val = matcher.group(2)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\\\", "\\");
            if (!val.isBlank()) {
                result.put(key, val);
            }
        }
        return result;
    }

    private static String fieldLabel(String field) {
        return switch (field) {
            case "era" -> "时代";
            case "geography" -> "地理";
            case "history_events" -> "历史";
            case "politics" -> "政治";
            case "economy" -> "经济";
            case "military" -> "军事";
            case "culture" -> "文化";
            case "core_rule" -> "核心规则";
            default -> field;
        };
    }

    // ── Injection level config ──

    public record LevelConfig(int maxFields, int maxChars, String label) {}

    public static LevelConfig getLevelConfig(String level) {
        return switch (level != null ? level.toUpperCase() : "STANDARD") {
            case "MINIMAL" -> new LevelConfig(4, 200, "精简");
            case "DETAILED" -> new LevelConfig(99, 1000, "详细");
            default -> new LevelConfig(8, 500, "标准");
        };
    }
}
