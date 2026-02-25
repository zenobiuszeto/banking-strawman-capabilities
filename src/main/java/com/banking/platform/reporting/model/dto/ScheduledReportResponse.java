package com.banking.platform.reporting.model.dto;

import com.banking.platform.reporting.model.entity.ReportType;
import com.banking.platform.reporting.model.entity.ScheduleFrequency;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduledReportResponse(
        UUID id,
        UUID customerId,
        UUID accountId,
        ReportType reportType,
        ScheduleFrequency frequency,
        String description,
        boolean active,
        LocalDate nextRunDate,
        LocalDate lastRunDate,
        Instant createdAt
) {
}

