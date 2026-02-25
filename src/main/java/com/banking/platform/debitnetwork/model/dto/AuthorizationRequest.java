package com.banking.platform.debitnetwork.model.dto;

import com.banking.platform.debitnetwork.model.entity.DebitTransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record AuthorizationRequest(
        @NotNull(message = "Debit card ID is required")
        UUID debitCardId,

        @NotBlank(message = "Merchant name is required")
        String merchantName,

        String merchantCategoryCode,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,

        String currency,

        @NotNull(message = "Transaction type is required")
        DebitTransactionType transactionType
) {
    public AuthorizationRequest(UUID debitCardId, String merchantName, String merchantCategoryCode,
                                BigDecimal amount, String currency, DebitTransactionType transactionType) {
        this.debitCardId = debitCardId;
        this.merchantName = merchantName;
        this.merchantCategoryCode = merchantCategoryCode;
        this.amount = amount;
        this.currency = currency != null ? currency : "USD";
        this.transactionType = transactionType;
    }
}

