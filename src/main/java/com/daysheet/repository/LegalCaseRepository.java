package com.daysheet.repository;

import com.daysheet.domain.LegalCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LegalCaseRepository extends JpaRepository<LegalCase, Long> {

    Optional<LegalCase> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("select c from LegalCase c join fetch c.client where c.workspace.id = :ws order by c.createdAt desc")
    List<LegalCase> findAllForWorkspace(@Param("ws") Long workspaceId);

    List<LegalCase> findByClientIdOrderByCreatedAtDesc(Long clientId);
}
