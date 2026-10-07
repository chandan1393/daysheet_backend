package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Your company and GST details (a single row). Filled from the BUSINESS_* and GST_* settings on
 * first start, then edited in the admin panel. Used on tax invoices and the legal pages.
 */
@Entity
@Table(name = "company_settings")
@Getter @Setter @NoArgsConstructor
public class CompanySettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false, length = 160)
    private String businessName;

    @Column(nullable = false, length = 400)
    private String businessAddress;

    @Column(length = 30)
    private String businessPhone;

    /** Proprietor or company legal name. Public only where the law requires it. */
    @Column(length = 160)
    private String legalName;

    @Column(nullable = false, length = 160)
    private String supportEmail;

    @Column(length = 15)
    private String gstin;

    @Column(nullable = false)
    private int gstRate = 18;

    @Column(nullable = false)
    private boolean pricesIncludeTax = true;

    @Column(length = 10)
    private String sac;

    @Column(nullable = false, length = 8)
    private String invoicePrefix = "DS";

    @Column(length = 80)
    private String jurisdictionCity;

    @Column(length = 120)
    private String grievanceOfficer;

    private Instant updatedAt;
}
