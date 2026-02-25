package com.banking.platform.transaction.model.entity;

import lombok.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA Entity representing a financial transaction.
 * Stores complete transaction history and details for audit and reporting.
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_account_post_date", columnList = "account_id, post_date"),
    @Index(name = "idx_reference_number", columnList = "reference_number"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_category", columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Transaction {

    /**
     * Unique identifier for the transaction.
     */
    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Account that originated or is affected by this transaction.
     */
    @Column(name = "account_id", columnDefinition = "uuid", nullable = false)
    private UUID accountId;

    /**
     * Related account for transfers; nullable for non-transfer transactions.
     */
    @Column(name = "related_account_id", columnDefinition = "uuid")
    private UUID relatedAccountId;

    /**
     * Unique reference number for the transaction (e.g., confirmation number).
     * Generated and unique across the system.
     */
    @Column(name = "reference_number", length = 50, nullable = false, unique = true)
    private String referenceNumber;

    /**
     * Type of transaction (CREDIT or DEBIT).
     */
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    /**
     * Category of transaction providing detailed classification.
     */
    @Column(name = "category", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionCategory category;

    /**
     * Status of the transaction in its lifecycle.
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    /**
     * Transaction amount in dollars.
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * Running balance of the account after this transaction was posted.
     */
    @Column(name = "running_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal runningBalance;

    /**
     * Transaction description or memo.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Merchant or payee name.
     */
    @Column(name = "merchant_name", length = 255)
    private String merchantName;

    /**
     * Merchant category code or classification.
     */
    @Column(name = "merchant_category", length = 100)
    private String merchantCategory;

    /**
     * Channel through which transaction was initiated.
     */
    @Column(name = "channel", length = 50)
    private String channel;

    /**
     * Date when the transaction was posted to the account.
     */
    @Column(name = "post_date", nullable = false)
    private LocalDate postDate;

    /**
     * Date when the transaction becomes effective (may differ from post date).
     */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /**
     * Timestamp when the transaction record was created in the system.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp of the last update to the transaction record.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Automatically set timestamps before persisting.
     */
    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Automatically update the updatedAt timestamp before updating.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
