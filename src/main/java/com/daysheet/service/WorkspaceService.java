package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.Workspace;
import com.daysheet.domain.WorkingHours;
import com.daysheet.dto.AuthDtos.WorkspaceDto;
import com.daysheet.dto.WorkspaceDtos.WorkingHoursDto;
import com.daysheet.dto.WorkspaceDtos.WorkspaceUpdateRequest;
import com.daysheet.repository.WorkingHoursRepository;
import com.daysheet.repository.WorkspaceRepository;
import com.daysheet.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final WorkingHoursRepository hours;

    public Workspace current() {
        return workspaces.findById(CurrentUser.workspaceId())
                .orElseThrow(() -> ApiException.notFound("Workspace"));
    }

    public static ZoneId zone(Workspace w) {
        try {
            return ZoneId.of(w.getTimezone());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    public static LocalDate today(Workspace w) { return LocalDate.now(zone(w)); }

    public static LocalDateTime now(Workspace w) { return LocalDateTime.now(zone(w)); }

    @Transactional(readOnly = true)
    public WorkspaceDto get() {
        return Mappers.workspace(current());
    }

    @Transactional
    public WorkspaceDto update(WorkspaceUpdateRequest r) {
        Workspace w = current();
        validateZone(r.timezone());
        w.setName(r.name().trim());
        w.setPhone(blankToNull(r.phone()));
        w.setEmail(blankToNull(r.email()));
        w.setAddress(blankToNull(r.address()));
        w.setCurrency(r.currency().toUpperCase());
        w.setTimezone(r.timezone());
        w.setClientLabel(r.clientLabel().trim());
        w.setClientLabelPlural(r.clientLabelPlural().trim());
        w.setSessionLabel(r.sessionLabel().trim());
        w.setSlotMinutes(r.slotMinutes());
        w.setPractitionerTitle(blankToNull(r.practitionerTitle()));
        w.setRegistrationNumber(blankToNull(r.registrationNumber()));
        return Mappers.workspace(w);
    }

    @Transactional
    public WorkspaceDto updateModules(java.util.List<String> modules) {
        Workspace w = current();
        w.setEnabledModules(com.daysheet.domain.PackModule.format(modules));
        return Mappers.workspace(w);
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursDto> getHours() {
        return hours.findByWorkspaceIdOrderByDayOfWeekAsc(CurrentUser.workspaceId()).stream()
                .map(h -> new WorkingHoursDto(h.getDayOfWeek(), h.isEnabled(), h.getStartTime(), h.getEndTime()))
                .toList();
    }

    @Transactional
    public List<WorkingHoursDto> updateHours(List<WorkingHoursDto> request) {
        Workspace w = current();
        for (WorkingHoursDto dto : request) {
            if (dto.dayOfWeek() < 1 || dto.dayOfWeek() > 7) continue;
            if (dto.enabled() && (dto.startTime() == null || dto.endTime() == null
                    || !dto.endTime().isAfter(dto.startTime()))) {
                throw ApiException.badRequest("Closing time must be after opening time for "
                        + DayOfWeek.of(dto.dayOfWeek()).name().charAt(0)
                        + DayOfWeek.of(dto.dayOfWeek()).name().substring(1).toLowerCase() + ".");
            }
            WorkingHours h = hours.findByWorkspaceIdAndDayOfWeek(w.getId(), dto.dayOfWeek())
                    .orElseGet(() -> {
                        WorkingHours n = new WorkingHours();
                        n.setWorkspace(w);
                        n.setDayOfWeek(dto.dayOfWeek());
                        return n;
                    });
            h.setEnabled(dto.enabled());
            if (dto.startTime() != null) h.setStartTime(dto.startTime());
            if (dto.endTime() != null) h.setEndTime(dto.endTime());
            hours.save(h);
        }
        return getHours();
    }

    /** Mon–Fri 09:00–18:00, Sat 10:00–14:00, Sunday closed. */
    void createDefaultHours(Workspace w) {
        for (int d = 1; d <= 7; d++) {
            WorkingHours h = new WorkingHours();
            h.setWorkspace(w);
            h.setDayOfWeek(d);
            h.setEnabled(d <= 6);
            if (d == 6) {
                h.setStartTime(LocalTime.of(10, 0));
                h.setEndTime(LocalTime.of(14, 0));
            }
            hours.save(h);
        }
    }

    static void validateZone(String tz) {
        try {
            ZoneId.of(tz);
        } catch (DateTimeException e) {
            throw ApiException.badRequest("Choose a valid timezone.");
        }
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
