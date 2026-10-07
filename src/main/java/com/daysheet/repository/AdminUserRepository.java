package com.daysheet.repository;

import com.daysheet.domain.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByActiveTrue();
    List<AdminUser> findAllByOrderByCreatedAtAsc();
}
