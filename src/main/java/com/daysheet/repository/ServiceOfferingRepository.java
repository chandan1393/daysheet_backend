package com.daysheet.repository;

import com.daysheet.domain.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {
    List<ServiceOffering> findByWorkspaceIdAndActiveTrueOrderByNameAsc(Long workspaceId);
    List<ServiceOffering> findByWorkspaceIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc(Long workspaceId);
    Optional<ServiceOffering> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
