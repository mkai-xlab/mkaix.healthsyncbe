package com.g93.be.controller;

import com.g93.be.dto.CreateUserRequest;
import com.g93.be.dto.UserResponse;
import com.g93.be.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Controller for managing user-related operations.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * Creates a generic user.
     * Restricts assignment of the ADMIN role.
     *
     * @param request The user creation payload.
     * @return The created UserResponse.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Received request to register a new user: {}", request.getEmail());
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    /**
     * Retrieves the list of medical staff (Doctors, Head of Departments).
     *
     * @return List of UserResponse.
     */
    @org.springframework.web.bind.annotation.GetMapping("/staff")
    @PreAuthorize("hasAnyRole('HEAD_OF_DEPARTMENT', 'DEPARTMENT_HEAD', 'ADMIN')")
    public ResponseEntity<java.util.List<UserResponse>> getStaffList() {
        log.info("Received request to fetch medical staff list");
        java.util.List<UserResponse> staffList = userService.getStaffList();
        return ResponseEntity.ok(staffList);
    }

    @org.springframework.web.bind.annotation.GetMapping("/count/doctors")
    public ResponseEntity<Long> countDoctors(java.security.Principal principal) {
        log.info("Received request to count doctors by user: {}", principal.getName());
        return ResponseEntity.ok(userService.countDoctors(principal.getName()));
    }

    @org.springframework.web.bind.annotation.GetMapping("/count/heads")
    public ResponseEntity<Long> countHeads(java.security.Principal principal) {
        log.info("Received request to count heads of department by user: {}", principal.getName());
        return ResponseEntity.ok(userService.countHeads(principal.getName()));
    }
}
