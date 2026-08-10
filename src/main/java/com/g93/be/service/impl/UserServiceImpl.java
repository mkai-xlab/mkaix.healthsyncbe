package com.g93.be.service.impl;

import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.entity.UserStatus;
import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.CreateUserRequest;
import com.g93.be.dto.UpdateUserRoleRequest;
import com.g93.be.dto.UserResponse;
import com.g93.be.exception.ResourceNotFoundException;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private static final String MEDICAL_STAFF_USER_TYPE = "DOCTOR";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailUtil mailUtil;

    @Value("${app.login-url:http://localhost:3000/login}")
    private String loginUrl;

    @Override
    @Transactional
    @com.g93.be.aspect.LogAction("CREATE_USER")
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Creating user with email: {}", request.getEmail());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email is already registered");
        }

        if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new IllegalArgumentException("Phone '" + request.getPhone() + "' is already registered");
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

        // userType identifies the medical staff entity; role carries authorization.
        user.setUserType(role.getCode());
        user.setStatus(UserStatus.ACTIVE);
        user.setIsFirstActivated(true);

        User savedUser = userRepository.save(user);

        log.info("User created successfully with ID: {}", savedUser.getId());

        // Send email notification
        sendWelcomeEmail(savedUser, tempPassword);

        return mapToResponse(savedUser);
    }

    @Override
    @Transactional
    @com.g93.be.aspect.LogAction("UPDATE_USER_ROLE")
    public UserResponse updateUserRole(Long userId, UpdateUserRoleRequest request, String actorUsername) {
        if (request == null || request.roleId() == null) {
            throw new IllegalArgumentException("Role ID cannot be null");
        }

        User actor = actorUsername == null
                ? null
                : userRepository.findByUsername(actorUsername).orElse(null);
        if (actor == null || !hasRole(actor, "ADMIN")) {
            throw new AccessDeniedException("Only Admin can update user roles");
        }

        if (Objects.equals(actor.getId(), userId)) {
            throw new IllegalArgumentException("An admin cannot change their own role");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));
        if (hasRole(target, "ADMIN")) {
            throw new IllegalArgumentException("An admin role cannot be changed via this endpoint");
        }

        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role with id " + request.roleId() + " not found"));
        if (hasRole(role, "ADMIN")) {
            throw new IllegalArgumentException("Cannot assign the ADMIN role via this endpoint");
        }

        String previousRoleCode = target.getRole() == null ? null : target.getRole().getCode();
        target.setRole(role);
        User savedUser = userRepository.save(target);
        log.info("User {} role changed from {} to {} by {}", userId,
                previousRoleCode, role.getCode(), actorUsername);
        return mapToResponse(savedUser);
    }

    @Override
    public List<UserResponse> getStaffList() {
        log.info("Fetching medical staff list");
        List<User> staffUsers = userRepository
                .findByRoleCodeIn(List.of("HEAD_OF_DEPARTMENT", "DOCTOR"));
        return staffUsers.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public long countDoctors(String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String currentRole = currentUser.getRole().getCode();

        if (!currentRole.equals("ADMIN") && !currentRole.equals("HEAD_OF_DEPARTMENT")) {
            throw new AccessDeniedException("Only Admin or Head of Department can view the total number of doctors.");
        }

        return userRepository.countByRoleCode("DOCTOR");
    }

    @Override
    public long countHeads(String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!currentUser.getRole().getCode().equals("ADMIN")) {
            throw new AccessDeniedException("Only Admin can view the total number of heads of department.");
        }

        return userRepository.countByRoleCode("HEAD_OF_DEPARTMENT");
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

    private boolean hasRole(User user, String roleCode) {
        return user != null && user.getRole() != null && hasRole(user.getRole(), roleCode);
    }

    private boolean hasRole(Role role, String roleCode) {
        return role != null && role.getCode() != null
                && roleCode.equalsIgnoreCase(role.getCode().trim());
    }
}
