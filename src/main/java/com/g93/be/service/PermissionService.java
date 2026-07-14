package com.g93.be.service;

import com.g93.be.dto.FeatureResponse;
import com.g93.be.dto.UpdateRolePermissionsRequest;
import java.util.List;

public interface PermissionService {
    List<FeatureResponse> getPermissionTree();
    List<Long> getRolePermissions(String roleName);
    void updateRolePermissions(String roleName, UpdateRolePermissionsRequest request);
    
    FeatureResponse createFeature(com.g93.be.dto.CreateFeatureRequest request);
    FeatureResponse updateFeature(Long id, com.g93.be.dto.UpdateFeatureRequest request);
    
    com.g93.be.dto.PermissionResponse createPermission(com.g93.be.dto.CreatePermissionRequest request);
    com.g93.be.dto.PermissionResponse updatePermission(Long id, com.g93.be.dto.UpdatePermissionRequest request);
}
