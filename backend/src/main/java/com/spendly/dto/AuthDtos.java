package com.spendly.dto;

import com.spendly.domain.Role;
import com.spendly.validation.BcryptSafePassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8) @BcryptSafePassword String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            // Bounded so an oversized body can't be turned into BCrypt work.
            // Deliberately not @BcryptSafePassword: login should reject a wrong
            // password, not explain the hashing algorithm's limits.
            @NotBlank @Size(max = 200) String password
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 200) String currentPassword,
            @NotBlank @Size(min = 8) @BcryptSafePassword String newPassword
    ) {
    }

    /**
     * The access token and who it belongs to.
     *
     * <p>The refresh token is deliberately absent: it travels in an httpOnly
     * cookie, where script running in the page cannot read it. That is the whole
     * point of splitting the two — a cross-site script can steal what is in
     * {@code localStorage}, and what it steals now expires in fifteen minutes.
     *
     * <p>{@code expiresInSeconds} lets the client refresh before a request
     * fails, instead of discovering the expiry through a 401.
     */
    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            Long userId,
            String email,
            Role role
    ) {
        public static AuthResponse of(String token, long expiresInSeconds, Long userId, String email, Role role) {
            return new AuthResponse(token, "Bearer", expiresInSeconds, userId, email, role);
        }
    }
}
