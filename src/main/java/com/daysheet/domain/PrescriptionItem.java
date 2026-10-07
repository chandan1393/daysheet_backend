package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One medicine line: "Paracetamol 650 mg, 1 tablet, 1-0-1, after food, 5 days". */
@Entity
@Table(name = "prescription_items")
@Getter @Setter @NoArgsConstructor
public class PrescriptionItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Prescription prescription;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 160)
    private String medicine;

    @Column(length = 60)
    private String dose;

    /** e.g. 1-0-1 (morning-afternoon-night) */
    @Column(length = 40)
    private String frequency;

    @Column(length = 60)
    private String timing;

    @Column(length = 40)
    private String duration;

    @Column(length = 200)
    private String notes;
}
