package com.g93.be.repository;

import com.g93.be.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
    boolean existsByCode(String code);
    List<Permission> findByFeatureId(Long featureId);

    @Modifying
    @Query("UPDATE Permission p SET p.requiresPermission = null WHERE p.requiresPermission.id = :permissionId")
    int clearRequiredPermissionReferences(@Param("permissionId") Long permissionId);
}
