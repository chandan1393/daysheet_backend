package com.daysheet.repository;

import com.daysheet.domain.WorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {
    List<WorkingHours> findByWorkspaceIdOrderByDayOfWeekAsc(Long workspaceId);
    Optional<WorkingHours> findByWorkspaceIdAndDayOfWeek(Long workspaceId, int dayOfWeek);
}
