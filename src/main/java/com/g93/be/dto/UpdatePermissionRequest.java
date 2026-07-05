package com.g93.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePermissionRequest(
        @NotBlank(message = "Permission code is required")
        @Size(max = 100, message = "Permission code must not exceed 100 characters")
        String code,

        String name,

        Integer priority,

        String presentation,

        Long requiresPermissionId
) {}
