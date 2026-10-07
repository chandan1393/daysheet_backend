package com.daysheet.repository;

import com.daysheet.domain.Workspace;
import com.daysheet.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    Optional<Workspace> findBySlug(String slug);
    boolean existsBySlug(String slug);

    java.util.List<com.daysheet.domain.Workspace> findAllByOrderByCreatedAtDesc();

    long countByCreatedAtAfter(Instant after);

    @Query("select w from Workspace w where w.plan = :plan and w.trialEndsAt >= :from and w.trialEndsAt < :to")
    java.util.List<com.daysheet.domain.Workspace> trialsEndingBetween(@Param("plan") Plan plan,
                                                                     @Param("from") Instant from, @Param("to") Instant to);

    @Query("select w from Workspace w where w.plan = :plan and w.subscriptionEndsAt >= :from and w.subscriptionEndsAt < :to")
    java.util.List<com.daysheet.domain.Workspace> paidEndingBetween(@Param("plan") Plan plan,
                                                                   @Param("from") Instant from, @Param("to") Instant to);

    @Modifying
    @Query("""
            update Workspace w set w.plan = :expired
            where (w.plan = :trial and w.trialEndsAt < :now)
               or (w.plan = :active and w.subscriptionEndsAt < :now)
            """)
    int expireLapsed(@Param("now") Instant now, @Param("trial") Plan trial,
                     @Param("active") Plan active, @Param("expired") Plan expired);
}
