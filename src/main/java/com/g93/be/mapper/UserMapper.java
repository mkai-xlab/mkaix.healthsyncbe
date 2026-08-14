package com.g93.be.mapper;

import com.g93.be.dto.UserResponse;
import com.g93.be.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse mapToResponse(User user) {
        if (user == null) {
            return null;
        }

        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus() != null ? user.getStatus().name() : null);
        response.setUserType(user.getUserType());
        if (user.getAvatar() != null) {
            response.setAvatarUrl(user.getAvatar().getFilePath());
        }
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());

        return response;
    }
}
