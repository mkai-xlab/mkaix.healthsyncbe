package com.g93.be.service;

import com.g93.be.dto.CreateFeatureRequest;
import com.g93.be.dto.CreatePermissionRequest;
import com.g93.be.dto.FeatureResponse;
import com.g93.be.dto.PermissionResponse;
import com.g93.be.dto.UpdateFeatureRequest;
import com.g93.be.dto.UpdatePermissionRequest;
import com.g93.be.dto.UpdateRolePermissionsRequest;
import com.g93.be.entity.Feature;
import com.g93.be.entity.Permission;
import com.g93.be.entity.Role;
import com.g93.be.entity.RolePermission;
import com.g93.be.repository.FeatureRepository;
import com.g93.be.repository.PermissionRepository;
import com.g93.be.repository.RolePermissionRepository;
import com.g93.be.repository.RoleRepository;
import com.g93.be.service.impl.PermissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private FeatureRepository featureRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    private Feature feature;
    private Permission viewPermission;

    @BeforeEach
    void setUp() {
        feature = new Feature(10L, "Reports", "Report management");
        viewPermission = new Permission(20L, "VIEW_REPORT", "View report", 2,
                "View", feature, null);
    }

    @Test
    void getPermissionTreeGroupsAndSortsPermissionsByPriority() {
        Permission createPermission = new Permission(21L, "CREATE_REPORT", "Create report", 1,
                "Create", feature, viewPermission);
        when(featureRepository.findAll()).thenReturn(List.of(feature));
        when(permissionRepository.findAll()).thenReturn(List.of(viewPermission, createPermission));

        List<FeatureResponse> result = permissionService.getPermissionTree();

        assertEquals(1, result.size());
        assertEquals("Reports", result.getFirst().name());
        assertEquals(List.of("CREATE_REPORT", "VIEW_REPORT"), result.getFirst().permissions().stream()
                .map(PermissionResponse::code).toList());
        assertEquals(20L, result.getFirst().permissions().getFirst().requiresPermissionId());
    }

    @Test
    void getRolePermissionsReturnsPermissionIds() {
        Role role = role(5L, "DOCTOR");
        Permission second = new Permission(22L, "EDIT_REPORT", "Edit report", 3,
                "Edit", feature, null);
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(role));
        when(rolePermissionRepository.findByRoleId(5L)).thenReturn(List.of(
                new RolePermission(1L, role, viewPermission),
                new RolePermission(2L, role, second)));

        assertEquals(List.of(20L, 22L), permissionService.getRolePermissions("DOCTOR"));
    }

    @Test
    void getRolePermissionsRejectsUnknownRole() {
        when(roleRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> permissionService.getRolePermissions("UNKNOWN"));

        assertEquals("Role not found: UNKNOWN", error.getMessage());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateRolePermissionsReplacesExistingAssignments() {
        Role role = role(5L, "DOCTOR");
        Permission second = new Permission(22L, "EDIT_REPORT", "Edit report", 3,
                "Edit", feature, null);
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(role));
        when(permissionRepository.findById(20L)).thenReturn(Optional.of(viewPermission));
        when(permissionRepository.findById(22L)).thenReturn(Optional.of(second));

        permissionService.updateRolePermissions("DOCTOR",
                new UpdateRolePermissionsRequest(List.of(20L, 22L)));

        verify(rolePermissionRepository).deleteByRoleId(5L);
        ArgumentCaptor<List<RolePermission>> captor = ArgumentCaptor.forClass(List.class);
        verify(rolePermissionRepository).saveAll(captor.capture());
        assertEquals(List.of(20L, 22L), captor.getValue().stream()
                .map(rolePermission -> rolePermission.getPermission().getId()).toList());
        assertEquals(List.of(role, role), captor.getValue().stream()
                .map(RolePermission::getRole).toList());
    }

    @Test
    void updateRolePermissionsRejectsMissingPermission() {
        Role role = role(5L, "DOCTOR");
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(role));
        when(permissionRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> permissionService.updateRolePermissions("DOCTOR",
                        new UpdateRolePermissionsRequest(List.of(999L))));

        assertEquals("Permission not found with ID: 999", error.getMessage());
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void createFeaturePersistsAndReturnsFeature() {
        when(featureRepository.existsByName("Reports")).thenReturn(false);
        when(featureRepository.save(any(Feature.class))).thenAnswer(invocation -> {
            Feature saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        FeatureResponse result = permissionService.createFeature(
                new CreateFeatureRequest("Reports", "Report management"));

        assertEquals(10L, result.id());
        assertEquals("Reports", result.name());
        assertEquals(List.of(), result.permissions());
    }

    @Test
    void createFeatureRejectsDuplicateName() {
        when(featureRepository.existsByName("Reports")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> permissionService.createFeature(
                new CreateFeatureRequest("Reports", "Duplicate")));
        verify(featureRepository, never()).save(any());
    }

    @Test
    void updateFeaturePersistsChangesAndReturnsExistingPermissions() {
        when(featureRepository.findById(10L)).thenReturn(Optional.of(feature));
        when(featureRepository.save(feature)).thenReturn(feature);
        when(permissionRepository.findAll()).thenReturn(List.of(viewPermission));

        FeatureResponse result = permissionService.updateFeature(10L,
                new UpdateFeatureRequest("Clinical reports", "Updated"));

        assertEquals("Clinical reports", result.name());
        assertEquals("Updated", result.description());
        assertEquals(List.of("VIEW_REPORT"), result.permissions().stream()
                .map(PermissionResponse::code).toList());
    }

    @Test
    void deleteFeatureRemovesPermissionRelationsBeforeFeature() {
        when(featureRepository.findById(10L)).thenReturn(Optional.of(feature));
        when(permissionRepository.findByFeatureId(10L)).thenReturn(List.of(viewPermission));

        permissionService.deleteFeature(10L);

        verify(permissionRepository).clearRequiredPermissionReferences(20L);
        verify(rolePermissionRepository).deleteByPermissionIdIn(List.of(20L));
        verify(permissionRepository).deleteAll(List.of(viewPermission));
        verify(featureRepository).delete(feature);
    }

    @Test
    void deleteFeatureRejectsUnknownFeature() {
        when(featureRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> permissionService.deleteFeature(999L));

        assertEquals("Feature not found with ID: 999", error.getMessage());
        verify(featureRepository, never()).delete(any());
    }

    @Test
    void createPermissionUsesDefaultPriorityAndOptionalRequirement() {
        when(permissionRepository.existsByCode("CREATE_REPORT")).thenReturn(false);
        when(featureRepository.findById(10L)).thenReturn(Optional.of(feature));
        when(permissionRepository.findById(20L)).thenReturn(Optional.of(viewPermission));
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> {
            Permission saved = invocation.getArgument(0);
            saved.setId(21L);
            return saved;
        });

        PermissionResponse result = permissionService.createPermission(
                new CreatePermissionRequest("CREATE_REPORT", "Create report", null,
                        "Create", 10L, 20L));

        assertEquals(21L, result.id());
        assertEquals(1, result.priority());
        assertEquals(20L, result.requiresPermissionId());
    }

    @Test
    void createPermissionRejectsDuplicateCode() {
        when(permissionRepository.existsByCode("VIEW_REPORT")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> permissionService.createPermission(
                new CreatePermissionRequest("VIEW_REPORT", "View", 1, "View", 10L, null)));
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void updatePermissionClearsRequirementAndPreservesPriorityWhenOmitted() {
        viewPermission.setRequiresPermission(new Permission());
        when(permissionRepository.findById(20L)).thenReturn(Optional.of(viewPermission));
        when(permissionRepository.save(viewPermission)).thenReturn(viewPermission);

        PermissionResponse result = permissionService.updatePermission(20L,
                new UpdatePermissionRequest("VIEW_REPORT", "View updated", null,
                        "View", null));

        assertEquals(2, result.priority());
        assertNull(result.requiresPermissionId());
        assertNull(viewPermission.getRequiresPermission());
    }

    @Test
    void updatePermissionRejectsSelfRequirement() {
        when(permissionRepository.findById(20L)).thenReturn(Optional.of(viewPermission));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> permissionService.updatePermission(20L,
                        new UpdatePermissionRequest("VIEW_REPORT", "View", 2,
                                "View", 20L)));

        assertEquals("Permission cannot require itself", error.getMessage());
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void deletePermissionRemovesRelationsBeforePermission() {
        when(permissionRepository.findById(20L)).thenReturn(Optional.of(viewPermission));

        permissionService.deletePermission(20L);

        verify(permissionRepository).clearRequiredPermissionReferences(20L);
        verify(rolePermissionRepository).deleteByPermissionId(20L);
        verify(permissionRepository).delete(viewPermission);
    }

    @Test
    void deletePermissionRejectsUnknownPermission() {
        when(permissionRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> permissionService.deletePermission(999L));

        assertEquals("Permission not found with ID: 999", error.getMessage());
        verify(permissionRepository, never()).delete(any());
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        return role;
    }
}
