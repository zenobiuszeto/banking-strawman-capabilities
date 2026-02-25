package com.banking.platform.rewards.model.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RewardsTier {
    BRONZE("Bronze", 1.0, 0),
    SILVER("Silver", 1.25, 5000),
    GOLD("Gold", 1.5, 25000),
    PLATINUM("Platinum", 2.0, 100000),
    DIAMOND("Diamond", 3.0, 500000);

    private final String displayName;
    private final double pointsMultiplier;
    private final long minPointsRequired;
}
