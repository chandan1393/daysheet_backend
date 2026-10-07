package com.daysheet.repository;

import com.daysheet.domain.ClientDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientDocumentRepository extends JpaRepository<ClientDocument, Long> {

    List<ClientDocument> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<ClientDocument> findByAppointmentIdOrderByCreatedAtAsc(Long appointmentId);

    Optional<ClientDocument> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("select coalesce(sum(d.sizeBytes), 0) from ClientDocument d where d.workspace.id = :workspaceId")
    long totalBytes(@Param("workspaceId") Long workspaceId);

    /** Keeps files on the person's record when the visit they were attached to is deleted. */
    @Modifying
    @Query("update ClientDocument d set d.appointment = null where d.appointment.id = :appointmentId")
    int unlinkAppointment(@Param("appointmentId") Long appointmentId);
}
