package com.daysheet.repository;

import com.daysheet.domain.PackageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PackageTemplateRepository extends JpaRepository<PackageTemplate, Long> {
    List<PackageTemplate> findByWorkspaceIdAndActiveTrueOrderByNameAsc(Long workspaceId);
    Optional<PackageTemplate> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
