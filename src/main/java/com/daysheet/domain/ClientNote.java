package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "client_notes", indexes = {@Index(columnList = "client_id"), @Index(columnList = "appointment_id")})
@Getter @Setter @NoArgsConstructor
public class ClientNote {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Client client;

    /** The visit this note belongs to; null for a general note about the person. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Appointment appointment;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    private String authorName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
