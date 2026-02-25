package com.banking.platform.reporting.service;

import com.banking.platform.reporting.mapper.ReportingMapper;
import com.banking.platform.reporting.model.dto.*;
import com.banking.platform.reporting.model.entity.*;
import com.banking.platform.reporting.model.event.ReportEvent;
import com.banking.platform.reporting.repository.ReportRepository;
import com.banking.platform.reporting.repository.ScheduledReportRepository;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import com.banking.platform.shared.util.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportingService {

    private final ReportRepository reportRepository;
    private final ScheduledReportRepository scheduledReportRepository;
    private final ReportingMapper reportingMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String REPORT_TOPIC = "report-events";

    @Transactional
    @CacheEvict(value = "reports", allEntries = true)
    public ReportResponse generateReport(GenerateReportRequest request) {
        log.info("Generating report for customer: {}, type: {}", request.customerId(), request.reportType());

        Report report = Report.builder()
                .customerId(request.customerId())
                .accountId(request.accountId())
                .reportType(request.reportType())
                .status(ReportStatus.REQUESTED)
                .description(request.description())
                .periodStart(request.periodStart() != null ? request.periodStart() : LocalDate.now().minusMonths(1))
                .periodEnd(request.periodEnd() != null ? request.periodEnd() : LocalDate.now())
                .build();

        Report savedReport = reportRepository.save(report);

        // Trigger async report generation
        processReportAsync(savedReport.getId());

        publishReportEvent(savedReport, "report.requested");

        log.info("Report requested successfully: {}", savedReport.getId());
        return reportingMapper.toReportResponse(savedReport);
    }

    @Async
    public void processReportAsync(UUID reportId) {
        try {
            log.info("Processing report asynchronously: {}", reportId);

            Report report = reportRepository.findById(reportId)
                    .orElseThrow(() -> new ResourceNotFoundException("Report", reportId.toString()));

            report.setStatus(ReportStatus.PROCESSING);
            reportRepository.save(report);

            // Simulate report generation
            Thread.sleep(2000);

            String fileUrl = String.format("/reports/%s/%s_%s.pdf",
                    report.getCustomerId(), report.getReportType(), report.getId());

            report.setStatus(ReportStatus.COMPLETED);
            report.setGeneratedFileUrl(fileUrl);
            report.setCompletedAt(Instant.now());
            reportRepository.save(report);

            publishReportEvent(report, "report.completed");
            log.info("Report generation completed: {}", reportId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Report generation interrupted: {}", reportId, e);
            markReportFailed(reportId);
        } catch (Exception e) {
            log.error("Report generation failed: {}", reportId, e);
            markReportFailed(reportId);
        }
    }

    private void markReportFailed(UUID reportId) {
        reportRepository.findById(reportId).ifPresent(report -> {
            report.setStatus(ReportStatus.FAILED);
            reportRepository.save(report);
            publishReportEvent(report, "report.failed");
        });
    }

    @Cacheable(value = "reports", key = "#reportId")
    public ReportResponse getReport(UUID reportId) {
        log.debug("Fetching report: {}", reportId);

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report", reportId.toString()));

        return reportingMapper.toReportResponse(report);
    }

    public PagedResponse<ReportResponse> getCustomerReports(UUID customerId, int page, int size) {
        log.debug("Fetching reports for customer: {}, page: {}, size: {}", customerId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Report> reportPage = reportRepository.findByCustomerIdOrderByRequestedAtDesc(customerId, pageable);

        List<ReportResponse> content = reportPage.getContent()
                .stream()
                .map(reportingMapper::toReportResponse)
                .collect(Collectors.toList());

        return PagedResponse.<ReportResponse>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(reportPage.getTotalElements())
                .totalPages(reportPage.getTotalPages())
                .last(reportPage.isLast())
                .build();
    }

    // --- Scheduled Reports ---

    @Transactional
    public ScheduledReportResponse createScheduledReport(ScheduledReportRequest request) {
        log.info("Creating scheduled report for customer: {}, type: {}, frequency: {}",
                request.customerId(), request.reportType(), request.frequency());

        LocalDate nextRun = calculateNextRunDate(request.frequency());

        ScheduledReport scheduled = ScheduledReport.builder()
                .customerId(request.customerId())
                .accountId(request.accountId())
                .reportType(request.reportType())
                .frequency(request.frequency())
                .description(request.description())
                .active(true)
                .nextRunDate(nextRun)
                .build();

        ScheduledReport saved = scheduledReportRepository.save(scheduled);
        log.info("Scheduled report created: {}", saved.getId());

        return reportingMapper.toScheduledReportResponse(saved);
    }

    public List<ScheduledReportResponse> getCustomerScheduledReports(UUID customerId) {
        log.debug("Fetching scheduled reports for customer: {}", customerId);

        return scheduledReportRepository.findByCustomerId(customerId)
                .stream()
                .map(reportingMapper::toScheduledReportResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ScheduledReportResponse toggleScheduledReport(UUID scheduledReportId, boolean active) {
        log.info("Toggling scheduled report {} to active={}", scheduledReportId, active);

        ScheduledReport scheduled = scheduledReportRepository.findById(scheduledReportId)
                .orElseThrow(() -> new ResourceNotFoundException("ScheduledReport", scheduledReportId.toString()));

        scheduled.setActive(active);
        if (active) {
            scheduled.setNextRunDate(calculateNextRunDate(scheduled.getFrequency()));
        }

        ScheduledReport saved = scheduledReportRepository.save(scheduled);
        return reportingMapper.toScheduledReportResponse(saved);
    }

    @Transactional
    public void deleteScheduledReport(UUID scheduledReportId) {
        log.info("Deleting scheduled report: {}", scheduledReportId);

        if (!scheduledReportRepository.existsById(scheduledReportId)) {
            throw new ResourceNotFoundException("ScheduledReport", scheduledReportId.toString());
        }

        scheduledReportRepository.deleteById(scheduledReportId);
    }

    private LocalDate calculateNextRunDate(ScheduleFrequency frequency) {
        LocalDate now = LocalDate.now();
        return switch (frequency) {
            case DAILY -> now.plusDays(1);
            case WEEKLY -> now.plusWeeks(1);
            case BIWEEKLY -> now.plusWeeks(2);
            case MONTHLY -> now.plusMonths(1);
            case QUARTERLY -> now.plusMonths(3);
            case ANNUALLY -> now.plusYears(1);
        };
    }

    private void publishReportEvent(Report report, String eventType) {
        try {
            ReportEvent event = ReportEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .timestamp(Instant.now())
                    .reportId(report.getId())
                    .customerId(report.getCustomerId())
                    .reportType(report.getReportType())
                    .status(report.getStatus())
                    .build();

            kafkaTemplate.send(REPORT_TOPIC, report.getId().toString(), event);
            log.debug("Report event published: {}", eventType);
        } catch (Exception e) {
            log.error("Failed to publish report event: {}", eventType, e);
        }
    }
}

