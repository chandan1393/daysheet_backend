package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "services", indexes = @Index(columnList = "workspace_id"))
@Getter @Setter @NoArgsConstructor
public class ServiceOffering {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false, length = 9)
    private String color = "#0E7C86";

    @Column(nullable = false)
    private boolean bookableOnline = true;

    @Column(nullable = false)
    private boolean active = true;
}
