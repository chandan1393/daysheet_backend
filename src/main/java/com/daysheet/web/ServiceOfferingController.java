package com.daysheet.web;

import com.daysheet.dto.ServiceDtos.ServiceDto;
import com.daysheet.dto.ServiceDtos.ServiceRequest;
import com.daysheet.service.ServiceOfferingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceOfferingController {

    private final ServiceOfferingService services;

    @GetMapping
    public List<ServiceDto> list() { return services.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceDto create(@Valid @RequestBody ServiceRequest request) { return services.create(request); }

    @PutMapping("/{id}")
    public ServiceDto update(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
        return services.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { services.delete(id); }
}
