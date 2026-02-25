package com.banking.platform.ledger.model.dto;

import com.banking.platform.ledger.model.entity.GlAccountType;
import com.banking.platform.ledger.model.entity.NormalBalance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateGlAccountRequest(
        @NotBlank(message = "Account code is required")
        String accountCode,

        @NotBlank(message = "Account name is required")
        String name,

        String description,

        @NotNull(message = "Account type is required")
        GlAccountType accountType,

        @NotNull(message = "Normal balance is required")
        NormalBalance normalBalance,

        UUID parentId
) {
}

