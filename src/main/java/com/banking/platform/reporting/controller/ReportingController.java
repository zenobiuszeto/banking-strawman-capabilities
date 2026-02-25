package com.banking.platform.reporting.controller;

import com.banking.platform.reporting.model.dto.*;
import com.banking.platform.reporting.service.ReportingService;
import com.banking.platform.shared.util.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reporting", description = "Report generation and scheduled reports")
public class ReportingController {

    private final ReportingService reportingService;

    @PostMapping
    @Operation(summary = "Generate a new report")
    public ResponseEntity<ReportResponse> generateReport(@Valid @RequestBody GenerateReportRequest request) {
        log.info("POST /reports - Generate report for customer: {}", request.customerId());
        ReportResponse response = reportingService.generateReport(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Get report by ID")
    public ResponseEntity<ReportResponse> getReport(@PathVariable UUID reportId) {
        log.info("GET /reports/{} - Fetch report", reportId);
        ReportResponse response = reportingService.getReport(reportId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get all reports for a customer")
    public ResponseEntity<PagedResponse<ReportResponse>> getCustomerReports(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /reports/customer/{} - Fetch customer reports", customerId);
        PagedResponse<ReportResponse> response = reportingService.getCustomerReports(customerId, page, size);
        return ResponseEntity.ok(response);
    }

    // --- Scheduled Reports ---

    @PostMapping("/scheduled")
    @Operation(summary = "Create a scheduled report")
    public ResponseEntity<ScheduledReportResponse> createScheduledReport(
            @Valid @RequestBody ScheduledReportRequest request) {
        log.info("POST /reports/scheduled - Create scheduled report for customer: {}", request.customerId());
        ScheduledReportResponse response = reportingService.createScheduledReport(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/scheduled/customer/{customerId}")
    @Operation(summary = "Get scheduled reports for a customer")
    public ResponseEntity<List<ScheduledReportResponse>> getCustomerScheduledReports(
            @PathVariable UUID customerId) {
        log.info("GET /reports/scheduled/customer/{}", customerId);
        List<ScheduledReportResponse> response = reportingService.getCustomerScheduledReports(customerId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/scheduled/{scheduledReportId}/toggle")
    @Operation(summary = "Activate or deactivate a scheduled report")
    public ResponseEntity<ScheduledReportResponse> toggleScheduledReport(
            @PathVariable UUID scheduledReportId,
            @RequestParam boolean active) {
        log.info("PATCH /reports/scheduled/{}/toggle - active={}", scheduledReportId, active);
        ScheduledReportResponse response = reportingService.toggleScheduledReport(scheduledReportId, active);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/scheduled/{scheduledReportId}")
    @Operation(summary = "Delete a scheduled report")
    public ResponseEntity<Void> deleteScheduledReport(@PathVariable UUID scheduledReportId) {
        log.info("DELETE /reports/scheduled/{}", scheduledReportId);
        reportingService.deleteScheduledReport(scheduledReportId);
        return ResponseEntity.noContent().build();
    }
}

