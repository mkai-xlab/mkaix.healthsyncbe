package com.g93.be.repository;

import com.g93.be.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRoleId(Long roleId);
    void deleteByRoleId(Long roleId);
    
    @Query("SELECT rp.permission.name FROM RolePermission rp WHERE rp.role.name = :roleName")
    List<String> findPermissionNamesByRoleName(@Param("roleName") String roleName);
}
