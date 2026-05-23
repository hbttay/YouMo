package com.youmo.core.service;

public interface PromptAssemblyService {

    /**
     * Assemble a dynamic system prompt for AI continuation,
     * injecting character cards, world settings, and style info
     * from the specified book.
     *
     * @param bookId the book to pull metadata from
     * @param basePrompt the base writing instructions (injected by caller, not stored in code)
     * @return composed system prompt string
     */
    String buildContinuePrompt(Long bookId, String basePrompt);
}
