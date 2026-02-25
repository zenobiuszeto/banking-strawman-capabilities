package com.banking.platform.reporting.mapper;

import com.banking.platform.reporting.model.dto.ReportResponse;
import com.banking.platform.reporting.model.dto.ScheduledReportResponse;
import com.banking.platform.reporting.model.entity.Report;
import com.banking.platform.reporting.model.entity.ScheduledReport;
import org.springframework.stereotype.Component;

@Component
public class ReportingMapper {

    public ReportResponse toReportResponse(Report report) {
        if (report == null) return null;

        return new ReportResponse(
                report.getId(),
                report.getCustomerId(),
                report.getAccountId(),
                report.getReportType(),
                report.getStatus(),
                report.getDescription(),
                report.getPeriodStart(),
                report.getPeriodEnd(),
                report.getGeneratedFileUrl(),
                report.getRequestedAt(),
                report.getCompletedAt()
        );
    }

    public ScheduledReportResponse toScheduledReportResponse(ScheduledReport scheduled) {
        if (scheduled == null) return null;

        return new ScheduledReportResponse(
                scheduled.getId(),
                scheduled.getCustomerId(),
                scheduled.getAccountId(),
                scheduled.getReportType(),
                scheduled.getFrequency(),
                scheduled.getDescription(),
                scheduled.isActive(),
                scheduled.getNextRunDate(),
                scheduled.getLastRunDate(),
                scheduled.getCreatedAt()
        );
    }
}

