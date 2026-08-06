package com.g93.be.config;

import com.g93.be.entity.*;
import com.g93.be.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Khởi tạo dữ liệu mẫu cho hệ thống.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final RoleRepository roleRepository;
    private final FeatureRepository featureRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        System.out.println("Migrating old NEED_REVERIFY statuses to NEED_VERIFY...");
        jdbcTemplate.update("UPDATE examinations SET status = 'NEED_VERIFY' WHERE status = 'NEED_REVERIFY'");
        System.out.println("Migration completed.");

        // 1. Initialize Dynamic Roles and Permissions FIRST
        if (roleRepository.findByCode("ADMIN").isEmpty()) {
            Role adminRole = new Role(null, "ADMIN", "System Administrator", null, null);
            Role doctorRole = new Role(null, "DOCTOR", "Medical Doctor", null, null);
            Role headOfDepartmentRole = new Role(null, "HEAD_OF_DEPARTMENT", "Head of Department", null, null);
            roleRepository.saveAll(java.util.List.of(adminRole, doctorRole, headOfDepartmentRole));

            // Create Features
            Feature fUser = new Feature(null, "User & Account Management", "Quản lý Tài khoản");
            Feature fPatient = new Feature(null, "Patient & Exam Management", "Quản lý Bệnh nhân & Ca khám");
            Feature fDicom = new Feature(null, "DICOM & Image Management", "Quản lý Hình ảnh/Tệp phim");
            Feature fAi = new Feature(null, "AI Analysis & XAI Visualization", "Chẩn đoán AI & Bản đồ nhiệt");
            Feature fHist = new Feature(null, "Clinical History & Trend", "Lịch sử & Diễn tiến");
            Feature fRev = new Feature(null, "Doctor Review & Decision", "Bác sĩ Đánh giá & Chốt");
            Feature fRep = new Feature(null, "Reporting & Export", "Lập báo cáo kết quả");
            Feature fDashDoc = new Feature(null, "Clinical Analytics Dashboard", "Dashboard Bác sĩ");
            Feature fDashAdm = new Feature(null, "System Admin Dashboard", "Dashboard Quản trị");
            featureRepository.saveAll(java.util.List.of(fUser, fPatient, fDicom, fAi, fHist, fRev, fRep, fDashDoc, fDashAdm));

            // Create Base Permissions (No Dependencies)
            Permission pAuth01 = new Permission(null, "READ_OWN_PROFILE", permissionName("READ_OWN_PROFILE"), 7, null, fUser, null);
            Permission pAuth02 = new Permission(null, "REQUEST_PASSWORD_RESET", permissionName("REQUEST_PASSWORD_RESET"), 5, null, fUser, null);
            Permission pAuth03 = new Permission(null, "VIEW_USER_LIST", permissionName("VIEW_USER_LIST"), 3, "user_list_page", fUser, null);
            
            Permission pPat01 = new Permission(null, "READ_PATIENT_LIST", permissionName("READ_PATIENT_LIST"), 2, "patient_list_page", fPatient, null);
            Permission pPat03 = new Permission(null, "CREATE_PATIENT_EXAM", permissionName("CREATE_PATIENT_EXAM"), 3, "examination_list_page", fPatient, null);
            
            Permission pImg01 = new Permission(null, "VIEW_IMAGE_LIST", permissionName("VIEW_IMAGE_LIST"), 3, null, fDicom, null);
            Permission pImg02 = new Permission(null, "UPLOAD_DICOM_IMAGE", permissionName("UPLOAD_DICOM_IMAGE"), 2, "file_upload_page", fDicom, null);
            
            Permission pAi01 = new Permission(null, "TRIGGER_AI_ANALYSIS", permissionName("TRIGGER_AI_ANALYSIS"), 8, null, fAi, null);
            Permission pAi02 = new Permission(null, "VIEW_AI_RESULT", permissionName("VIEW_AI_RESULT"), 9, null, fAi, null);
            
            Permission pHist01 = new Permission(null, "VIEW_ANALYTIC_HISTORY", permissionName("VIEW_ANALYTIC_HISTORY"), 10, null, fHist, null);
            
            Permission pRev01 = new Permission(null, "VIEW_PENDING_DIAGNOSIS", permissionName("VIEW_PENDING_DIAGNOSIS"), 11, null, fRev, null);
            
            Permission pRep01 = new Permission(null, "GENERATE_PDF_REPORT", permissionName("GENERATE_PDF_REPORT"), 12, null, fRep, null);
            
            Permission pDash01 = new Permission(null, "VIEW_DOCTOR_DASHBOARD", permissionName("VIEW_DOCTOR_DASHBOARD"), 1, "doctor_dashboard_page", fDashDoc, null);
            Permission pAdm01 = new Permission(null, "VIEW_ADMIN_DASHBOARD", permissionName("VIEW_ADMIN_DASHBOARD"), 14, null, fDashAdm, null);

            permissionRepository.saveAll(java.util.List.of(
                    pAuth01, pAuth02, pAuth03, pPat01, pPat03, pImg01, pImg02,
                    pAi01, pAi02, pHist01, pRev01, pRep01, pDash01, pAdm01
            ));

            // Create Dependent Permissions
            Permission pAuth04 = new Permission(null, "MANAGE_USER_ROLE", permissionName("MANAGE_USER_ROLE"), 15, null, fUser, pAuth03);
            Permission pPat02 = new Permission(null, "VIEW_PATIENT_DETAIL", permissionName("VIEW_PATIENT_DETAIL"), 2, "patient_detail_page", fPatient, pPat01);
            Permission pAi03 = new Permission(null, "COMPARE_XAI_SIDE_BY_SIDE", permissionName("COMPARE_XAI_SIDE_BY_SIDE"), 17, null, fAi, pAi02);
            Permission pRev02 = new Permission(null, "ADD_CLINICAL_COMMENT", permissionName("ADD_CLINICAL_COMMENT"), 18, null, fRev, pRev01);
            Permission pRev03 = new Permission(null, "OVERRIDE_AI_GRADE", permissionName("OVERRIDE_AI_GRADE"), 19, null, fRev, pRev01);
            Permission pRev04 = new Permission(null, "CONFIRM_CONCLUSION", permissionName("CONFIRM_CONCLUSION"), 20, null, fRev, pRev01);
            Permission pRep02 = new Permission(null, "EXPORT_DOWNLOAD_PDF", permissionName("EXPORT_DOWNLOAD_PDF"), 21, null, fRep, pRep01);
            Permission pAdm02 = new Permission(null, "GENERATE_OPERATIONAL_REP", permissionName("GENERATE_OPERATIONAL_REP"), 22, null, fDashAdm, pAdm01);

            permissionRepository.saveAll(java.util.List.of(
                    pAuth04, pPat02, pAi03, pRev02, pRev03, pRev04, pRep02, pAdm02
            ));

            // Keep VIEW_IMAGE_LIST at seed ID 6 while linking it to VIEW_PATIENT_DETAIL (seed ID 16).
            pImg01.setRequiresPermission(pPat02);
            permissionRepository.save(pImg01);

            // Bind to Roles
            java.util.List<RolePermission> rps = new java.util.ArrayList<>();
            // Admin only receives account and system-administration permissions.
            for (Permission permission : permissionRepository.findAll()) {
                if (PermissionCatalog.ADMIN_DEFAULT_PERMISSION_CODES.contains(permission.getCode())) {
                    rps.add(new RolePermission(null, adminRole, permission));
                }
            }
            List<Permission> medicalPermissions = List.of(
                    pAuth01, pAuth02, pPat01, pPat02, pPat03, pImg01, pImg02,
                    pAi01, pAi02, pAi03, pHist01, pRev01, pRev02, pRev03,
                    pRev04, pRep01, pRep02, pDash01);
            for (Role medicalRole : List.of(doctorRole, headOfDepartmentRole)) {
                medicalPermissions.forEach(permission ->
                        rps.add(new RolePermission(null, medicalRole, permission)));
            }

            rolePermissionRepository.saveAll(rps);

            System.out.println(">>> Đã khởi tạo Dynamic Roles và Permissions mặc định");
        }

        synchronizePermissionNames();
        synchronizeAdminPermissions();
        ensureHeadOfDepartmentRole();

        // 2. Kiểm tra nếu tài khoản admin chưa tồn tại thì khởi tạo
        if (userRepository.findByUsername("admin").isEmpty()) {
            Admin admin = new Admin();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin12345"));
            admin.setFullName("System Administrator");
            admin.setEmail("admin@healthsync.com");
            admin.setPhone("0123456789");

            Role adminRole = roleRepository.findByCode("ADMIN")
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not found"));
            admin.setRole(adminRole);

            admin.setStatus(UserStatus.ACTIVE);
            admin.setIsFirstActivated(false);

            adminRepository.save(admin);
            System.out.println(">>> Đã khởi tạo tài khoản admin mặc định (admin/admin)");
        }
    }

    private Role getOrCreateRole(String name) {
        return roleRepository.findByCode(name)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setCode(name);
                    r.setName(name);
                    return roleRepository.save(r);
                });
    }

    private String permissionName(String code) {
        return PermissionCatalog.VIETNAMESE_NAMES.get(code);
    }

    private void synchronizePermissionNames() {
        List<Permission> changedPermissions = new ArrayList<>();
        for (Permission permission : permissionRepository.findAll()) {
            String vietnameseName = PermissionCatalog.VIETNAMESE_NAMES.get(permission.getCode());
            if (vietnameseName != null && !Objects.equals(vietnameseName, permission.getName())) {
                permission.setName(vietnameseName);
                changedPermissions.add(permission);
            }
        }
        if (!changedPermissions.isEmpty()) {
            permissionRepository.saveAll(changedPermissions);
        }
    }

    private void synchronizeAdminPermissions() {
        Role adminRole = roleRepository.findByCode("ADMIN").orElse(null);
        if (adminRole == null) {
            return;
        }

        List<RolePermission> currentAssignments = rolePermissionRepository.findByRoleId(adminRole.getId());
        List<RolePermission> clinicalAssignments = currentAssignments.stream()
                .filter(rolePermission -> PermissionCatalog.isClinical(rolePermission.getPermission().getCode()))
                .toList();
        if (!clinicalAssignments.isEmpty()) {
            rolePermissionRepository.deleteAll(clinicalAssignments);
        }

        Set<String> retainedCodes = new HashSet<>();
        currentAssignments.stream()
                .filter(rolePermission -> !clinicalAssignments.contains(rolePermission))
                .map(rolePermission -> rolePermission.getPermission().getCode())
                .forEach(retainedCodes::add);

        List<RolePermission> missingDefaults = permissionRepository.findAll().stream()
                .filter(permission -> PermissionCatalog.ADMIN_DEFAULT_PERMISSION_CODES.contains(permission.getCode()))
                .filter(permission -> !retainedCodes.contains(permission.getCode()))
                .map(permission -> new RolePermission(null, adminRole, permission))
                .toList();
        if (!missingDefaults.isEmpty()) {
            rolePermissionRepository.saveAll(missingDefaults);
        }
    }

    private void ensureHeadOfDepartmentRole() {
        Role headOfDepartmentRole = roleRepository.findByCode("HEAD_OF_DEPARTMENT")
                .orElseGet(() -> roleRepository.save(
                        new Role(null, "HEAD_OF_DEPARTMENT", "Head of Department", null, null)));

        if (rolePermissionRepository.findByRoleId(headOfDepartmentRole.getId()).isEmpty()) {
            Role doctorRole = roleRepository.findByCode("DOCTOR").orElse(null);
            if (doctorRole != null) {
                List<RolePermission> doctorPermissions = rolePermissionRepository.findByRoleId(doctorRole.getId())
                        .stream()
                        .map(rolePermission -> new RolePermission(
                                null, headOfDepartmentRole, rolePermission.getPermission()))
                        .toList();
                rolePermissionRepository.saveAll(doctorPermissions);
            }
        }
    }
}
