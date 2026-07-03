package com.g93.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
        @NotBlank(message = "Permission code is required")
        @Size(max = 100, message = "Permission code must not exceed 100 characters")
        String code,

        String name,

        Integer priority,

        String presentation,

        @NotNull(message = "Feature ID is required")
        Long featureId,

        Long requiresPermissionId
) {}
