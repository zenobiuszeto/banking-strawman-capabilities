package com.banking.platform.ach.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity representing an ACH (Automated Clearing House) transfer.
 * Handles both inbound and outbound ACH transactions with full lifecycle management.
 */
@Entity
@Table(
    name = "ach_transfers",
    indexes = {
        @Index(name = "idx_account_id", columnList = "account_id"),
        @Index(name = "idx_trace_number", columnList = "trace_number", unique = true),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_effective_date", columnList = "effective_date"),
        @Index(name = "idx_batch_number", columnList = "batch_number")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AchTransfer implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this ACH transfer
     */
    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Account ID that initiated or is receiving this transfer
     */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /**
     * Linked bank ID associated with this transfer
     */
    @Column(name = "linked_bank_id", nullable = false)
    private UUID linkedBankId;

    /**
     * Unique trace number for identifying the transfer (ACH identifier)
     */
    @Column(name = "trace_number", nullable = false, unique = true, length = 15)
    private String traceNumber;

    /**
     * Batch number for grouping multiple ACH transfers
     */
    @Column(name = "batch_number", length = 10)
    private String batchNumber;

    /**
     * Direction of the transfer (INBOUND or OUTBOUND)
     */
    @Column(name = "direction", nullable = false)
    @Enumerated(EnumType.STRING)
    private AchDirection direction;

    /**
     * Type of ACH processing (STANDARD, SAME_DAY, NEXT_DAY)
     */
    @Column(name = "ach_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AchType achType;

    /**
     * Current status of the transfer
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AchStatus status;

    /**
     * Standard Entry Class code for the transfer
     */
    @Column(name = "sec_code", nullable = false)
    @Enumerated(EnumType.STRING)
    private SecCode secCode;

    /**
     * Transfer amount in dollars
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * Name of the sender
     */
    @Column(name = "sender_name", nullable = false, length = 35)
    private String senderName;

    /**
     * Routing number of the sender's bank
     */
    @Column(name = "sender_routing_number", nullable = false, length = 9)
    private String senderRoutingNumber;

    /**
     * Account number of the sender
     */
    @Column(name = "sender_account_number", nullable = false, length = 17)
    private String senderAccountNumber;

    /**
     * Name of the receiver
     */
    @Column(name = "receiver_name", nullable = false, length = 35)
    private String receiverName;

    /**
     * Routing number of the receiver's bank
     */
    @Column(name = "receiver_routing_number", nullable = false, length = 9)
    private String receiverRoutingNumber;

    /**
     * Account number of the receiver
     */
    @Column(name = "receiver_account_number", nullable = false, length = 17)
    private String receiverAccountNumber;

    /**
     * Company name for the transfer
     */
    @Column(name = "company_name", nullable = false, length = 35)
    private String companyName;

    /**
     * Company identification number
     */
    @Column(name = "company_id", nullable = false, length = 10)
    private String companyId;

    /**
     * Entry description/reference
     */
    @Column(name = "entry_description", nullable = false, length = 10)
    private String entryDescription;

    /**
     * User-provided memo or note
     */
    @Column(name = "memo", length = 255)
    private String memo;

    /**
     * Effective date for the transfer processing
     */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /**
     * Date when the transfer was settled
     */
    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    /**
     * Return reason code if transfer was returned
     */
    @Column(name = "return_reason_code", length = 3)
    private String returnReasonCode;

    /**
     * Detailed return description
     */
    @Column(name = "return_description", length = 255)
    private String returnDescription;

    /**
     * Number of retry attempts made
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /**
     * Timestamp when the transfer was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the transfer was last updated
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Timestamp when the transfer was submitted to the Federal Reserve
     */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    /**
     * Timestamp when the transfer was settled
     */
    @Column(name = "settled_at")
    private Instant settledAt;
}
