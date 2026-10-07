package com.daysheet.repository;

import com.daysheet.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);

    /** Row lock so the browser callback and the webhook can't both extend the plan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.razorpayOrderId = :orderId")
    Optional<Payment> lockByOrderId(@Param("orderId") String orderId);

    long countByWorkspaceIdAndCreatedAtAfter(Long workspaceId, Instant after);

    java.util.Optional<Payment> findByIdAndWorkspaceId(Long id, Long workspaceId);

    List<Payment> findTop10ByStatusOrderByPaidAtDesc(com.daysheet.domain.PaymentStatus status);

    @Query("select coalesce(sum(p.amountPaise), 0) from Payment p where p.status = com.daysheet.domain.PaymentStatus.PAID and p.paidAt >= :from")
    long revenueSince(@Param("from") Instant from);

    /** For the GST report your CA uses to file GSTR-1. */
    List<Payment> findByStatusAndInvoiceDateBetweenOrderByInvoiceNumberAsc(com.daysheet.domain.PaymentStatus status,
                                                                          java.time.LocalDate from, java.time.LocalDate to);
}
