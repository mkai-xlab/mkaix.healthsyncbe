package com.g93.be.dto;

import com.g93.be.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Data Transfer Object representing doctor details returned to the client.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private String role;
    private UserStatus status;
    private Integer yearsOfExperience;
    private String degree;
    private String biography;
    private String inactiveReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
