package com.g93.be.service;
import com.g93.be.dto.CreateFeatureRequest;
import com.g93.be.dto.UpdateFeatureRequest;
import com.g93.be.dto.PermissionResponse;
import com.g93.be.dto.CreatePermissionRequest;
import com.g93.be.dto.UpdatePermissionRequest;


import com.g93.be.dto.FeatureResponse;
import com.g93.be.dto.UpdateRolePermissionsRequest;
import java.util.List;

public interface PermissionService {
    List<FeatureResponse> getPermissionTree();
    List<Long> getRolePermissions(String roleName);
    void updateRolePermissions(String roleName, UpdateRolePermissionsRequest request);
    
    FeatureResponse createFeature(CreateFeatureRequest request);
    FeatureResponse updateFeature(Long id, UpdateFeatureRequest request);
    void deleteFeature(Long id);
    
    PermissionResponse createPermission(CreatePermissionRequest request);
    PermissionResponse updatePermission(Long id, UpdatePermissionRequest request);
    void deletePermission(Long id);
}

