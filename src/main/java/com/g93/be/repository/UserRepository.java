package com.g93.be.repository;

import com.g93.be.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.g93.be.entity.UserStatus;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
       Optional<User> findByEmail(String email);

       Optional<User> findByUsername(String username);

       Optional<User> findByUsernameOrEmail(String username, String email);

       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("select u from User u where u.username = :identifier or u.email = :identifier")
       Optional<User> findByLoginIdentifierForUpdate(@Param("identifier") String identifier);

       Optional<User> findByPhone(String phone);

       List<User> findByRoleCodeIn(List<String> roleCodes);

       long countByRoleCode(String roleCode);

       /**
        * Tìm kiếm nhân viên với các bộ lọc
        */
       @Query("SELECT u FROM User u WHERE u.role.code IN :roles " +
                     "AND (:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                     "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                     "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                     "AND (:status IS NULL OR u.status = :status)")
       Page<User> searchStaff(@Param("roles") List<String> roles,
                     @Param("keyword") String keyword,
                     @Param("status") UserStatus status,
                     Pageable pageable);
}
