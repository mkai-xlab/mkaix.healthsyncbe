package com.g93.be.dto;

import java.util.List;

public record FeatureResponse(
    Long id,
    String name,
    String description,
    List<PermissionResponse> permissions
) {}
