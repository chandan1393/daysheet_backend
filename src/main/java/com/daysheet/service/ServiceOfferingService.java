package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.ServiceOffering;
import com.daysheet.dto.ServiceDtos.ServiceDto;
import com.daysheet.dto.ServiceDtos.ServiceRequest;
import com.daysheet.repository.ServiceOfferingRepository;
import com.daysheet.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceOfferingService {

    private final ServiceOfferingRepository services;
    private final WorkspaceService workspaceService;

    @Transactional(readOnly = true)
    public List<ServiceDto> list() {
        return services.findByWorkspaceIdAndActiveTrueOrderByNameAsc(CurrentUser.workspaceId())
                .stream().map(Mappers::service).toList();
    }

    @Transactional
    public ServiceDto create(ServiceRequest r) {
        ServiceOffering s = new ServiceOffering();
        s.setWorkspace(workspaceService.current());
        apply(s, r);
        return Mappers.service(services.save(s));
    }

    @Transactional
    public ServiceDto update(Long id, ServiceRequest r) {
        ServiceOffering s = find(id);
        apply(s, r);
        return Mappers.service(s);
    }

    /** Soft delete: past appointments keep pointing at the service. */
    @Transactional
    public void delete(Long id) {
        find(id).setActive(false);
    }

    ServiceOffering find(Long id) {
        return services.findByIdAndWorkspaceId(id, CurrentUser.workspaceId())
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> ApiException.notFound("Service"));
    }

    private void apply(ServiceOffering s, ServiceRequest r) {
        s.setName(r.name().trim());
        s.setDescription(WorkspaceService.blankToNull(r.description()));
        s.setDurationMinutes(r.durationMinutes());
        s.setPrice(r.price());
        if (r.color() != null) s.setColor(r.color());
        s.setBookableOnline(r.bookableOnline());
    }
}
