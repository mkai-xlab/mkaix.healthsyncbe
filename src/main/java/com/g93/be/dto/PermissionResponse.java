package com.g93.be.dto;

public record PermissionResponse(
    Long id,
    String code,
    String name,
    Integer priority,
    String presentation,
    Long requiresPermissionId
) {}
