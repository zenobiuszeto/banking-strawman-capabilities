package com.banking.platform.onboarding.model.event;

import com.banking.platform.onboarding.model.entity.AccountType;
import com.banking.platform.onboarding.model.entity.ApplicationStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class ApplicationEvent {
    String eventId;
    String eventType;
    Instant timestamp;
    UUID applicationId;
    UUID customerId;
    ApplicationStatus status;
    AccountType accountType;
}
