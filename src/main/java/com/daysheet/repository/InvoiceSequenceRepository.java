package com.daysheet.repository;

import com.daysheet.domain.InvoiceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, String> {

    /** Creates the year's counter if it doesn't exist yet, safely under concurrent payments. */
    @Modifying
    @Query(value = "insert into invoice_sequences (financial_year, last_number) values (:fy, 0) on conflict (financial_year) do nothing",
            nativeQuery = true)
    int ensure(@Param("fy") String financialYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvoiceSequence s where s.financialYear = :fy")
    Optional<InvoiceSequence> lock(@Param("fy") String financialYear);
}
