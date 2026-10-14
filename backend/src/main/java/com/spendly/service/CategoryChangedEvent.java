package com.spendly.service;

/**
 * Raised when a category's name or colour changes.
 *
 * <p>The cached monthly summary carries each category's <em>name and colour</em>,
 * not just its id, so either change makes every cached month for that user
 * wrong — and unlike an expense write there is no single month to invalidate.
 * The whole user's summaries go, which is cheap: the cache is keyed per user
 * and month, and editing a category is rare.
 */
public record CategoryChangedEvent(Long userId) {
}
