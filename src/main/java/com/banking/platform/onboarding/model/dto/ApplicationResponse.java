package com.banking.platform.onboarding.model.dto;

import com.banking.platform.onboarding.model.entity.AccountType;
import com.banking.platform.onboarding.model.entity.ApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String phone,
    ApplicationStatus status,
    AccountType accountType,
    Instant submittedAt,
    Instant reviewedAt
) {
}
