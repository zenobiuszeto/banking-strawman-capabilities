package com.banking.platform.transaction.model.dto;

import com.banking.platform.transaction.model.entity.TransactionCategory;
import com.banking.platform.transaction.model.entity.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for creating a new transaction.
 * Contains the required and optional fields to initiate a transaction.
 */
public record CreateTransactionRequest(
    /**
     * Account ID on which the transaction will be recorded.
     */
    @NotNull(message = "Account ID is required")
    UUID accountId,

    /**
     * Related account ID for transfers; optional for non-transfer transactions.
     */
    UUID relatedAccountId,

    /**
     * Transaction type (CREDIT or DEBIT).
     */
    @NotNull(message = "Transaction type is required")
    TransactionType type,

    /**
     * Transaction category.
     */
    @NotNull(message = "Transaction category is required")
    TransactionCategory category,

    /**
     * Transaction amount; must be positive.
     */
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    BigDecimal amount,

    /**
     * Transaction description or memo.
     */
    String description,

    /**
     * Merchant or payee name.
     */
    String merchantName,

    /**
     * Merchant category code or classification.
     */
    String merchantCategory,

    /**
     * Channel through which transaction is initiated
     * (e.g., ONLINE, MOBILE, BRANCH, ATM, ACH, WIRE).
     */
    @NotBlank(message = "Channel is required")
    String channel
) {}
