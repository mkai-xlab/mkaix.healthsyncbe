package com.g93.be.service;

import com.g93.be.dto.CreateUserRequest;
import com.g93.be.dto.UserResponse;

public interface UserService {
    /**
     * Creates a new generic user.
     * Restricts assignment of the ADMIN role.
     *
     * @param request the request containing user details and role ID
     * @return the created UserResponse
     */
    UserResponse createUser(CreateUserRequest request);
    java.util.List<UserResponse> getStaffList();
    long countDoctors(String username);
    long countHeads(String username);
}
