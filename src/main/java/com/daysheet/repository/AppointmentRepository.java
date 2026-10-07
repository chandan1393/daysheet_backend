package com.daysheet.repository;

import com.daysheet.domain.Appointment;
import com.daysheet.domain.AppointmentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("""
            select a from Appointment a
            join fetch a.client
            left join fetch a.service
            left join fetch a.clientPackage
            where a.workspace.id = :ws and a.startAt >= :from and a.startAt < :to
            order by a.startAt
            """)
    List<Appointment> findInRange(@Param("ws") Long workspaceId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    @Query("""
            select a from Appointment a
            left join fetch a.service
            left join fetch a.clientPackage
            where a.client.id = :clientId
            order by a.startAt desc
            """)
    List<Appointment> findByClient(@Param("clientId") Long clientId);

    @Query("""
            select a from Appointment a
            where a.workspace.id = :ws and a.status not in :ignored
              and a.startAt < :end and a.endAt > :start
              and a.id <> :excludeId
            """)
    List<Appointment> findOverlapping(@Param("ws") Long workspaceId,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      @Param("ignored") Collection<AppointmentStatus> ignored,
                                      @Param("excludeId") Long excludeId);

    /** Rows of [clientId, visitCount, lastVisit] for past, non-cancelled appointments. */
    @Query("""
            select a.client.id, count(a), max(a.startAt) from Appointment a
            where a.workspace.id = :ws and a.startAt < :now and a.status not in :ignored
            group by a.client.id
            """)
    List<Object[]> pastVisitStats(@Param("ws") Long workspaceId,
                                  @Param("now") LocalDateTime now,
                                  @Param("ignored") Collection<AppointmentStatus> ignored);

    /** Rows of [clientId, nextVisit] for upcoming, active appointments. */
    @Query("""
            select a.client.id, min(a.startAt) from Appointment a
            where a.workspace.id = :ws and a.startAt >= :now and a.status not in :ignored
            group by a.client.id
            """)
    List<Object[]> nextVisits(@Param("ws") Long workspaceId,
                              @Param("now") LocalDateTime now,
                              @Param("ignored") Collection<AppointmentStatus> ignored);

    /** Earlier visits of the same person, newest first. Use PageRequest.of(0, 1) for "the last visit". */
    @Query("""
            select a from Appointment a
            left join fetch a.service
            where a.client.id = :clientId and a.startAt < :before and a.status not in :ignored
            order by a.startAt desc
            """)
    List<Appointment> findEarlier(@Param("clientId") Long clientId,
                                  @Param("before") LocalDateTime before,
                                  @Param("ignored") Collection<AppointmentStatus> ignored,
                                  Pageable page);

    /** Which visit number this is for the person: 1 for the first visit, 10 for the tenth. */
    @Query("""
            select count(a) from Appointment a
            where a.client.id = :clientId and a.startAt <= :upTo and a.status not in :ignored
            """)
    long countVisitsUpTo(@Param("clientId") Long clientId,
                         @Param("upTo") LocalDateTime upTo,
                         @Param("ignored") Collection<AppointmentStatus> ignored);

    long countByWorkspaceIdAndStatusAndStartAtBetween(Long workspaceId, AppointmentStatus status,
                                                      LocalDateTime from, LocalDateTime to);

    /** Upcoming online bookings by one person, to stop someone filling the calendar. */
    long countByClientIdAndSourceAndStartAtAfterAndStatusIn(Long clientId, com.daysheet.domain.AppointmentSource source,
                                                            LocalDateTime after, Collection<AppointmentStatus> statuses);

    long countByWorkspaceIdAndStartAtBetween(Long workspaceId, LocalDateTime from, LocalDateTime to);
}
