package com.daysheet.service;

import com.daysheet.domain.Appointment;
import com.daysheet.domain.AppointmentStatus;
import com.daysheet.domain.InvoiceStatus;
import com.daysheet.domain.PackModule;
import com.daysheet.domain.Workspace;
import com.daysheet.dto.AppointmentDtos.AppointmentDto;
import com.daysheet.dto.DashboardDtos.*;
import com.daysheet.repository.AppointmentRepository;
import com.daysheet.repository.ClientRepository;
import com.daysheet.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AppointmentRepository appointments;
    private final InvoiceRepository invoices;
    private final ClientRepository clients;
    private final AppointmentService appointmentService;
    private final com.daysheet.repository.HearingRepository hearings;
    private final com.daysheet.repository.DeadlineRepository deadlines;
    private final WorkspaceService workspaceService;

    @Transactional(readOnly = true)
    public DashboardResponse get() {
        Workspace w = workspaceService.current();
        Long ws = w.getId();
        LocalDate today = WorkspaceService.today(w);
        LocalDateTime now = WorkspaceService.now(w);

        List<AppointmentDto> todayList = appointmentService.toDtos(
                appointments.findInRange(ws, today.atStartOfDay(), today.plusDays(1).atStartOfDay()));

        List<Appointment> next = appointments.findInRange(ws, today.plusDays(1).atStartOfDay(), today.plusDays(15).atStartOfDay())
                .stream().filter(a -> !ClientService.INACTIVE.contains(a.getStatus())).limit(6).toList();
        List<AppointmentDto> upcoming = appointmentService.toDtos(next);

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long weekCount = appointments.countByWorkspaceIdAndStartAtBetween(ws, weekStart.atStartOfDay(),
                weekStart.plusDays(7).atStartOfDay());

        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal monthRevenue = invoices.sumPaidBetween(ws, monthStart, monthStart.plusMonths(1));
        BigDecimal lastMonthRevenue = invoices.sumPaidBetween(ws, monthStart.minusMonths(1), monthStart);
        BigDecimal outstanding = invoices.sumByStatuses(ws, List.of(InvoiceStatus.SENT));
        BigDecimal overdue = invoices.sumOverdue(ws, today);

        long activeClients = clients.countByWorkspaceIdAndArchivedFalse(ws);
        Instant monthStartInstant = monthStart.atStartOfDay(WorkspaceService.zone(w)).toInstant();
        long newClients = clients.countByWorkspaceIdAndArchivedFalseAndCreatedAtAfter(ws, monthStartInstant);

        LocalDateTime thirtyDaysAgo = now.minusDays(30);
        long noShows = appointments.countByWorkspaceIdAndStatusAndStartAtBetween(ws, AppointmentStatus.NO_SHOW, thirtyDaysAgo, now);
        long completed = appointments.countByWorkspaceIdAndStatusAndStartAtBetween(ws, AppointmentStatus.COMPLETED, thirtyDaysAgo, now);
        double noShowRate = (noShows + completed) == 0 ? 0 : (double) noShows / (noShows + completed);

        List<MonthRevenue> revenue = new ArrayList<>();
        for (int m = 5; m >= 0; m--) {
            LocalDate start = monthStart.minusMonths(m);
            revenue.add(new MonthRevenue(
                    start.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    start.getYear(),
                    invoices.sumPaidBetween(ws, start, start.plusMonths(1))));
        }

        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (AppointmentStatus s : AppointmentStatus.values()) {
            breakdown.put(s.name(), appointments.countByWorkspaceIdAndStatusAndStartAtBetween(ws, s, thirtyDaysAgo, now.plusDays(30)));
        }

        Stats stats = new Stats(todayList.stream().filter(a -> !a.status().equals("CANCELLED")).count(),
                weekCount, monthRevenue, lastMonthRevenue, outstanding, overdue,
                activeClients, newClients, noShowRate);

        var pack = new com.daysheet.dto.PackDtos.PackSummary(
                w.hasModule(PackModule.CASES)
                        ? hearings.upcoming(ws, today, today.plusDays(7)).stream().map(CaseService::toDto).toList() : List.of(),
                w.hasModule(PackModule.DEADLINES)
                        ? deadlines.pendingDueBy(ws, today.plusDays(7)).stream().map(d -> DeadlineService.toDto(d, today)).toList() : List.of());
        return new DashboardResponse(todayList, upcoming, stats, revenue, breakdown, pack);
    }
}
