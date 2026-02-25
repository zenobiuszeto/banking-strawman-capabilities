package com.banking.platform.reporting.model.event;

import com.banking.platform.reporting.model.entity.ReportStatus;
import com.banking.platform.reporting.model.entity.ReportType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class ReportEvent {
    String eventId;
    String eventType;
    Instant timestamp;
    UUID reportId;
    UUID customerId;
    ReportType reportType;
    ReportStatus status;
}

