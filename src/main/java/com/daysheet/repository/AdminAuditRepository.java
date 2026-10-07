package com.daysheet.repository;

import com.daysheet.domain.AdminAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditRepository extends JpaRepository<AdminAudit, Long> {
    List<AdminAudit> findTop100ByOrderByCreatedAtDesc();
    List<AdminAudit> findTop15ByOrderByCreatedAtDesc();
}
