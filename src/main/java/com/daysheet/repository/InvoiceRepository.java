package com.daysheet.repository;

import com.daysheet.domain.Invoice;
import com.daysheet.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("""
            select i from Invoice i join fetch i.client
            where i.workspace.id = :ws
            order by i.issueDate desc, i.id desc
            """)
    List<Invoice> findAllForWorkspace(@Param("ws") Long workspaceId);

    @Query("select i from Invoice i where i.client.id = :clientId order by i.issueDate desc")
    List<Invoice> findByClient(@Param("clientId") Long clientId);

    long countByWorkspaceId(Long workspaceId);

    @Query("select i.appointment.id from Invoice i where i.appointment.id in :ids and i.status <> com.daysheet.domain.InvoiceStatus.VOID")
    List<Long> findInvoicedAppointmentIds(@Param("ids") Collection<Long> appointmentIds);

    boolean existsByAppointmentIdAndStatusNot(Long appointmentId, InvoiceStatus status);

    @Query("""
            select coalesce(sum(i.total), 0) from Invoice i
            where i.workspace.id = :ws and i.status = com.daysheet.domain.InvoiceStatus.PAID
              and i.paidDate >= :from and i.paidDate < :to
            """)
    BigDecimal sumPaidBetween(@Param("ws") Long workspaceId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(i.total), 0) from Invoice i
            where i.workspace.id = :ws and i.status in :statuses
            """)
    BigDecimal sumByStatuses(@Param("ws") Long workspaceId,
                             @Param("statuses") Collection<InvoiceStatus> statuses);

    @Query("""
            select coalesce(sum(i.total), 0) from Invoice i
            where i.workspace.id = :ws and i.status = com.daysheet.domain.InvoiceStatus.SENT
              and i.dueDate < :today
            """)
    BigDecimal sumOverdue(@Param("ws") Long workspaceId, @Param("today") LocalDate today);

    @Query("""
            select coalesce(sum(i.total), 0) from Invoice i
            where i.client.id = :clientId and i.status = com.daysheet.domain.InvoiceStatus.PAID
            """)
    BigDecimal sumPaidForClient(@Param("clientId") Long clientId);
}
