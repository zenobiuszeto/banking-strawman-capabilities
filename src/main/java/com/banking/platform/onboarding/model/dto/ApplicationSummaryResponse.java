package com.banking.platform.onboarding.model.dto;

import com.banking.platform.onboarding.model.entity.AccountType;
import com.banking.platform.onboarding.model.entity.ApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public record ApplicationSummaryResponse(
    UUID id,
    String fullName,
    ApplicationStatus status,
    AccountType type,
    Instant submittedAt
) {
}
