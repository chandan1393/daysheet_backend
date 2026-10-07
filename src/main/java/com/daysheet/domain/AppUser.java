package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_users")
@Getter @Setter @NoArgsConstructor
public class AppUser {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.OWNER;

    /**
     * false until the email is confirmed. null for accounts created before verification existed
     * (treated as verified so nobody gets locked out).
     */
    private Boolean emailVerified;

    private Instant emailVerifiedAt;

    public boolean isPendingVerification() { return Boolean.FALSE.equals(emailVerified); }

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
