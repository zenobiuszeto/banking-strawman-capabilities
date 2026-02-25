package com.banking.platform.transaction.model.dto;

import com.banking.platform.transaction.model.entity.TransactionCategory;
import com.banking.platform.transaction.model.entity.TransactionStatus;
import com.banking.platform.transaction.model.entity.TransactionType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * DTO for advanced transaction search with optional filters.
 * Supports filtering by type, category, status, amount range, date range, and keyword.
 */
public record TransactionSearchRequest(
    /**
     * Account ID to search transactions for.
     */
    @NotNull(message = "Account ID is required")
    UUID accountId,

    /**
     * Optional transaction type filter.
     */
    Optional<TransactionType> type,

    /**
     * Optional transaction category filter.
     */
    Optional<TransactionCategory> category,

    /**
     * Optional transaction status filter.
     */
    Optional<TransactionStatus> status,

    /**
     * Optional minimum amount filter (inclusive).
     */
    Optional<BigDecimal> minAmount,

    /**
     * Optional maximum amount filter (inclusive).
     */
    Optional<BigDecimal> maxAmount,

    /**
     * Start date for transaction search (inclusive).
     */
    @NotNull(message = "From date is required")
    LocalDate fromDate,

    /**
     * End date for transaction search (inclusive).
     */
    @NotNull(message = "To date is required")
    LocalDate toDate,

    /**
     * Optional keyword to search in description and merchant name (case-insensitive).
     */
    Optional<String> keyword
) {}
