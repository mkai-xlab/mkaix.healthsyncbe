package com.g93.be.config;

import com.g93.be.entity.Admin;
import com.g93.be.entity.UserRole;
import com.g93.be.entity.UserStatus;
import com.g93.be.repository.AdminRepository;
import com.g93.be.repository.UserRepository;
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
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Kiểm tra nếu tài khoản admin chưa tồn tại thì khởi tạo
        if (userRepository.findByUsername("admin").isEmpty()) {
            Admin admin = new Admin();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin"));
            admin.setFullName("System Administrator");
            admin.setEmail("admin@healthsync.com");
            admin.setPhone("0123456789");
            admin.setRole(UserRole.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            admin.setIsFirstActivated(false); // Tài khoản hệ thống mặc định có thể bỏ qua bước kích hoạt lần đầu
            
            // Các trường riêng của entity Admin
            admin.setAdminCode("ADMIN_001");
            admin.setPosition("System Manager");

            adminRepository.save(admin);
            System.out.println(">>> Đã khởi tạo tài khoản admin mặc định (admin/admin)");
        }
    }
}
