package com.banking.platform.account.model.dto;

import com.banking.platform.onboarding.model.entity.AccountType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountRequest(
    @NotNull(message = "Customer ID is required")
    UUID customerId,

    @NotNull(message = "Account type is required")
    AccountType accountType,

    BigDecimal initialDeposit,

    String currency
) {
    public CreateAccountRequest(UUID customerId, AccountType accountType, BigDecimal initialDeposit, String currency) {
        this.customerId = customerId;
        this.accountType = accountType;
        this.initialDeposit = initialDeposit != null ? initialDeposit : BigDecimal.ZERO;
        this.currency = currency != null ? currency : "USD";
    }
}
