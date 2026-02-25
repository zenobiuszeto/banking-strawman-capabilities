package com.banking.platform.reporting.model.dto;

import com.banking.platform.reporting.model.entity.ReportType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record GenerateReportRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotNull(message = "Account ID is required")
        UUID accountId,

        @NotNull(message = "Report type is required")
        ReportType reportType,

        LocalDate periodStart,
        LocalDate periodEnd,
        String description
) {
}

