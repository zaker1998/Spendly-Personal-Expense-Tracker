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
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) @BcryptSafePassword String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            // Bounded so an oversized body can't be turned into BCrypt work.
            // Deliberately not @BcryptSafePassword: login should reject a wrong
            // password, not explain the hashing algorithm's limits.
            @NotBlank @Size(max = 200) String password
    ) {
    }

    public record AuthResponse(
            String accessToken,
            String tokenType,
            Long userId,
            String email,
            Role role
    ) {
        public static AuthResponse of(String token, Long userId, String email, Role role) {
            return new AuthResponse(token, "Bearer", userId, email, role);
        }
    }
}
