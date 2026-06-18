package com.g93.be.controller;

import com.g93.be.dto.CreateFeatureRequest;
import com.g93.be.dto.FeatureResponse;
import com.g93.be.dto.UpdateFeatureRequest;
import com.g93.be.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/features")
@RequiredArgsConstructor
public class FeatureController {

    private final PermissionService permissionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureResponse> createFeature(@Valid @RequestBody CreateFeatureRequest request) {
        FeatureResponse response = permissionService.createFeature(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureResponse> updateFeature(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFeatureRequest request) {
        FeatureResponse response = permissionService.updateFeature(id, request);
        return ResponseEntity.ok(response);
    }
}
