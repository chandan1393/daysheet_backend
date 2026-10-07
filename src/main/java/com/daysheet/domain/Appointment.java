package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/** Times are stored as wall-clock time in the workspace's own timezone. */
@Entity
@Table(name = "appointments", indexes = {
        @Index(columnList = "workspace_id,start_at"),
        @Index(columnList = "client_id")
})
@Getter @Setter @NoArgsConstructor
public class Appointment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    private ServiceOffering service;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentSource source = AppointmentSource.MANUAL;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    /** The package this visit used a session from, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    private ClientPackage clientPackage;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
