package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One Razorpay order for a subscription, and what happened to it. */
@Entity
@Table(name = "payments", indexes = @Index(columnList = "workspace_id"))
@Getter @Setter @NoArgsConstructor
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    @Column(nullable = false, length = 40)
    private String planCode;

    @Column(nullable = false, length = 80)
    private String planName;

    @Column(nullable = false)
    private int durationDays;

    @Column(nullable = false)
    private long amountPaise;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false, unique = true, length = 60)
    private String razorpayOrderId;

    @Column(unique = true, length = 60)
    private String razorpayPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.CREATED;

    // GST invoice (filled when the payment succeeds). Wrapper types so older rows stay valid.
    @Column(unique = true, length = 16)
    private String invoiceNumber;
    private java.time.LocalDate invoiceDate;
    private Long taxablePaise;
    private Long cgstPaise;
    private Long sgstPaise;
    private Long igstPaise;
    private Integer gstRate;
    @Column(length = 10)
    private String sac;

    // Buyer details as they were when the order was placed
    @Column(length = 120)
    private String billName;
    @Column(length = 300)
    private String billAddress;
    @Column(length = 2)
    private String billStateCode;
    @Column(length = 15)
    private String billGstin;

    /** RAZORPAY, or MANUAL for a bank transfer / UPI payment recorded by an admin. */
    @Column(length = 20)
    private String method;

    /** UTR or transaction reference for manual payments. */
    @Column(length = 80)
    private String reference;

    @Column(length = 160)
    private String recordedBy;

    private Instant periodStart;
    private Instant periodEnd;
    private Instant paidAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
