package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One line in the admin activity log: who changed what, and when. */
@Entity
@Table(name = "admin_audit", indexes = @Index(columnList = "createdAt"))
@Getter @Setter @NoArgsConstructor
public class AdminAudit {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String adminEmail;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 500)
    private String details;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
