package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A prescription written at a visit: vitals, complaints, diagnosis, medicines, advice and follow-up. */
@Entity
@Table(name = "prescriptions", indexes = {@Index(columnList = "client_id"), @Index(columnList = "appointment_id")})
@Getter @Setter @NoArgsConstructor
public class Prescription {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    private Appointment appointment;

    @Column(length = 300)
    private String vitals;

    @Column(length = 1000)
    private String complaints;

    @Column(length = 1000)
    private String diagnosis;

    @Column(columnDefinition = "text")
    private String advice;

    @Column(length = 1000)
    private String tests;

    private LocalDate followUpDate;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<PrescriptionItem> items = new ArrayList<>();

    private String authorName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public void replaceItems(List<PrescriptionItem> newItems) {
        items.clear();
        int i = 0;
        for (PrescriptionItem item : newItems) {
            item.setPrescription(this);
            item.setPosition(i++);
            items.add(item);
        }
    }
}
