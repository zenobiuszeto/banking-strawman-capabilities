package com.banking.platform.transaction.model.event;

import com.banking.platform.transaction.model.entity.TransactionCategory;
import com.banking.platform.transaction.model.entity.TransactionStatus;
import com.banking.platform.transaction.model.entity.TransactionType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a transaction state changes.
 * Used for asynchronous processing and event-driven architecture.
 */
@Value
@Builder
public class TransactionEvent {

    /**
     * Unique event identifier.
     */
    private String eventId;

    /**
     * Type of event
     * (e.g., "transaction.created", "transaction.posted", "transaction.reversed").
     */
    private String eventType;

    /**
     * Timestamp when the event occurred.
     */
    private Instant timestamp;

    /**
     * ID of the transaction associated with this event.
     */
    private UUID transactionId;

    /**
     * Account ID associated with the transaction.
     */
    private UUID accountId;

    /**
     * Type of the transaction.
     */
    private TransactionType type;

    /**
     * Category of the transaction.
     */
    private TransactionCategory category;

    /**
     * Transaction amount.
     */
    private BigDecimal amount;

    /**
     * Status of the transaction.
     */
    private TransactionStatus status;
}
