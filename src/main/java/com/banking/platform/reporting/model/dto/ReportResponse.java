package com.banking.platform.reporting.model.dto;

import com.banking.platform.reporting.model.entity.ReportStatus;
import com.banking.platform.reporting.model.entity.ReportType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        UUID customerId,
        UUID accountId,
        ReportType reportType,
        ReportStatus status,
        String description,
        LocalDate periodStart,
        LocalDate periodEnd,
        String generatedFileUrl,
        Instant requestedAt,
        Instant completedAt
) {
}

