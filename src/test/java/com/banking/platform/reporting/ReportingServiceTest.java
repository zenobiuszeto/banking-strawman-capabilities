package com.banking.platform.reporting;

import com.banking.platform.reporting.mapper.ReportingMapper;
import com.banking.platform.reporting.model.dto.GenerateReportRequest;
import com.banking.platform.reporting.model.dto.ReportResponse;
import com.banking.platform.reporting.model.dto.ScheduledReportRequest;
import com.banking.platform.reporting.model.dto.ScheduledReportResponse;
import com.banking.platform.reporting.model.entity.*;
import com.banking.platform.reporting.repository.ReportRepository;
import com.banking.platform.reporting.repository.ScheduledReportRepository;
import com.banking.platform.reporting.service.ReportingService;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ScheduledReportRepository scheduledReportRepository;

    @Mock
    private ReportingMapper reportingMapper;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private ReportingService reportingService;

    private UUID customerId;
    private UUID accountId;
    private UUID reportId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        reportId = UUID.randomUUID();
    }

    @Test
    void testGenerateReport_Success() {
        GenerateReportRequest request = new GenerateReportRequest(
                customerId, accountId, ReportType.ACCOUNT_STATEMENT,
                LocalDate.now().minusMonths(1), LocalDate.now(), "Monthly statement"
        );

        Report savedReport = Report.builder()
                .id(reportId)
                .customerId(customerId)
                .accountId(accountId)
                .reportType(ReportType.ACCOUNT_STATEMENT)
                .status(ReportStatus.REQUESTED)
                .description("Monthly statement")
                .periodStart(LocalDate.now().minusMonths(1))
                .periodEnd(LocalDate.now())
                .requestedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ReportResponse expectedResponse = new ReportResponse(
                reportId, customerId, accountId, ReportType.ACCOUNT_STATEMENT,
                ReportStatus.REQUESTED, "Monthly statement",
                LocalDate.now().minusMonths(1), LocalDate.now(), null, Instant.now(), null
        );

        when(reportRepository.save(any(Report.class))).thenReturn(savedReport);
        when(reportingMapper.toReportResponse(savedReport)).thenReturn(expectedResponse);

        ReportResponse response = reportingService.generateReport(request);

        assertNotNull(response);
        assertEquals(reportId, response.id());
        assertEquals(ReportType.ACCOUNT_STATEMENT, response.reportType());
        assertEquals(ReportStatus.REQUESTED, response.status());
        verify(reportRepository, times(1)).save(any(Report.class));
    }

    @Test
    void testGetReport_Success() {
        Report report = Report.builder()
                .id(reportId)
                .customerId(customerId)
                .accountId(accountId)
                .reportType(ReportType.TRANSACTION_SUMMARY)
                .status(ReportStatus.COMPLETED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ReportResponse expectedResponse = new ReportResponse(
                reportId, customerId, accountId, ReportType.TRANSACTION_SUMMARY,
                ReportStatus.COMPLETED, null, null, null, "/reports/file.pdf",
                Instant.now(), Instant.now()
        );

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportingMapper.toReportResponse(report)).thenReturn(expectedResponse);

        ReportResponse response = reportingService.getReport(reportId);

        assertNotNull(response);
        assertEquals(reportId, response.id());
        verify(reportRepository, times(1)).findById(reportId);
    }

    @Test
    void testGetReport_NotFound() {
        when(reportRepository.findById(reportId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reportingService.getReport(reportId));
    }

    @Test
    void testCreateScheduledReport_Success() {
        ScheduledReportRequest request = new ScheduledReportRequest(
                customerId, accountId, ReportType.ACCOUNT_STATEMENT,
                ScheduleFrequency.MONTHLY, "Monthly statement"
        );

        ScheduledReport saved = ScheduledReport.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .accountId(accountId)
                .reportType(ReportType.ACCOUNT_STATEMENT)
                .frequency(ScheduleFrequency.MONTHLY)
                .description("Monthly statement")
                .active(true)
                .nextRunDate(LocalDate.now().plusMonths(1))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ScheduledReportResponse expectedResponse = new ScheduledReportResponse(
                saved.getId(), customerId, accountId, ReportType.ACCOUNT_STATEMENT,
                ScheduleFrequency.MONTHLY, "Monthly statement", true,
                LocalDate.now().plusMonths(1), null, Instant.now()
        );

        when(scheduledReportRepository.save(any(ScheduledReport.class))).thenReturn(saved);
        when(reportingMapper.toScheduledReportResponse(saved)).thenReturn(expectedResponse);

        ScheduledReportResponse response = reportingService.createScheduledReport(request);

        assertNotNull(response);
        assertTrue(response.active());
        assertEquals(ScheduleFrequency.MONTHLY, response.frequency());
        verify(scheduledReportRepository, times(1)).save(any(ScheduledReport.class));
    }

    @Test
    void testToggleScheduledReport_Success() {
        UUID scheduledId = UUID.randomUUID();
        ScheduledReport scheduled = ScheduledReport.builder()
                .id(scheduledId)
                .customerId(customerId)
                .accountId(accountId)
                .reportType(ReportType.BALANCE_HISTORY)
                .frequency(ScheduleFrequency.WEEKLY)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ScheduledReport toggled = ScheduledReport.builder()
                .id(scheduledId)
                .customerId(customerId)
                .accountId(accountId)
                .reportType(ReportType.BALANCE_HISTORY)
                .frequency(ScheduleFrequency.WEEKLY)
                .active(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ScheduledReportResponse expectedResponse = new ScheduledReportResponse(
                scheduledId, customerId, accountId, ReportType.BALANCE_HISTORY,
                ScheduleFrequency.WEEKLY, null, false, null, null, Instant.now()
        );

        when(scheduledReportRepository.findById(scheduledId)).thenReturn(Optional.of(scheduled));
        when(scheduledReportRepository.save(any(ScheduledReport.class))).thenReturn(toggled);
        when(reportingMapper.toScheduledReportResponse(toggled)).thenReturn(expectedResponse);

        ScheduledReportResponse response = reportingService.toggleScheduledReport(scheduledId, false);

        assertNotNull(response);
        assertFalse(response.active());
    }

    @Test
    void testDeleteScheduledReport_NotFound() {
        UUID scheduledId = UUID.randomUUID();
        when(scheduledReportRepository.existsById(scheduledId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> reportingService.deleteScheduledReport(scheduledId));
    }
}

