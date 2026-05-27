package com.youmo.core.service.impl;

import com.youmo.core.service.TextMetricsService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TextMetricsServiceImpl implements TextMetricsService {

    private static final Pattern SENTENCE_END = Pattern.compile("[。！？.!?]");
    private static final Pattern WORD_CHAR = Pattern.compile("[一-鿿\\w]");
    private static final Pattern DIALOGUE_QUOTE = Pattern.compile("[“”‘’「」『』“”\"]");

    @Override
    public Map<String, Object> computeMetrics(String text) {
        Map<String, Object> metrics = new HashMap<>();
        if (text == null || text.isBlank()) {
            metrics.put("avg_sentence_length", 0.0);
            metrics.put("dialogue_ratio", 0.0);
            metrics.put("paragraph_style", "MEDIUM");
            metrics.put("description_action_ratio", 0.0);
            metrics.put("vocabulary_richness", 0.0);
            metrics.put("sentence_variety", 0.0);
            return metrics;
        }

        // Split into sentences
        String[] sentences = SENTENCE_END.split(text);
        int totalChars = 0;
        int sentenceCount = 0;
        int[] sentenceLengths = new int[sentences.length];
        for (int i = 0; i < sentences.length; i++) {
            String s = sentences[i].trim();
            int len = countChineseChars(s);
            if (len > 0) {
                totalChars += len;
                sentenceCount++;
                sentenceLengths[i] = len;
            }
        }

        double avgSentenceLength = sentenceCount > 0 ? (double) totalChars / sentenceCount : 0;

        // Dialogue ratio: characters inside quotes / total
        int dialogueChars = 0;
        boolean inQuote = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '“' || c == '「' || c == '『' || c == '‘' || c == '"') {
                inQuote = true;
            } else if (c == '”' || c == '」' || c == '』' || c == '’' || c == '"') {
                inQuote = false;
            } else if (inQuote && isChineseChar(c)) {
                dialogueChars++;
            }
        }
        double dialogueRatio = totalChars > 0 ? (double) dialogueChars / totalChars : 0;

        // Paragraph style
        String[] paragraphs = text.split("\\n\\s*\\n");
        int avgParaLen = 0;
        int paraCount = 0;
        for (String p : paragraphs) {
            int len = countChineseChars(p.trim());
            if (len > 0) { avgParaLen += len; paraCount++; }
        }
        avgParaLen = paraCount > 0 ? avgParaLen / paraCount : 0;
        String paragraphStyle = avgParaLen < 100 ? "SHORT" : avgParaLen > 300 ? "LONG" : "MEDIUM";

        // Description/action ratio: approximate by keyword counts
        int descWords = countMatches(text, "看|见|望|听|闻|感|觉|色|光|影|风|云|雾|气|香|味");
        int actionWords = countMatches(text, "打|杀|冲|跳|飞|跑|走|抓|拿|放|踢|砍|刺|劈|斩|射|击");
        double descActionRatio = actionWords > 0 ? (double) descWords / actionWords : 1.0;

        // Vocabulary richness: unique Chinese chars / total Chinese chars
        HashSet<Character> uniqueChars = new HashSet<>();
        int totalChinese = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChineseChar(c)) {
                uniqueChars.add(c);
                totalChinese++;
            }
        }
        double vocabRichness = totalChinese > 100 ? (double) uniqueChars.size() / totalChinese * 100 : 0;

        // Sentence variety: std deviation of sentence lengths / mean
        double sentenceVariety = 0;
        if (sentenceCount > 1 && avgSentenceLength > 0) {
            double sumSqDiff = 0;
            int validSentences = 0;
            for (int i = 0; i < sentenceLengths.length; i++) {
                if (sentenceLengths[i] > 0) {
                    double diff = sentenceLengths[i] - avgSentenceLength;
                    sumSqDiff += diff * diff;
                    validSentences++;
                }
            }
            double stdDev = Math.sqrt(sumSqDiff / validSentences);
            sentenceVariety = stdDev / avgSentenceLength;
        }

        metrics.put("avg_sentence_length", Math.round(avgSentenceLength * 10.0) / 10.0);
        metrics.put("dialogue_ratio", Math.round(dialogueRatio * 1000.0) / 1000.0);
        metrics.put("paragraph_style", paragraphStyle);
        metrics.put("description_action_ratio", Math.round(descActionRatio * 100.0) / 100.0);
        metrics.put("vocabulary_richness", Math.round(vocabRichness * 10.0) / 10.0);
        metrics.put("sentence_variety", Math.round(sentenceVariety * 100.0) / 100.0);

        return metrics;
    }

    private int countChineseChars(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (isChineseChar(s.charAt(i))) count++;
        }
        return count;
    }

    private boolean isChineseChar(char c) {
        return Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }

    private int countMatches(String text, String pattern) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (pattern.indexOf(text.charAt(i)) >= 0) count++;
        }
        return count;
    }
}
