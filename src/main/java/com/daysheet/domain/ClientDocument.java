package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A file kept on someone's record: a prescription, report, scan, contract, worksheet or photo. */
@Entity
@Table(name = "client_documents", indexes = {
        @Index(columnList = "client_id"),
        @Index(columnList = "appointment_id")
})
@Getter @Setter @NoArgsConstructor
public class ClientDocument {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Client client;

    /** The visit this file was added to; null when it belongs to the person in general. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Appointment appointment;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    /** Where the bytes live in FileStorage. Never shown to users. */
    @Column(nullable = false, unique = true, length = 160)
    private String storageKey;

    private String uploadedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
