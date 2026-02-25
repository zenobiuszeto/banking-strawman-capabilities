package com.banking.platform.ledger.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostingRuleRequest(
        @NotBlank(message = "Rule code is required")
        String ruleCode,

        @NotBlank(message = "Rule name is required")
        String name,

        String description,

        @NotBlank(message = "Trigger event is required")
        String triggerEvent,

        @NotBlank(message = "Debit account code is required")
        String debitAccountCode,

        @NotBlank(message = "Credit account code is required")
        String creditAccountCode
) {
}

