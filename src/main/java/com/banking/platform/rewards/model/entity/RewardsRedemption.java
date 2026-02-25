package com.banking.platform.rewards.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rewards_redemptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RewardsRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID rewardsAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RedemptionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RedemptionStatus status;

    @Column(nullable = false)
    private long pointsRedeemed;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cashValue;

    @Column
    private String description;

    @Column
    private String partnerName;

    @Column(nullable = false)
    private Instant requestedAt;

    @Column
    private Instant completedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}
