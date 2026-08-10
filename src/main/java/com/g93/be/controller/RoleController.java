package com.g93.be.controller;

import com.g93.be.dto.RoleDto;
import com.g93.be.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleRepository roleRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RoleDto>> getAllRoles() {
        List<RoleDto> roles = roleRepository.findAll().stream()
                .map(role -> new RoleDto(role.getId(), role.getCode(), role.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(roles);
    }
}
