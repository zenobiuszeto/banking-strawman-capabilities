package com.banking.platform.bank.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record VerifyMicroDepositsRequest(
        @NotNull(message = "First deposit amount is required")
        @Positive(message = "First deposit amount must be positive")
        BigDecimal amount1,

        @NotNull(message = "Second deposit amount is required")
        @Positive(message = "Second deposit amount must be positive")
        BigDecimal amount2
) {
}
