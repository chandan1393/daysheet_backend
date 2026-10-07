package com.daysheet.repository;

import com.daysheet.domain.Prescription;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    Optional<Prescription> findByIdAndWorkspaceId(Long id, Long workspaceId);

    List<Prescription> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<Prescription> findByAppointmentIdOrderByCreatedAtAsc(Long appointmentId);

    /** Medicines this practice has prescribed before, for quick typing. */
    @Query("""
            select i.medicine from PrescriptionItem i
            where i.prescription.workspace.id = :ws and lower(i.medicine) like concat(:q, '%')
            group by i.medicine order by count(i) desc
            """)
    List<String> medicineSuggestions(@Param("ws") Long workspaceId, @Param("q") String prefix, Pageable page);

    @Modifying
    @Query("update Prescription p set p.appointment = null where p.appointment.id = :appointmentId")
    int unlinkAppointment(@Param("appointmentId") Long appointmentId);
}
