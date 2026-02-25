package com.banking.platform.debitnetwork.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "debit_cards", indexes = {
        @Index(name = "idx_debit_cards_account_id", columnList = "account_id"),
        @Index(name = "idx_debit_cards_customer_id", columnList = "customer_id"),
        @Index(name = "idx_debit_cards_card_number", columnList = "card_number_masked"),
        @Index(name = "idx_debit_cards_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DebitCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(name = "card_number_masked", nullable = false, length = 19)
    private String cardNumberMasked;

    @Column(nullable = false)
    private String cardHolderName;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyLimit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyUsed;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyUsed;

    @Column
    private LocalDate dailyUsedResetDate;

    @Column
    private LocalDate monthlyUsedResetDate;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (dailyUsed == null) dailyUsed = BigDecimal.ZERO;
        if (monthlyUsed == null) monthlyUsed = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

