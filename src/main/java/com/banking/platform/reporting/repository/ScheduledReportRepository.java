package com.banking.platform.reporting.repository;

import com.banking.platform.reporting.model.entity.ScheduledReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledReportRepository extends JpaRepository<ScheduledReport, UUID> {

    List<ScheduledReport> findByCustomerId(UUID customerId);

    List<ScheduledReport> findByActiveTrue();

    List<ScheduledReport> findByActiveTrueAndNextRunDateLessThanEqual(LocalDate date);
}

