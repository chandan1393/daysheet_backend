package com.daysheet.repository;

import com.daysheet.domain.Hearing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HearingRepository extends JpaRepository<Hearing, Long> {

    @Query("select h from Hearing h where h.id = :id and h.legalCase.workspace.id = :ws")
    Optional<Hearing> findForWorkspace(@Param("id") Long id, @Param("ws") Long workspaceId);

    @Query("""
            select h from Hearing h join fetch h.legalCase c join fetch c.client
            where c.workspace.id = :ws and c.status = 'OPEN' and h.hearingDate >= :from and h.hearingDate <= :to
            order by h.hearingDate
            """)
    List<Hearing> upcoming(@Param("ws") Long workspaceId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
