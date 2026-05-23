package com.youmo.api.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads AI prompts from external classpath files ({@code youmo-prompts/*.txt}).
 * Falls back to minimal built-in defaults when the prompt directory is absent
 * (e.g. public clone without the proprietary prompt files).
 */
@Slf4j
@Component
public class PromptConfig {

    private static final String PROMPTS_DIR = "classpath:youmo-prompts/*.txt";

    @Getter
    private final Map<String, String> prompts = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources(PROMPTS_DIR);
            for (Resource r : resources) {
                String name = r.getFilename();
                if (name != null && name.endsWith(".txt")) {
                    String key = name.replace(".txt", "");
                    String content = r.getContentAsString(StandardCharsets.UTF_8).strip();
                    prompts.put(key, content);
                    log.info("Loaded prompt '{}' from file ({} chars)", key, content.length());
                }
            }
            if (prompts.isEmpty()) {
                log.warn("No prompt files found in youmo-prompts/ — using built-in fallbacks");
            }
        } catch (IOException e) {
            log.warn("Failed to scan youmo-prompts/ directory — using built-in fallbacks", e);
        }
    }

    /**
     * Returns the prompt for the given key, or the provided fallback.
     */
    public String get(String key, String fallback) {
        return prompts.getOrDefault(key, fallback);
    }
}
