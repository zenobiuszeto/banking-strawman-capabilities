package com.banking.platform.debitnetwork.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record IssueCardRequest(
        @NotNull(message = "Account ID is required")
        UUID accountId,

        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotBlank(message = "Card holder name is required")
        String cardHolderName,

        @Positive(message = "Daily limit must be positive")
        BigDecimal dailyLimit,

        @Positive(message = "Monthly limit must be positive")
        BigDecimal monthlyLimit
) {
    public IssueCardRequest(UUID accountId, UUID customerId, String cardHolderName,
                            BigDecimal dailyLimit, BigDecimal monthlyLimit) {
        this.accountId = accountId;
        this.customerId = customerId;
        this.cardHolderName = cardHolderName;
        this.dailyLimit = dailyLimit != null ? dailyLimit : new BigDecimal("2500.00");
        this.monthlyLimit = monthlyLimit != null ? monthlyLimit : new BigDecimal("25000.00");
    }
}

