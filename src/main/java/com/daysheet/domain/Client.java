package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "clients", indexes = @Index(columnList = "workspace_id"))
@Getter @Setter @NoArgsConstructor
public class Client {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    @Column(nullable = false)
    private String fullName;

    private String email;
    private String phone;
    private LocalDate dateOfBirth;

    /** Comma-separated, e.g. "VIP,Insurance" */
    private String tags;

    @Column(nullable = false)
    private boolean archived = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
