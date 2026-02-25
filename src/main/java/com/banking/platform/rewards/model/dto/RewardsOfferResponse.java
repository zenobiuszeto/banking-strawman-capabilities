package com.banking.platform.rewards.model.dto;

import com.banking.platform.rewards.model.entity.OfferType;
import com.banking.platform.rewards.model.entity.RewardsTier;

import java.time.LocalDate;
import java.util.UUID;

public record RewardsOfferResponse(
        UUID id,
        String offerCode,
        String title,
        String description,
        OfferType type,
        int bonusPoints,
        double multiplier,
        LocalDate startDate,
        LocalDate endDate,
        RewardsTier minimumTier
) {
}
