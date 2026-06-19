package com.g93.be.dto;

public record PermissionResponse(
    Long id,
    String name,
    String description,
    Long requiresPermissionId
) {}
