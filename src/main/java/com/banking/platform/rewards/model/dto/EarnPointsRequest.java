package com.banking.platform.rewards.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record EarnPointsRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        UUID sourceTransactionId,

        @Positive(message = "Base points must be positive")
        long basePoints,

        String description
) {
}
