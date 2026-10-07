package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "workspaces")
@Getter @Setter @NoArgsConstructor
public class Workspace {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Profession profession;

    @Column(nullable = false, length = 40)
    private String clientLabel;

    @Column(nullable = false, length = 40)
    private String clientLabelPlural;

    @Column(nullable = false, length = 40)
    private String sessionLabel;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 60)
    private String timezone;

    private String phone;
    private String email;
    private String address;

    @Column(nullable = false)
    private int slotMinutes = 30;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.TRIAL;

    private Instant trialEndsAt;

    /** End of the paid period; set when a payment succeeds. */
    private Instant subscriptionEndsAt;

    // Billing details printed on the GST invoice for this practice's subscription
    @Column(length = 120)
    private String billingName;

    @Column(length = 300)
    private String billingAddress;

    /** GST state code, e.g. "07" for Delhi. Decides CGST+SGST vs IGST. */
    @Column(length = 2)
    private String billingStateCode;

    @Column(length = 15)
    private String billingGstin;

    /** Comma-separated PackModule names; null = profession defaults. */
    @Column(length = 200)
    private String enabledModules;

    /** Shown on prescriptions, e.g. "Dr. Priya Mehta, MBBS, MD (Medicine)". */
    @Column(length = 160)
    private String practitionerTitle;

    /** Medical council / bar council registration number, printed on prescriptions. */
    @Column(length = 80)
    private String registrationNumber;

    public java.util.Set<PackModule> modules() { return PackModule.parse(enabledModules, profession); }

    public boolean hasModule(PackModule m) { return modules().contains(m); }

    /** Sample data asked for at signup; added once the email is verified. */
    private Boolean sampleDataPending;

    /** Set by an admin to block an account that misuses the service. */
    private Boolean suspended;

    @Column(length = 300)
    private String suspendedReason;

    public boolean isSuspendedNow() { return Boolean.TRUE.equals(suspended); }

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** True when the trial or paid period has run out, even before the hourly job flips the plan to EXPIRED. */
    public boolean isExpired(Instant now) {
        return switch (plan) {
            case EXPIRED -> true;
            case TRIAL -> trialEndsAt != null && trialEndsAt.isBefore(now);
            case ACTIVE, SOLO, PRACTICE -> subscriptionEndsAt != null && subscriptionEndsAt.isBefore(now);
        };
    }

    /** When the current access ends: trial end or paid period end. */
    public Instant accessEndsAt() {
        return plan == Plan.TRIAL ? trialEndsAt : subscriptionEndsAt;
    }
}
