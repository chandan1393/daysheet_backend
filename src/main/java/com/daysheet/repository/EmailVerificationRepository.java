package com.daysheet.repository;

import com.daysheet.domain.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(Long userId);

    Optional<EmailVerification> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<EmailVerification> findByTokenHash(String tokenHash);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    @Modifying
    @Query("update EmailVerification v set v.usedAt = :now where v.user.id = :userId and v.usedAt is null")
    int invalidateAll(@Param("userId") Long userId, @Param("now") Instant now);
}
