package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** A package a practice sells, e.g. "10 physio sessions for ₹8,000, valid 90 days". */
@Entity
@Table(name = "package_templates", indexes = @Index(columnList = "workspace_id"))
@Getter @Setter @NoArgsConstructor
public class PackageTemplate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int sessions;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    /** Days the package stays valid after purchase; null = no expiry. */
    private Integer validityDays;

    /** Only visits for this service use up the package; null = any service. */
    @ManyToOne(fetch = FetchType.LAZY)
    private ServiceOffering service;

    @Column(nullable = false)
    private boolean active = true;
}
