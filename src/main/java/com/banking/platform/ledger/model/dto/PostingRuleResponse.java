package com.banking.platform.ledger.model.dto;

import java.time.Instant;
import java.util.UUID;

public record PostingRuleResponse(
        UUID id,
        String ruleCode,
        String name,
        String description,
        String triggerEvent,
        String debitAccountCode,
        String creditAccountCode,
        boolean active,
        Instant createdAt
) {
}

