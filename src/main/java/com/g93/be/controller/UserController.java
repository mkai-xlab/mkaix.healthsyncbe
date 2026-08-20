package com.g93.be.controller;

import com.g93.be.dto.CreateUserRequest;
import com.g93.be.dto.UpdateUserRoleRequest;
import com.g93.be.dto.UserResponse;
import com.g93.be.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PatchMapping;
import com.g93.be.dto.ToggleStatusRequest;
import com.g93.be.entity.UserStatus;

import java.security.Principal;
import java.util.List;

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
     * Changes a non-admin user's role. Admin accounts cannot be changed through
     * this endpoint.
     *
     * @param userId    The user whose role is being changed.
     * @param request   The target role payload.
     * @param principal The authenticated administrator.
     * @return The user with the updated role.
     */
    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRoleRequest request,
            Principal principal) {
        log.info("Received request to update role for user ID: {}", userId);
        UserResponse response = userService.updateUserRole(userId, request,
                principal == null ? null : principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('HEAD_OF_DEPARTMENT', 'ADMIN')")
    public ResponseEntity<List<UserResponse>> getStaffList() {
        log.info("Received request to fetch medical staff list");
        List<UserResponse> staffList = userService.getStaffList();
        return ResponseEntity.ok(staffList);
    }

    @GetMapping("/staff/search")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('VIEW_USER_LIST') or hasRole('HEAD_OF_DEPARTMENT')")
    public ResponseEntity<Page<UserResponse>> searchStaff(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("Received request to search medical staff");
        Page<UserResponse> staffPage = userService.searchStaff(keyword, status, page,
                size);
        return ResponseEntity.ok(staffPage);
    }

    @PatchMapping("/{userId}/status/toggle")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('UPDATE_USER')")
    public ResponseEntity<UserResponse> toggleUserStatus(
            @PathVariable Long userId,
            @RequestBody(required = false) ToggleStatusRequest request,
            Principal principal) {
        log.info("Received request to toggle status for user ID: {}", userId);
        UserResponse response = userService.toggleUserStatus(userId, request,
                principal == null ? null : principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count/doctors")
    @PreAuthorize("hasAnyRole('HEAD_OF_DEPARTMENT', 'ADMIN')")
    public ResponseEntity<Long> countDoctors(Principal principal) {
        log.info("Received request to count doctors by user: {}", principal.getName());
        return ResponseEntity.ok(userService.countDoctors(principal.getName()));
    }

    @GetMapping("/count/heads")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> countHeads(Principal principal) {
        log.info("Received request to count heads of department by user: {}", principal.getName());
        return ResponseEntity.ok(userService.countHeads(principal.getName()));
    }
}
