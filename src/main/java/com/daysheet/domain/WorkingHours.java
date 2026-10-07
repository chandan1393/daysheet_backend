package com.daysheet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "working_hours",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "day_of_week"}))
@Getter @Setter @NoArgsConstructor
public class WorkingHours {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Workspace workspace;

    /** ISO day of week: 1 = Monday ... 7 = Sunday */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private LocalTime startTime = LocalTime.of(9, 0);

    @Column(nullable = false)
    private LocalTime endTime = LocalTime.of(18, 0);
}
