package com.banking.platform.shared.saga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Input DTO for the Transaction Saga workflow.
 *
 * Carries all data needed to orchestrate the multi-step transaction:
 * fund reservation → payment network → GL posting → event publishing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionSagaRequest {

    /** Source account to debit. */
    private UUID accountId;

    /** Destination account (for transfers). May be null for outgoing payments. */
    private UUID destinationAccountId;

    /** Amount to transfer/pay. */
    private BigDecimal amount;

    /** ISO-4217 currency code, e.g. "USD". */
    private String currency;

    /** Human-readable description of the transaction. */
    private String description;

    /** Idempotency key — same key = same saga (used for replay safety). */
    private String idempotencyKey;

    /** Originating transaction type: "ACH", "WIRE", "INTERNAL_TRANSFER". */
    private String transactionType;

    /** Reference ID from the originating domain (e.g. wire transfer UUID). */
    private UUID referenceId;
}

