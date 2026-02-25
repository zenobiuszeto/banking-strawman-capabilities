package com.banking.platform.rewards.model.dto;

import com.banking.platform.rewards.model.entity.RewardsTransactionType;

import java.time.Instant;
import java.util.UUID;

public record RewardsTransactionResponse(
        UUID id,
        RewardsTransactionType type,
        long points,
        long runningBalance,
        String description,
        Instant createdAt
) {
}
