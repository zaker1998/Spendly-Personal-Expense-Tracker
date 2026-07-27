package com.spendly.dto;

import com.spendly.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
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
