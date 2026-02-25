package com.banking.platform.reporting.repository;

import com.banking.platform.reporting.model.entity.Report;
import com.banking.platform.reporting.model.entity.ReportStatus;
import com.banking.platform.reporting.model.entity.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findByCustomerIdOrderByRequestedAtDesc(UUID customerId, Pageable pageable);

    List<Report> findByCustomerIdAndReportType(UUID customerId, ReportType reportType);

    List<Report> findByStatus(ReportStatus status);

    Page<Report> findByCustomerIdAndAccountIdOrderByRequestedAtDesc(UUID customerId, UUID accountId, Pageable pageable);
}

