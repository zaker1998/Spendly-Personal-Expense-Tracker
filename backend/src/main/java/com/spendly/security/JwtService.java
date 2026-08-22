package com.spendly.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    /** HS256 needs a key at least as long as its output. */
    private static final int MIN_SECRET_BYTES = 32;

    static final String DEV_SECRET = "spendly-dev-secret-key-change-me-in-production-32chars-min";

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${spendly.jwt.secret:" + DEV_SECRET + "}") String secret,
            @Value("${spendly.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // Previously a short secret was zero-padded to 32 bytes, which silently
        // turned a weak secret into a weak key. Refuse to start instead.
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "spendly.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes (was " + keyBytes.length + "). Set the JWT_SECRET environment variable.");
        }
        if (DEV_SECRET.equals(secret)) {
            log.warn("Using the built-in development JWT secret. Set JWT_SECRET before deploying.");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .claims(Map.of(
                        "uid", principal.getId(),
                        "role", principal.getRole().name()
                ))
                .subject(principal.getUsername())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserPrincipal principal) {
        String username = extractUsername(token);
        return username.equalsIgnoreCase(principal.getUsername()) && !isExpired(token);
    }

    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
