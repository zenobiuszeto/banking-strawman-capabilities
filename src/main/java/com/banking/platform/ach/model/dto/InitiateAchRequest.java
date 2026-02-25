package com.banking.platform.ach.model.dto;

import com.banking.platform.ach.model.entity.AchDirection;
import com.banking.platform.ach.model.entity.AchType;
import com.banking.platform.ach.model.entity.SecCode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for initiating a new ACH transfer.
 */
public record InitiateAchRequest(
    /**
     * The account ID that is initiating or receiving the transfer
     */
    @NotNull(message = "Account ID is required")
    UUID accountId,

    /**
     * The linked bank ID for the transfer
     */
    @NotNull(message = "Linked bank ID is required")
    UUID linkedBankId,

    /**
     * Direction of the transfer (INBOUND or OUTBOUND)
     */
    @NotNull(message = "Transfer direction is required")
    AchDirection direction,

    /**
     * Type of ACH processing
     */
    @NotNull(message = "ACH type is required")
    AchType achType,

    /**
     * Transfer amount
     */
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    BigDecimal amount,

    /**
     * Optional memo or description
     */
    String memo,

    /**
     * Standard Entry Class code
     */
    @NotNull(message = "SEC code is required")
    SecCode secCode,

    /**
     * Effective date for the transfer
     */
    LocalDate effectiveDate
) {
}
