package com.banking.platform.rewards.model.event;

import com.banking.platform.rewards.model.entity.RewardsTier;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class RewardsEvent {
    String eventId;
    String eventType; // "rewards.earned", "rewards.redeemed", "rewards.tier_upgraded", "rewards.expired"
    Instant timestamp;
    UUID customerId;
    UUID rewardsAccountId;
    long points;
    long newBalance;
    RewardsTier tier;
}
