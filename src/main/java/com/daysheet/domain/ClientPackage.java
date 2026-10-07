package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A package bought by one person. Completed visits use up its sessions automatically. */
@Entity
@Table(name = "client_packages", indexes = @Index(columnList = "client_id"))
@Getter @Setter @NoArgsConstructor
public class ClientPackage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    private PackageTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    private ServiceOffering service;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int totalSessions;

    @Column(nullable = false)
    private int usedSessions;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate purchasedOn;

    private LocalDate expiresOn;

    private Long invoiceId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean usable(LocalDate today) {
        return usedSessions < totalSessions && (expiresOn == null || !expiresOn.isBefore(today));
    }

    public String status(LocalDate today) {
        if (usedSessions >= totalSessions) return "USED_UP";
        if (expiresOn != null && expiresOn.isBefore(today)) return "EXPIRED";
        return "ACTIVE";
    }
}
