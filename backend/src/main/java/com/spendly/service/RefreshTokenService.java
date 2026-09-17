package com.spendly.service;

import com.spendly.domain.RefreshToken;
import com.spendly.domain.User;
import com.spendly.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <p>The access token is a JWT and cannot be withdrawn once signed, so it is
 * deliberately short-lived (15 minutes) and the session is kept alive by this
 * instead. That is what makes "log out" and "log out everywhere" mean something
 * on the server rather than only in the browser's localStorage.
 *
 * <p>Tokens rotate on every use: a refresh hands back a new token and revokes
 * the one presented. A stolen token is therefore usable at most once, and
 * whichever party uses it second is rejected.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final long expirationMs;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            @Value("${spendly.refresh.expiration-ms:2592000000}") long expirationMs
    ) {
        this.repository = repository;
        this.expirationMs = expirationMs;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /** Returns the raw token. Only its hash is stored, so this is the only chance to read it. */
    @Transactional
    public String issue(User user) {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(hash(token));
        entity.setExpiresAt(Instant.now().plusMillis(expirationMs));
        repository.save(entity);
        return token;
    }

    /**
     * Exchanges a valid refresh token for a fresh one.
     *
     * @return the owning user and the replacement token, or empty when the
     *     presented token is unknown, expired or already used
     */
    @Transactional
    public Optional<Rotated> rotate(String presented) {
        if (presented == null || presented.isBlank()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return repository.findByTokenHash(hash(presented))
                .filter(existing -> {
                    if (existing.isUsable(now)) {
                        return true;
                    }
                    // Re-use of an already-rotated token is the signature of a
                    // stolen one being replayed, so it is worth a line in the log.
                    if (existing.getRevokedAt() != null) {
                        log.warn("Refresh token for user {} presented again after rotation",
                                existing.getUser().getId());
                    }
                    return false;
                })
                .map(existing -> {
                    existing.setRevokedAt(now);
                    User user = existing.getUser();
                    return new Rotated(user, issue(user));
                });
    }

    @Transactional
    public void revoke(String presented) {
        if (presented == null || presented.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(presented))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(Instant.now()));
    }

    /** Ends every session for a user — used by "log out everywhere" and password change. */
    @Transactional
    public int revokeAllForUser(Long userId) {
        return repository.revokeAllForUser(userId, Instant.now());
    }

    /**
     * Expired rows are dead weight; nothing reads them and the table would
     * otherwise grow for the life of the deployment.
     */
    @Scheduled(cron = "${spendly.refresh.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpiredBefore(Instant.now());
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }

    /**
     * SHA-256 rather than BCrypt: the token is 32 bytes from a CSPRNG, so there
     * is no guessable input to slow an attacker down over, and the lookup is on
     * the hash — it has to be deterministic.
     */
    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JRE", e);
        }
    }

    public record Rotated(User user, String token) {
    }
}
