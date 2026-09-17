package com.spendly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    /**
     * {@code color} is rendered straight into a style binding in the SPA, so it
     * is constrained to the one shape that means anything there. Length alone
     * used to be the only check, which let any 16-character string through.
     */
    public record CategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a hex colour such as #2A9D8F") String color,
            Long version
    ) {
    }

    public record CategoryResponse(
            Long id,
            String name,
            String color,
            Long version,
            Instant createdAt
    ) {
    }
}
