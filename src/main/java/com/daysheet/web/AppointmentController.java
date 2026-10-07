package com.daysheet.web;

import com.daysheet.dto.AppointmentDtos.*;
import com.daysheet.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointments;

    @GetMapping
    public List<AppointmentDto> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return appointments.list(from, to);
    }

    /** Notes and files for this visit, plus the previous visit for context. */
    @GetMapping("/{id}/record")
    public VisitRecord record(@PathVariable Long id) {
        return appointments.record(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentDto create(@Valid @RequestBody AppointmentRequest request) {
        return appointments.create(request);
    }

    @PutMapping("/{id}")
    public AppointmentDto update(@PathVariable Long id, @Valid @RequestBody AppointmentRequest request) {
        return appointments.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public AppointmentDto status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return appointments.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { appointments.delete(id); }
}
