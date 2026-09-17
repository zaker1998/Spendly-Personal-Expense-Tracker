package com.spendly.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Per-account lockout after repeated failed logins.
 *
 * <p>{@link RateLimitFilter} already limits attempts per IP, but that is the
 * wrong axis on its own: an attacker with a handful of hosts gets a fresh bucket
 * from each one while hammering a single account. This counts the other way
 * round — per email, wherever the attempts come from.
 *
 * <p>In memory, for the same reason the rate limiter is: the application runs as
 * a single instance. Two instances would each keep their own count and the
 * effective limit would multiply; that trade is recorded in ENGINEERING_NOTES.
 *
 * <p>Failures are logged at WARN whether or not they trip the lock, because
 * "somebody is being brute-forced" was previously invisible in both the logs and
 * the metrics.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /** Bound on the map, so a flood of made-up addresses cannot grow it without limit. */
    private static final int MAX_TRACKED_ACCOUNTS = 10_000;

    private final boolean enabled;
    private final int maxAttempts;
    private final Duration lockout;
    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${spendly.login-protection.enabled:true}") boolean enabled,
            @Value("${spendly.login-protection.max-attempts:8}") int maxAttempts,
            @Value("${spendly.login-protection.lockout-minutes:15}") int lockoutMinutes
    ) {
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
        this.lockout = Duration.ofMinutes(lockoutMinutes);
    }

    /** Seconds left on the lock, or 0 when the account is not locked. */
    public long lockedForSeconds(String email) {
        if (!enabled) {
            return 0;
        }
        Attempts current = attempts.get(key(email));
        if (current == null || current.count < maxAttempts) {
            return 0;
        }
        long remaining = Duration.between(Instant.now(), current.lastFailure.plus(lockout)).toSeconds();
        return Math.max(remaining, 0);
    }

    public void recordFailure(String email, String clientIp) {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();
        String key = key(email);
        Attempts updated = attempts.compute(key, (k, existing) ->
                existing == null || existing.isStale(now, lockout)
                        ? new Attempts(1, now)
                        : new Attempts(existing.count + 1, now));

        if (updated.count >= maxAttempts) {
            log.warn("Login locked for {} after {} failed attempts (last from {})",
                    key, updated.count, clientIp);
        } else {
            log.warn("Failed login for {} from {} ({}/{})", key, clientIp, updated.count, maxAttempts);
        }
        evictStale(now);
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    private void evictStale(Instant now) {
        if (attempts.size() > MAX_TRACKED_ACCOUNTS) {
            attempts.values().removeIf(a -> a.isStale(now, lockout));
        }
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempts(int count, Instant lastFailure) {
        boolean isStale(Instant now, Duration window) {
            return lastFailure.plus(window).isBefore(now);
        }
    }
}
