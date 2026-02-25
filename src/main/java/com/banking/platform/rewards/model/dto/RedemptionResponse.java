package com.banking.platform.rewards.model.dto;

import com.banking.platform.rewards.model.entity.RedemptionStatus;
import com.banking.platform.rewards.model.entity.RedemptionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RedemptionResponse(
        UUID id,
        long pointsRedeemed,
        BigDecimal cashValue,
        RedemptionType type,
        RedemptionStatus status,
        Instant requestedAt
) {
}
