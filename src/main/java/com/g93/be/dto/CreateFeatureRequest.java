package com.g93.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFeatureRequest(
        @NotBlank(message = "Feature name is required")
        @Size(max = 100, message = "Feature name must not exceed 100 characters")
        String name,

        String description
) {}
