package com.banking.platform.rewards.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "rewards_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RewardsAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RewardsTier tier;

    @Column(nullable = false)
    private long totalPointsEarned;

    @Column(nullable = false)
    private long totalPointsRedeemed;

    @Column(nullable = false)
    private long currentBalance;

    @Column(nullable = false)
    private BigDecimal lifetimeValue;

    @Column(nullable = true)
    private LocalDate tierExpiryDate;

    @Column(nullable = false)
    private LocalDate tierEvaluationDate;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (tierEvaluationDate == null) {
            tierEvaluationDate = LocalDate.now();
        }
        if (lifetimeValue == null) {
            lifetimeValue = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
