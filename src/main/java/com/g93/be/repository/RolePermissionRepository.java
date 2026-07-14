package com.g93.be.repository;

import com.g93.be.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRoleId(Long roleId);
    void deleteByRoleId(Long roleId);
    
    @Query("SELECT rp.permission.code FROM RolePermission rp WHERE rp.role.code = :roleCode")
    List<String> findPermissionCodesByRoleCode(@Param("roleCode") String roleCode);
}
