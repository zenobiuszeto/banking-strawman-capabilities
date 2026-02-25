package com.banking.platform.transaction.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO providing a summary of transactions for a given account and date range.
 * Used for reporting and account statements.
 */
public record TransactionSummaryResponse(
    /**
     * Account ID for which the summary is generated.
     */
    UUID accountId,

    /**
     * Total amount of all credit transactions in the period.
     */
    BigDecimal totalCredits,

    /**
     * Total amount of all debit transactions in the period.
     */
    BigDecimal totalDebits,

    /**
     * Net change in account balance (credits - debits).
     */
    BigDecimal netChange,

    /**
     * Total number of transactions in the period.
     */
    int transactionCount,

    /**
     * Start date of the summary period.
     */
    LocalDate fromDate,

    /**
     * End date of the summary period.
     */
    LocalDate toDate
) {}
