package com.daysheet.web;

import com.daysheet.dto.AuthDtos.WorkspaceDto;
import com.daysheet.dto.WorkspaceDtos.WorkingHoursDto;
import com.daysheet.dto.WorkspaceDtos.WorkspaceUpdateRequest;
import com.daysheet.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaces;

    @GetMapping
    public WorkspaceDto get() { return workspaces.get(); }

    @PutMapping
    public WorkspaceDto update(@Valid @RequestBody WorkspaceUpdateRequest request) {
        return workspaces.update(request);
    }

    @PutMapping("/modules")
    public WorkspaceDto updateModules(@Valid @RequestBody com.daysheet.dto.WorkspaceDtos.ModulesRequest request) {
        return workspaces.updateModules(request.modules());
    }

    @GetMapping("/hours")
    public List<WorkingHoursDto> hours() { return workspaces.getHours(); }

    @PutMapping("/hours")
    public List<WorkingHoursDto> updateHours(@RequestBody List<WorkingHoursDto> request) {
        return workspaces.updateHours(request);
    }
}
