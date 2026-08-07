package com.spendly.service;

import com.spendly.config.CacheConfig;
import java.time.LocalDate;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Evicts exactly the summary entry affected by an expense write instead of
 * clearing the whole cache, keeping other users' (and other months') cached
 * summaries warm.
 */
@Component
public class SummaryCacheEvictor {

    private final CacheManager cacheManager;

    public SummaryCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictMonth(Long userId, LocalDate spentOn) {
        if (userId == null || spentOn == null) {
            return;
        }
        Cache cache = cacheManager.getCache(CacheConfig.MONTHLY_SUMMARY_CACHE);
        if (cache != null) {
            cache.evict(userId + ":" + spentOn.getYear() + ":" + spentOn.getMonthValue());
        }
    }
}
