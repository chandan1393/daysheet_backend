package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Someone who runs Daysheet itself (you), separate from practice accounts. */
@Entity
@Table(name = "admin_users")
@Getter @Setter @NoArgsConstructor
public class AdminUser {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    private Instant lastLoginAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
