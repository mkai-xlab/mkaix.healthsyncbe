package com.g93.be.security;

import com.g93.be.dto.PermissionResponse;

import com.g93.be.entity.User;
import com.g93.be.repository.UserRepository;
import com.g93.be.repository.RolePermissionRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public CustomUserDetailsService(UserRepository userRepository, RolePermissionRepository rolePermissionRepository) {
        this.userRepository = userRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username or email: " + usernameOrEmail));
        java.util.List<com.g93.be.entity.Permission> perms = rolePermissionRepository
                .findPermissionsByRoleCode(user.getRole().getCode());
        java.util.List<PermissionResponse> permissions = perms.stream()
                .map(p -> new PermissionResponse(p.getId(), p.getCode(), p.getName(), p.getPriority(),
                        p.getPresentation(),
                        p.getRequiresPermission() != null ? p.getRequiresPermission().getId() : null))
                .collect(java.util.stream.Collectors.toList());
        return new CustomUserDetails(user, permissions);
    }
}
