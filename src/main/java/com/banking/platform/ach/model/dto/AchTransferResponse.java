package com.banking.platform.ach.model.dto;

import com.banking.platform.ach.model.entity.AchDirection;
import com.banking.platform.ach.model.entity.AchStatus;
import com.banking.platform.ach.model.entity.AchType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for ACH transfer information.
 */
public record AchTransferResponse(
    /**
     * Unique identifier for the transfer
     */
    UUID id,

    /**
     * Unique trace number for the transfer
     */
    String traceNumber,

    /**
     * Direction of the transfer
     */
    AchDirection direction,

    /**
     * Type of ACH processing
     */
    AchType type,

    /**
     * Current status of the transfer
     */
    AchStatus status,

    /**
     * Transfer amount
     */
    BigDecimal amount,

    /**
     * Name of the sender
     */
    String senderName,

    /**
     * Name of the receiver
     */
    String receiverName,

    /**
     * Effective date for the transfer
     */
    LocalDate effectiveDate,

    /**
     * Date when the transfer was settled
     */
    LocalDate settlementDate,

    /**
     * Return reason code if applicable
     */
    String returnReasonCode,

    /**
     * Timestamp when the transfer was created
     */
    Instant createdAt
) {
}
