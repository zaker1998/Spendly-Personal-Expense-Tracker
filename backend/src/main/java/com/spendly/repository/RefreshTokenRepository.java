package com.spendly.repository;

import com.spendly.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** The user is always needed alongside the token, so fetch it in one query. */
    @EntityGraph(attributePaths = {"user"})
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Used by logout-everywhere and by the password change flow. */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /**
     * Rows are only useful until they expire; without this the table grows for
     * the life of the deployment.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
