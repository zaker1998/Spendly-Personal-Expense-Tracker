package com.spendly.dto;

import com.spendly.domain.Role;
import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(
            Long id,
            String email,
            Role role,
            Instant createdAt
    ) {
    }
}
