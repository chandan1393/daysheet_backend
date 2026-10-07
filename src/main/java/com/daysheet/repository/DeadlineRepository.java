package com.daysheet.repository;

import com.daysheet.domain.Deadline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    Optional<Deadline> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("select d from Deadline d join fetch d.client where d.workspace.id = :ws order by d.dueDate, d.id")
    List<Deadline> findAllForWorkspace(@Param("ws") Long workspaceId);

    List<Deadline> findByClientIdOrderByDueDateDesc(Long clientId);

    @Query("""
            select d from Deadline d join fetch d.client
            where d.workspace.id = :ws and d.status = 'PENDING' and d.dueDate <= :until
            order by d.dueDate
            """)
    List<Deadline> pendingDueBy(@Param("ws") Long workspaceId, @Param("until") LocalDate until);
}
