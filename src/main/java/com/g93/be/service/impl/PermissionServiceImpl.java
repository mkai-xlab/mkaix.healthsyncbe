package com.g93.be.service.impl;


import com.g93.be.entity.Feature;
import com.g93.be.entity.Permission;
import com.g93.be.entity.Role;
import com.g93.be.entity.RolePermission;
import com.g93.be.dto.FeatureResponse;
import com.g93.be.dto.PermissionResponse;
import com.g93.be.dto.UpdateRolePermissionsRequest;
import com.g93.be.dto.CreateFeatureRequest;
import com.g93.be.dto.UpdateFeatureRequest;
import com.g93.be.dto.CreatePermissionRequest;
import com.g93.be.dto.UpdatePermissionRequest;
import com.g93.be.entity.Feature;
import com.g93.be.entity.Permission;
import com.g93.be.entity.Role;
import com.g93.be.entity.RolePermission;
import com.g93.be.repository.FeatureRepository;
import com.g93.be.repository.PermissionRepository;
import com.g93.be.repository.RolePermissionRepository;
import com.g93.be.repository.RoleRepository;
import com.g93.be.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    private final FeatureRepository featureRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<FeatureResponse> getPermissionTree() {
        List<Feature> features = featureRepository.findAll();
        List<Permission> allPermissions = permissionRepository.findAll();

        return features.stream().map(feature -> {
            List<PermissionResponse> permissionResponses = allPermissions.stream()
                    .filter(p -> p.getFeature().getId().equals(feature.getId()))
                    .map(p -> new PermissionResponse(p.getId(), p.getCode(), p.getName(), p.getPriority(), p.getPresentation(),
                            p.getRequiresPermission() != null ? p.getRequiresPermission().getId() : null))
                    .sorted(Comparator.comparing(PermissionResponse::priority, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());
            
            return new FeatureResponse(feature.getId(), feature.getName(), feature.getDescription(), permissionResponses);
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getRolePermissions(String roleCode) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleCode));
        
        return rolePermissionRepository.findByRoleId(role.getId()).stream()
                .map(rp -> rp.getPermission().getId())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateRolePermissions(String roleCode, UpdateRolePermissionsRequest request) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleCode));

        // Delete existing permissions
        rolePermissionRepository.deleteByRoleId(role.getId());

        // Insert new permissions
        List<RolePermission> newPermissions = request.permissionIds().stream().map(permissionId -> {
            Permission permission = permissionRepository.findById(permissionId)
                    .orElseThrow(() -> new IllegalArgumentException("Permission not found with ID: " + permissionId));
            RolePermission rp = new RolePermission();
            rp.setRole(role);
            rp.setPermission(permission);
            return rp;
        }).collect(Collectors.toList());

        rolePermissionRepository.saveAll(newPermissions);
        log.info("Updated permissions for role: {}", roleCode);
    }

    @Override
    @Transactional
    public FeatureResponse createFeature(CreateFeatureRequest request) {
        if (featureRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Feature name already exists: " + request.name());
        }
        Feature feature = new Feature();
        feature.setName(request.name());
        feature.setDescription(request.description());
        feature = featureRepository.save(feature);
        return new FeatureResponse(feature.getId(), feature.getName(), feature.getDescription(), List.of());
    }

    @Override
    @Transactional
    public FeatureResponse updateFeature(Long id, UpdateFeatureRequest request) {
        Feature feature = featureRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Feature not found with ID: " + id));
        if (!feature.getName().equals(request.name()) && featureRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Feature name already exists: " + request.name());
        }
        feature.setName(request.name());
        feature.setDescription(request.description());
        final Feature savedFeature = featureRepository.save(feature);
        
        // Return existing permissions for the response
        List<PermissionResponse> permissionResponses = permissionRepository.findAll().stream()
                .filter(p -> p.getFeature().getId().equals(savedFeature.getId()))
                .map(p -> new PermissionResponse(p.getId(), p.getCode(), p.getName(), p.getPriority(), p.getPresentation(),
                        p.getRequiresPermission() != null ? p.getRequiresPermission().getId() : null))
                .sorted(Comparator.comparing(PermissionResponse::priority, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
                
        return new FeatureResponse(savedFeature.getId(), savedFeature.getName(), savedFeature.getDescription(), permissionResponses);
    }

    @Override
    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        if (permissionRepository.existsByCode(request.code())) {
            throw new IllegalArgumentException("Permission code already exists: " + request.code());
        }
        Feature feature = featureRepository.findById(request.featureId())
                .orElseThrow(() -> new IllegalArgumentException("Feature not found with ID: " + request.featureId()));
        
        Permission permission = new Permission();
        permission.setCode(request.code());
        permission.setName(request.name());
        permission.setPriority(request.priority() != null ? request.priority() : 1);
        permission.setPresentation(request.presentation());
        permission.setFeature(feature);
        
        if (request.requiresPermissionId() != null) {
            Permission reqPerm = permissionRepository.findById(request.requiresPermissionId())
                    .orElseThrow(() -> new IllegalArgumentException("Requires permission not found with ID: " + request.requiresPermissionId()));
            permission.setRequiresPermission(reqPerm);
        }
        
        permission = permissionRepository.save(permission);
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getName(), permission.getPriority(), permission.getPresentation(),
                permission.getRequiresPermission() != null ? permission.getRequiresPermission().getId() : null);
    }

    @Override
    @Transactional
    public PermissionResponse updatePermission(Long id, UpdatePermissionRequest request) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found with ID: " + id));
        if (!permission.getCode().equals(request.code()) && permissionRepository.existsByCode(request.code())) {
            throw new IllegalArgumentException("Permission code already exists: " + request.code());
        }
        permission.setCode(request.code());
        permission.setName(request.name());
        if (request.priority() != null) permission.setPriority(request.priority());
        permission.setPresentation(request.presentation());
        
        if (request.requiresPermissionId() != null) {
            Permission reqPerm = permissionRepository.findById(request.requiresPermissionId())
                    .orElseThrow(() -> new IllegalArgumentException("Requires permission not found with ID: " + request.requiresPermissionId()));
            // Prevent circular dependency (simple check)
            if (reqPerm.getId().equals(permission.getId())) {
                throw new IllegalArgumentException("Permission cannot require itself");
            }
            permission.setRequiresPermission(reqPerm);
        } else {
            permission.setRequiresPermission(null);
        }
        
        permission = permissionRepository.save(permission);
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getName(), permission.getPriority(), permission.getPresentation(),
                permission.getRequiresPermission() != null ? permission.getRequiresPermission().getId() : null);
    }
}
