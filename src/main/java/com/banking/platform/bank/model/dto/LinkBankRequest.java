package com.banking.platform.bank.model.dto;

import com.banking.platform.bank.model.entity.BankAccountType;
import com.banking.platform.bank.model.entity.VerificationMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LinkBankRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotBlank(message = "Routing number is required")
        String routingNumber,

        @NotBlank(message = "Account number is required")
        String accountNumber,

        @NotNull(message = "Account type is required")
        BankAccountType accountType,

        String accountHolderName,

        String nickname,

        @NotNull(message = "Verification method is required")
        VerificationMethod verificationMethod
) {
}
