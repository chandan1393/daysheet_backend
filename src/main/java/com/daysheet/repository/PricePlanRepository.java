package com.daysheet.repository;

import com.daysheet.domain.PricePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PricePlanRepository extends JpaRepository<PricePlan, Long> {
    List<PricePlan> findByActiveTrueOrderBySortOrderAsc();
    List<PricePlan> findAllByOrderBySortOrderAsc();
    Optional<PricePlan> findByCodeIgnoreCase(String code);
}
