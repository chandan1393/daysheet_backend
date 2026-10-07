package com.daysheet.web;

import com.daysheet.dto.PackDtos.*;
import com.daysheet.service.CaseService;
import com.daysheet.service.DeadlineService;
import com.daysheet.service.PackageService;
import com.daysheet.service.PrescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Profession pack endpoints: prescriptions, packages, cases and deadlines. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PackController {

    private final PrescriptionService prescriptions;
    private final PackageService packages;
    private final CaseService cases;
    private final DeadlineService deadlines;

    // ---------- Prescriptions
    @GetMapping("/clients/{clientId}/prescriptions")
    public List<PrescriptionDto> clientPrescriptions(@PathVariable Long clientId) { return prescriptions.forClient(clientId); }

    @GetMapping("/prescriptions/{id}")
    public PrescriptionDto prescription(@PathVariable Long id) { return prescriptions.get(id); }

    @GetMapping("/prescriptions/medicines")
    public List<String> medicines(@RequestParam(defaultValue = "") String q) { return prescriptions.medicines(q); }

    @PostMapping("/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionDto createPrescription(@Valid @RequestBody PrescriptionRequest r) { return prescriptions.create(r); }

    @PutMapping("/prescriptions/{id}")
    public PrescriptionDto updatePrescription(@PathVariable Long id, @Valid @RequestBody PrescriptionRequest r) { return prescriptions.update(id, r); }

    @DeleteMapping("/prescriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePrescription(@PathVariable Long id) { prescriptions.delete(id); }

    // ---------- Packages
    @GetMapping("/packages/templates")
    public List<PackageTemplateDto> templates() { return packages.templates(); }

    @PostMapping("/packages/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public PackageTemplateDto createTemplate(@Valid @RequestBody PackageTemplateRequest r) { return packages.saveTemplate(null, r); }

    @PutMapping("/packages/templates/{id}")
    public PackageTemplateDto updateTemplate(@PathVariable Long id, @Valid @RequestBody PackageTemplateRequest r) { return packages.saveTemplate(id, r); }

    @DeleteMapping("/packages/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable Long id) { packages.deleteTemplate(id); }

    @GetMapping("/clients/{clientId}/packages")
    public List<ClientPackageDto> clientPackages(@PathVariable Long clientId) { return packages.forClient(clientId); }

    @PostMapping("/clients/{clientId}/packages")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientPackageDto sell(@PathVariable Long clientId, @Valid @RequestBody SellPackageRequest r) { return packages.sell(clientId, r); }

    @PatchMapping("/client-packages/{id}/adjust")
    public ClientPackageDto adjust(@PathVariable Long id, @Valid @RequestBody AdjustRequest r) { return packages.adjust(id, r.delta()); }

    @DeleteMapping("/client-packages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePackage(@PathVariable Long id) { packages.delete(id); }

    // ---------- Cases and hearings
    @GetMapping("/cases")
    public List<CaseDto> cases(@RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        return cases.list(status, q);
    }

    @GetMapping("/cases/hearings/upcoming")
    public List<HearingDto> upcomingHearings(@RequestParam(defaultValue = "14") int days) {
        return cases.upcoming(Math.max(1, Math.min(days, 90)));
    }

    @GetMapping("/cases/{id}")
    public CaseDto caseDetail(@PathVariable Long id) { return cases.get(id); }

    @GetMapping("/clients/{clientId}/cases")
    public List<CaseDto> clientCases(@PathVariable Long clientId) { return cases.forClient(clientId); }

    @PostMapping("/cases")
    @ResponseStatus(HttpStatus.CREATED)
    public CaseDto createCase(@Valid @RequestBody CaseRequest r) { return cases.save(null, r); }

    @PutMapping("/cases/{id}")
    public CaseDto updateCase(@PathVariable Long id, @Valid @RequestBody CaseRequest r) { return cases.save(id, r); }

    @DeleteMapping("/cases/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCase(@PathVariable Long id) { cases.delete(id); }

    @PostMapping("/cases/{id}/hearings")
    public CaseDto addHearing(@PathVariable Long id, @Valid @RequestBody HearingRequest r) { return cases.addHearing(id, r); }

    @PutMapping("/hearings/{id}")
    public CaseDto updateHearing(@PathVariable Long id, @Valid @RequestBody HearingRequest r) { return cases.updateHearing(id, r); }

    @DeleteMapping("/hearings/{id}")
    public CaseDto deleteHearing(@PathVariable Long id) { return cases.deleteHearing(id); }

    // ---------- Deadlines
    @GetMapping("/deadlines")
    public List<DeadlineDto> deadlines() { return deadlines.list(); }

    @GetMapping("/clients/{clientId}/deadlines")
    public List<DeadlineDto> clientDeadlines(@PathVariable Long clientId) { return deadlines.forClient(clientId); }

    @PostMapping("/deadlines")
    @ResponseStatus(HttpStatus.CREATED)
    public DeadlineDto createDeadline(@Valid @RequestBody DeadlineRequest r) { return deadlines.save(null, r); }

    @PostMapping("/deadlines/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<DeadlineDto> bulkDeadlines(@Valid @RequestBody BulkDeadlineRequest r) { return deadlines.bulk(r); }

    @PutMapping("/deadlines/{id}")
    public DeadlineDto updateDeadline(@PathVariable Long id, @Valid @RequestBody DeadlineRequest r) { return deadlines.save(id, r); }

    @PatchMapping("/deadlines/{id}/done")
    public DeadlineDto markDone(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean done) { return deadlines.markDone(id, done); }

    @DeleteMapping("/deadlines/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeadline(@PathVariable Long id) { deadlines.delete(id); }
}
