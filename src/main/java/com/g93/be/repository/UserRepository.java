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
}

