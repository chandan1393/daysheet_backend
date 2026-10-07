package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A verification email: a 6-digit code (30 minutes) and a one-click link (24 hours). Only hashes are stored. */
@Entity
@Table(name = "email_verifications", indexes = @Index(columnList = "user_id"))
@Getter @Setter @NoArgsConstructor
public class EmailVerification {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser user;

    @Column(nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant codeExpiresAt;

    @Column(nullable = false)
    private Instant linkExpiresAt;

    @Column(nullable = false)
    private int attempts;

    private Instant usedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
