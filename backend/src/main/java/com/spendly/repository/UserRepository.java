package com.spendly.repository;

import com.spendly.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Written as explicit JPQL rather than as a derived {@code ...IgnoreCase}
     * method, because the derived form generates {@code upper(email) = upper(?)}
     * and the index added in V4 is on {@code lower(email)}. With the wrong case
     * function this lookup is a sequential scan — and it runs on every
     * authenticated request, not just at login.
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);
}
