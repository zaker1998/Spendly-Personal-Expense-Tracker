package com.spendly.service;

import com.spendly.config.CacheConfig;
import java.time.LocalDate;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts exactly the summary entry affected by an expense write instead of
 * clearing the whole cache, keeping other users' (and other months') cached
 * summaries warm.
 *
 * The eviction runs after commit. Evicting inline during the write left a window
 * where a concurrent read of the same month could repopulate the cache from the
 * uncommitted state and then serve those stale totals for the rest of the TTL.
 */
@Component
public class SummaryCacheEvictor {

    private final CacheManager cacheManager;

    public SummaryCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSummaryChanged(SummaryChangedEvent event) {
        evictMonth(event.userId(), event.spentOn());
    }

    void evictMonth(Long userId, LocalDate spentOn) {
        if (userId == null || spentOn == null) {
            return;
        }
        Cache cache = cacheManager.getCache(CacheConfig.MONTHLY_SUMMARY_CACHE);
        if (cache != null) {
            cache.evict(userId + ":" + spentOn.getYear() + ":" + spentOn.getMonthValue());
        }
    }
}
