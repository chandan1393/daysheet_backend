package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** One hearing date in a case, with its purpose and what happened. */
@Entity
@Table(name = "hearings", indexes = @Index(columnList = "hearingDate"))
@Getter @Setter @NoArgsConstructor
public class Hearing {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private LegalCase legalCase;

    @Column(nullable = false)
    private LocalDate hearingDate;

    @Column(length = 200)
    private String purpose;

    @Column(columnDefinition = "text")
    private String outcome;
}
