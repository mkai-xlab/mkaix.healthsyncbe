package com.g93.be.config;

import com.g93.be.entity.Feature;
import com.g93.be.entity.Permission;
import com.g93.be.entity.Role;
import com.g93.be.entity.RolePermission;
import com.g93.be.entity.User;
import com.g93.be.repository.AdminRepository;
import com.g93.be.repository.FeatureRepository;
import com.g93.be.repository.PermissionRepository;
import com.g93.be.repository.RolePermissionRepository;
import com.g93.be.repository.RoleRepository;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private FeatureRepository featureRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DataInitializer dataInitializer;

    @Test
    void permissionCatalogClassifiesEverySeedPermission() {
        Set<String> classifiedCodes = new HashSet<>(PermissionCatalog.ADMIN_DEFAULT_PERMISSION_CODES);
        classifiedCodes.addAll(PermissionCatalog.CLINICAL_PERMISSION_CODES);

        assertEquals(PermissionCatalog.VIETNAMESE_NAMES.keySet(), classifiedCodes);
        assertEquals("Xem hồ sơ cá nhân",
                PermissionCatalog.VIETNAMESE_NAMES.get("READ_OWN_PROFILE"));
        assertEquals("Điều chỉnh phân độ KL của AI",
                PermissionCatalog.VIETNAMESE_NAMES.get("OVERRIDE_AI_GRADE"));
        assertEquals("Tạo báo cáo vận hành",
                PermissionCatalog.VIETNAMESE_NAMES.get("GENERATE_OPERATIONAL_REP"));
    }

    @Test
    void existingDatabaseGetsVietnameseNamesAndAdminClinicalPermissionsRemoved() throws Exception {
        Role adminRole = role(1L, "ADMIN");
        User adminUser = user(1L, "admin", adminRole);
        Permission adminPermission = permission(14L, "VIEW_ADMIN_DASHBOARD", "View admin dashboard");
        Permission clinicalPermission = permission(9L, "VIEW_AI_RESULT", "View AI result");
        RolePermission retainedAssignment = new RolePermission(1L, adminRole, adminPermission);
        RolePermission clinicalAssignment = new RolePermission(2L, adminRole, clinicalPermission);

        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(adminRole));
        when(permissionRepository.findAll()).thenReturn(List.of(adminPermission, clinicalPermission));
        when(rolePermissionRepository.findByRoleId(1L))
                .thenReturn(List.of(retainedAssignment, clinicalAssignment));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        dataInitializer.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Permission>> changedNames = ArgumentCaptor.forClass(List.class);
        verify(permissionRepository).saveAll(changedNames.capture());
        assertEquals(Set.of("Xem trang tổng quan quản trị", "Xem kết quả AI"),
                changedNames.getValue().stream().map(Permission::getName).collect(java.util.stream.Collectors.toSet()));
        verify(rolePermissionRepository).deleteAll(List.of(clinicalAssignment));
    }

    @Test
    void existingAdminReceivesMissingAdministrativeDefaults() throws Exception {
        Role adminRole = role(1L, "ADMIN");
        User adminUser = user(1L, "admin", adminRole);
        Permission adminPermission = permission(
                14L, "VIEW_ADMIN_DASHBOARD", "Xem trang tổng quan quản trị");

        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(adminRole));
        when(permissionRepository.findAll()).thenReturn(List.of(adminPermission));
        when(rolePermissionRepository.findByRoleId(1L)).thenReturn(List.of());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        dataInitializer.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RolePermission>> addedAssignments = ArgumentCaptor.forClass(List.class);
        verify(rolePermissionRepository).saveAll(addedAssignments.capture());
        assertEquals(List.of("VIEW_ADMIN_DASHBOARD"), addedAssignments.getValue().stream()
                .map(rolePermission -> rolePermission.getPermission().getCode())
                .toList());
    }

    @Test
    void clinicalRolesReceiveChatAndKnowledgeManagement() throws Exception {
        Role adminRole = role(1L, "ADMIN");
        Role doctorRole = role(2L, "DOCTOR");
        User adminUser = user(1L, "admin", adminRole);
        Feature feature = new Feature();
        Permission useChat = permission(23L, "USE_AI_CHAT", "Su dung tro ly AI");
        Permission manageKnowledge = permission(
                24L, "MANAGE_MEDICAL_KNOWLEDGE", "Quan ly kho tri thuc y khoa");
        RolePermission adminChat = new RolePermission(1L, adminRole, useChat);
        RolePermission adminManagement = new RolePermission(2L, adminRole, manageKnowledge);

        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(doctorRole));
        when(featureRepository.findByName("AI Chatbox & Medical Knowledge"))
                .thenReturn(Optional.of(feature));
        when(permissionRepository.findByCode("USE_AI_CHAT")).thenReturn(Optional.of(useChat));
        when(permissionRepository.findByCode("MANAGE_MEDICAL_KNOWLEDGE"))
                .thenReturn(Optional.of(manageKnowledge));
        when(permissionRepository.findAll()).thenReturn(List.of(useChat, manageKnowledge));
        when(rolePermissionRepository.findByRoleId(1L))
                .thenReturn(List.of(adminChat, adminManagement));
        when(rolePermissionRepository.findByRoleId(2L)).thenReturn(List.of());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        dataInitializer.run();

        ArgumentCaptor<RolePermission> assignment = ArgumentCaptor.forClass(RolePermission.class);
        verify(rolePermissionRepository, times(2)).save(assignment.capture());
        assertEquals(Set.of("USE_AI_CHAT", "MANAGE_MEDICAL_KNOWLEDGE"),
                assignment.getAllValues().stream()
                        .map(saved -> saved.getPermission().getCode())
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("DOCTOR"),
                assignment.getAllValues().stream()
                        .map(saved -> saved.getRole().getCode())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        return role;
    }

    private User user(Long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    private Permission permission(Long id, String code, String name) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setCode(code);
        permission.setName(name);
        return permission;
    }
}
