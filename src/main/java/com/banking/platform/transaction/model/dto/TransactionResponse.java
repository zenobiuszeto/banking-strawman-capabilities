package com.banking.platform.transaction.model.dto;

import com.banking.platform.transaction.model.entity.TransactionCategory;
import com.banking.platform.transaction.model.entity.TransactionStatus;
import com.banking.platform.transaction.model.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO representing a transaction in response payloads.
 * Contains all publicly visible transaction details.
 */
public record TransactionResponse(
    /**
     * Unique transaction identifier.
     */
    UUID id,

    /**
     * Unique reference number for the transaction.
     */
    String referenceNumber,

    /**
     * Type of transaction (CREDIT or DEBIT).
     */
    TransactionType type,

    /**
     * Category of transaction.
     */
    TransactionCategory category,

    /**
     * Current status of the transaction.
     */
    TransactionStatus status,

    /**
     * Transaction amount in dollars.
     */
    BigDecimal amount,

    /**
     * Account balance after this transaction.
     */
    BigDecimal runningBalance,

    /**
     * Transaction description or memo.
     */
    String description,

    /**
     * Merchant or payee name.
     */
    String merchantName,

    /**
     * Date when transaction was posted.
     */
    LocalDate postDate,

    /**
     * Effective date of the transaction.
     */
    LocalDate effectiveDate,

    /**
     * Timestamp when the transaction was created.
     */
    Instant createdAt
) {}
