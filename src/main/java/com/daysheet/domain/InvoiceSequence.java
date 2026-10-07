package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Gap-free invoice counter per financial year (GST requires consecutive numbering). */
@Entity
@Table(name = "invoice_sequences")
@Getter @Setter @NoArgsConstructor
public class InvoiceSequence {

    /** e.g. "2026-27" */
    @Id
    @Column(name = "financial_year", length = 9)
    private String financialYear;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;
}
