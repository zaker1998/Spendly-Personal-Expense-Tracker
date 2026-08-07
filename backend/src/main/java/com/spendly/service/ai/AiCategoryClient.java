package com.spendly.service.ai;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction over the LLM provider so the suggestion logic can be unit-tested
 * without network access and the provider can be swapped without touching
 * business code.
 */
public interface AiCategoryClient {

    boolean isEnabled();

    /**
     * Asks the model to pick the best matching category name for the given
     * expense description. Returns empty if the client is disabled or the call
     * fails for any reason; callers are expected to fall back gracefully.
     */
    Optional<String> pickCategory(String description, List<String> categoryNames);
}
