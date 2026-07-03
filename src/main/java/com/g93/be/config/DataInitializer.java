package com.g93.be.config;

import com.g93.be.entity.*;
import com.g93.be.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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

    @Override
    public void run(String... args) throws Exception {
        // 1. Initialize Dynamic Roles and Permissions FIRST
        if (roleRepository.findByCode("ADMIN").isEmpty()) {
            Role adminRole = new Role(null, "ADMIN", "System Administrator", null, null);
            Role doctorRole = new Role(null, "DOCTOR", "Medical Doctor", null, null);
            roleRepository.saveAll(java.util.List.of(adminRole, doctorRole));

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
            Permission pAuth01 = new Permission(null, "READ_OWN_PROFILE", "Read own profile", 1, null, fUser, null);
            Permission pAuth02 = new Permission(null, "REQUEST_PASSWORD_RESET", "Request password reset", 2, null, fUser, null);
            Permission pAuth03 = new Permission(null, "VIEW_USER_LIST", "View user list", 3, null, fUser, null);
            
            Permission pPat01 = new Permission(null, "READ_PATIENT_LIST", "Read patient list", 4, null, fPatient, null);
            Permission pPat03 = new Permission(null, "CREATE_PATIENT_EXAM", "Create patient exam", 5, null, fPatient, null);
            
            Permission pImg01 = new Permission(null, "VIEW_IMAGE_LIST", "View image list", 6, null, fDicom, null);
            Permission pImg02 = new Permission(null, "UPLOAD_DICOM_IMAGE", "Upload DICOM image", 7, null, fDicom, null);
            
            Permission pAi01 = new Permission(null, "TRIGGER_AI_ANALYSIS", "Trigger AI analysis", 8, null, fAi, null);
            Permission pAi02 = new Permission(null, "VIEW_AI_RESULT", "View AI result", 9, null, fAi, null);
            
            Permission pHist01 = new Permission(null, "VIEW_ANALYTIC_HISTORY", "View analytic history", 10, null, fHist, null);
            
            Permission pRev01 = new Permission(null, "VIEW_PENDING_DIAGNOSIS", "View pending diagnosis", 11, null, fRev, null);
            
            Permission pRep01 = new Permission(null, "GENERATE_PDF_REPORT", "Generate PDF report", 12, null, fRep, null);
            
            Permission pDash01 = new Permission(null, "VIEW_DOCTOR_DASHBOARD", "View doctor dashboard", 13, null, fDashDoc, null);
            Permission pAdm01 = new Permission(null, "VIEW_ADMIN_DASHBOARD", "View admin dashboard", 14, null, fDashAdm, null);

            permissionRepository.saveAll(java.util.List.of(
                    pAuth01, pAuth02, pAuth03, pPat01, pPat03, pImg01, pImg02,
                    pAi01, pAi02, pHist01, pRev01, pRep01, pDash01, pAdm01
            ));

            // Create Dependent Permissions
            Permission pAuth04 = new Permission(null, "MANAGE_USER_ROLE", "Manage user role", 15, null, fUser, pAuth03);
            Permission pPat02 = new Permission(null, "VIEW_PATIENT_DETAIL", "View patient detail", 16, null, fPatient, pPat01);
            Permission pAi03 = new Permission(null, "COMPARE_XAI_SIDE_BY_SIDE", "Compare XAI side by side", 17, null, fAi, pAi02);
            Permission pRev02 = new Permission(null, "ADD_CLINICAL_COMMENT", "Add clinical comment", 18, null, fRev, pRev01);
            Permission pRev03 = new Permission(null, "OVERRIDE_AI_GRADE", "Override AI grade", 19, null, fRev, pRev01);
            Permission pRev04 = new Permission(null, "CONFIRM_CONCLUSION", "Confirm conclusion", 20, null, fRev, pRev01);
            Permission pRep02 = new Permission(null, "EXPORT_DOWNLOAD_PDF", "Export download PDF", 21, null, fRep, pRep01);
            Permission pAdm02 = new Permission(null, "GENERATE_OPERATIONAL_REP", "Generate operational report", 22, null, fDashAdm, pAdm01);

            permissionRepository.saveAll(java.util.List.of(
                    pAuth04, pPat02, pAi03, pRev02, pRev03, pRev04, pRep02, pAdm02
            ));

            // Bind to Roles
            java.util.List<RolePermission> rps = new java.util.ArrayList<>();
            // Admin gets all
            for (Permission p : permissionRepository.findAll()) {
                rps.add(new RolePermission(null, adminRole, p));
            }
            // Doctor gets specific ones
            rps.add(new RolePermission(null, doctorRole, pAuth01));
            rps.add(new RolePermission(null, doctorRole, pAuth02));
            rps.add(new RolePermission(null, doctorRole, pPat01));
            rps.add(new RolePermission(null, doctorRole, pPat02));
            rps.add(new RolePermission(null, doctorRole, pPat03));
            rps.add(new RolePermission(null, doctorRole, pImg01));
            rps.add(new RolePermission(null, doctorRole, pImg02));
            rps.add(new RolePermission(null, doctorRole, pAi01));
            rps.add(new RolePermission(null, doctorRole, pAi02));
            rps.add(new RolePermission(null, doctorRole, pAi03));
            rps.add(new RolePermission(null, doctorRole, pHist01));
            rps.add(new RolePermission(null, doctorRole, pRev01));
            rps.add(new RolePermission(null, doctorRole, pRev02));
            rps.add(new RolePermission(null, doctorRole, pRev03));
            rps.add(new RolePermission(null, doctorRole, pRev04));
            rps.add(new RolePermission(null, doctorRole, pRep01));
            rps.add(new RolePermission(null, doctorRole, pRep02));
            rps.add(new RolePermission(null, doctorRole, pDash01));

            rolePermissionRepository.saveAll(rps);

            System.out.println(">>> Đã khởi tạo Dynamic Roles và Permissions mặc định");
        }

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
}
