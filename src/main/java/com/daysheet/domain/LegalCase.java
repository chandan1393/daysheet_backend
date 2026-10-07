package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A matter handled for a client, with its hearings. */
@Entity
@Table(name = "legal_cases", indexes = {@Index(columnList = "workspace_id"), @Index(columnList = "client_id")})
@Getter @Setter @NoArgsConstructor
public class LegalCase {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Client client;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 80)
    private String caseNumber;

    @Column(length = 160)
    private String court;

    @Column(length = 160)
    private String oppositeParty;

    @Column(length = 80)
    private String caseType;

    /** OPEN or CLOSED */
    @Column(nullable = false, length = 10)
    private String status = "OPEN";

    @Column(columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "legalCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("hearingDate desc")
    private List<Hearing> hearings = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
