package com.banking.platform.reporting.model.dto;

import com.banking.platform.reporting.model.entity.ReportType;
import com.banking.platform.reporting.model.entity.ScheduleFrequency;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ScheduledReportRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotNull(message = "Account ID is required")
        UUID accountId,

        @NotNull(message = "Report type is required")
        ReportType reportType,

        @NotNull(message = "Schedule frequency is required")
        ScheduleFrequency frequency,

        String description
) {
}

