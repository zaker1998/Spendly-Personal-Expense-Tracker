package com.spendly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 16) String color
    ) {
    }

    public record CategoryResponse(
            Long id,
            String name,
            String color,
            Instant createdAt
    ) {
    }
}
