package com.daysheet.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record Stats(long todayCount, long weekCount, BigDecimal monthRevenue, BigDecimal lastMonthRevenue,
                        BigDecimal outstanding, BigDecimal overdue, long activeClients,
                        long newClientsThisMonth, double noShowRate) {}

    public record MonthRevenue(String label, int year, BigDecimal amount) {}

    public record DashboardResponse(List<AppointmentDtos.AppointmentDto> today,
                                    List<AppointmentDtos.AppointmentDto> upcoming,
                                    Stats stats,
                                    List<MonthRevenue> revenue,
                                    Map<String, Long> statusBreakdown,
                                    PackDtos.PackSummary pack) {}
}
