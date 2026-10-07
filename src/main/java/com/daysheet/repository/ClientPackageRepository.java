package com.daysheet.repository;

import com.daysheet.domain.ClientPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientPackageRepository extends JpaRepository<ClientPackage, Long> {
    List<ClientPackage> findByClientIdOrderByPurchasedOnDescIdDesc(Long clientId);
    Optional<ClientPackage> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
