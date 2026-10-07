package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** A compliance task for a client: "GSTR-3B for Sep 2026, due 20 Oct". */
@Entity
@Table(name = "deadlines", indexes = {@Index(columnList = "workspace_id,dueDate"), @Index(columnList = "client_id")})
@Getter @Setter @NoArgsConstructor
public class Deadline {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Client client;

    @Column(nullable = false, length = 120)
    private String title;

    /** GST, TDS, ITR, ADVANCE_TAX, ROC, AUDIT, OTHER */
    @Column(nullable = false, length = 20)
    private String category = "OTHER";

    @Column(length = 40)
    private String period;

    @Column(nullable = false)
    private LocalDate dueDate;

    /** PENDING or DONE */
    @Column(nullable = false, length = 10)
    private String status = "PENDING";

    private LocalDate doneOn;

    /** NONE, MONTHLY, QUARTERLY, YEARLY: the next one is created when this is marked done. */
    @Column(nullable = false, length = 10)
    private String recurrence = "NONE";

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
