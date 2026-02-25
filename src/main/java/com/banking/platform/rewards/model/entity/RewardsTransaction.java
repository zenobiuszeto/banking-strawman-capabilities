package com.banking.platform.rewards.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rewards_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RewardsTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID rewardsAccountId;

    @Column
    private UUID sourceTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RewardsTransactionType type;

    @Column(nullable = false)
    private long points;

    @Column(nullable = false)
    private long runningBalance;

    @Column
    private String description;

    @Column
    private String referenceCode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
