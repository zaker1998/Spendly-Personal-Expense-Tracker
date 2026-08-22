package com.spendly.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-process Caffeine cache for the monthly summary aggregation.
 *
 * Caffeine was chosen over Redis deliberately: the app runs as a single
 * instance (Render free tier), so a distributed cache would add infrastructure
 * without benefit. The short TTL bounds staleness; writes additionally evict
 * the affected user's entries (see ExpenseService).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String MONTHLY_SUMMARY_CACHE = "monthlySummary";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(MONTHLY_SUMMARY_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                // recordStats() is what lets Boot publish cache hit/miss/eviction
                // counters to /actuator/prometheus; without it the meters read zero.
                .recordStats());
        return manager;
    }
}
