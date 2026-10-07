package com.daysheet.repository;

import com.daysheet.domain.ClientNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientNoteRepository extends JpaRepository<ClientNote, Long> {

    List<ClientNote> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<ClientNote> findByAppointmentIdOrderByCreatedAtAsc(Long appointmentId);

    Optional<ClientNote> findByIdAndWorkspaceId(Long id, Long workspaceId);

    /** Keeps notes on the person's record when the visit they were written for is deleted. */
    @Modifying
    @Query("update ClientNote n set n.appointment = null where n.appointment.id = :appointmentId")
    int unlinkAppointment(@Param("appointmentId") Long appointmentId);
}
