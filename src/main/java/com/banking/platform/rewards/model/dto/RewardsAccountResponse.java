package com.banking.platform.rewards.model.dto;

import com.banking.platform.rewards.model.entity.RewardsTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RewardsAccountResponse(
        UUID id,
        UUID customerId,
        RewardsTier tier,
        String tierDisplayName,
        long currentBalance,
        long totalEarned,
        long totalRedeemed,
        BigDecimal estimatedCashValue,
        LocalDate tierExpiryDate
) {
}
