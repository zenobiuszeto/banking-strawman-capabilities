package com.banking.platform.rewards.model.dto;

import com.banking.platform.rewards.model.entity.RewardsTier;

import java.math.BigDecimal;
import java.util.UUID;

public record RewardsSummaryResponse(
        UUID customerId,
        RewardsTier currentTier,
        long currentBalance,
        BigDecimal cashEquivalent,
        long pointsToNextTier,
        RewardsTier nextTier,
        long earnedThisMonth,
        long redeemedThisMonth
) {
}
