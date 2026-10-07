package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A subscription plan with its price in Indian rupees. Prices live in the database
 * so they can be changed (with the admin API or SQL) without a new release.
 */
@Entity
@Table(name = "price_plans")
@Getter @Setter @NoArgsConstructor
public class PricePlan {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable identifier, e.g. MONTHLY or YEARLY. */
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    /** Price in paise (₹799 = 79900). Always INR. */
    @Column(nullable = false)
    private long amountPaise;

    /** How long one payment keeps the practice active. */
    @Column(nullable = false)
    private int durationDays;

    /** Shown after the price: "month" or "year". */
    @Column(nullable = false, length = 20)
    private String intervalLabel;

    /** Short highlight such as "2 months free". */
    @Column(length = 40)
    private String badge;

    /** One feature per line, shown on the pricing cards. */
    @Column(columnDefinition = "text")
    private String features;

    @Column(nullable = false)
    private boolean highlighted;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int sortOrder;
}
