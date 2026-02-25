package com.banking.platform.rewards.model.dto;

import com.banking.platform.rewards.model.entity.RewardsTransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record RedeemPointsRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @Positive(message = "Points must be positive")
        long points,

        @NotNull(message = "Redemption type is required")
        RewardsTransactionType redemptionType,

        String description
) {
}
