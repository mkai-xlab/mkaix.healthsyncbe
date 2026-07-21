package com.g93.be.service.impl;


import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.entity.UserStatus;
import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.CreateUserRequest;
import com.g93.be.dto.UserResponse;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.entity.UserStatus;
import com.g93.be.repository.RoleRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailUtil mailUtil;

    @Value("${app.login-url:http://localhost:3000/login}")
    private String loginUrl;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Creating user with email: {}", request.getEmail());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email is already registered");
        }
        
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            if (userRepository.findByPhone(request.getPhone()).isPresent()) {
                throw new IllegalArgumentException("Phone '" + request.getPhone() + "' is already registered");
            }
        }

        // Validate role
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if ("ADMIN".equalsIgnoreCase(role.getCode())) {
            throw new IllegalArgumentException("Cannot create a user with the ADMIN role via this endpoint");
        }

        // Generate credentials
        String tempUsername = generateUniqueUsername(request.getEmail());
        String tempPassword = generateSecurePassword();

        // Map and create user
        User user = new User();
        user.setUsername(tempUsername);
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(role);
        
        // Use the role name as the userType if needed, or leave it generic.
        user.setUserType(role.getCode());
        user.setStatus(UserStatus.ACTIVE);
        user.setIsFirstActivated(true);

        User savedUser = userRepository.save(user);
        
        log.info("User created successfully with ID: {}", savedUser.getId());
        
        // Send email notification
        sendWelcomeEmail(savedUser, tempPassword);
        
        return mapToResponse(savedUser);
    }

    private String generateUniqueUsername(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9._]", "").toLowerCase();
        if (base.isBlank()) {
            base = "user";
        }
        String username = base;
        int suffix = 1;
        while (userRepository.findByUsername(username).isPresent()) {
            username = base + suffix;
            suffix++;
        }
        return username;
    }

    private String generateSecurePassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specials = "!@#$%^&*";
        String all = upper + lower + digits + specials;

        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        sb.append(specials.charAt(random.nextInt(specials.length())));

        for (int i = 4; i < 12; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }

        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            char temp = chars[index];
            chars[index] = chars[i];
            chars[i] = temp;
        }

        return new String(chars);
    }

    private void sendWelcomeEmail(User user, String rawPassword) {
        try {
            Map<String, Object> variables = Map.of(
                    "fullName", user.getFullName(),
                    "username", user.getUsername(),
                    "password", rawPassword,
                    "loginUrl", loginUrl);

            mailUtil.sendTemplateMail(
                    user.getEmail(),
                    "Welcome to HealthSync - Your Account Credentials",
                    "doctor-welcome",
                    variables);
            log.info("Welcome email sent successfully to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}", user.getEmail(), e);
        }
    }

    private UserResponse mapToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus().name());
        response.setUserType(user.getUserType());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }
}
