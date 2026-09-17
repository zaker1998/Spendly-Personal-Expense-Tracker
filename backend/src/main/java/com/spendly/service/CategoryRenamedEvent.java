package com.spendly.service;

/**
 * Raised when a category's name changes.
 *
 * <p>The cached monthly summary carries category <em>names</em>, not just ids,
 * so a rename makes every cached month for that user wrong — and unlike an
 * expense write there is no single month to invalidate. The whole user's
 * summaries go, which is cheap: the cache is keyed per user and month, and
 * renaming a category is rare.
 */
public record CategoryRenamedEvent(Long userId) {
}
