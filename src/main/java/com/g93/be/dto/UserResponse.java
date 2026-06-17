package com.g93.be.dto;

import com.g93.be.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for responding with user details.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private Role role;
    private String status;
    private String userType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
