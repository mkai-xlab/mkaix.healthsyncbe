package com.g93.be.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "Role ID cannot be null")
        Long roleId) {
}
