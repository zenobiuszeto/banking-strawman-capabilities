package com.banking.platform.debitnetwork.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "debit_transactions", indexes = {
        @Index(name = "idx_debit_tx_card_id", columnList = "debit_card_id"),
        @Index(name = "idx_debit_tx_account_id", columnList = "account_id"),
        @Index(name = "idx_debit_tx_auth_code", columnList = "authorization_code"),
        @Index(name = "idx_debit_tx_status", columnList = "status"),
        @Index(name = "idx_debit_tx_authorized_at", columnList = "authorized_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DebitTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "debit_card_id", nullable = false)
    private UUID debitCardId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "authorization_code", nullable = false, unique = true, length = 20)
    private String authorizationCode;

    @Column(length = 50)
    private String networkReferenceId;

    @Column(nullable = false)
    private String merchantName;

    @Column(length = 10)
    private String merchantCategoryCode;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private DebitTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebitTransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column
    private DeclineReason declineReason;

    @Column(name = "authorized_at", nullable = false)
    private Instant authorizedAt;

    @Column
    private Instant settledAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (authorizedAt == null) authorizedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

