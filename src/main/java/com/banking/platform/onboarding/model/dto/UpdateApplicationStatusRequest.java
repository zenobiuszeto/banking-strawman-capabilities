package com.banking.platform.onboarding.model.dto;

import com.banking.platform.onboarding.model.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateApplicationStatusRequest(
    @NotNull(message = "Status is required")
    ApplicationStatus status,

    String rejectionReason,

    UUID reviewerId
) {
}
