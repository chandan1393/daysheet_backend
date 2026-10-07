package com.daysheet.repository;

import com.daysheet.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("""
            select c from Client c
            where c.workspace.id = :ws and c.archived = false
              and (:q = '' or lower(c.fullName) like concat('%', :q, '%')
                   or lower(coalesce(c.email, '')) like concat('%', :q, '%')
                   or coalesce(c.phone, '') like concat('%', :q, '%'))
            order by c.fullName
            """)
    List<Client> search(@Param("ws") Long workspaceId, @Param("q") String query);

    long countByWorkspaceIdAndArchivedFalse(Long workspaceId);

    /** Rows of [workspaceId, clientCount] for the admin practice list. */
    @Query("select c.workspace.id, count(c) from Client c where c.archived = false group by c.workspace.id")
    List<Object[]> countPerWorkspace();

    long countByWorkspaceIdAndArchivedFalseAndCreatedAtAfter(Long workspaceId, Instant after);

    Optional<Client> findFirstByWorkspaceIdAndEmailIgnoreCase(Long workspaceId, String email);
}
