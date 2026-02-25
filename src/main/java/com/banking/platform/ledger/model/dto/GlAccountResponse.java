package com.banking.platform.ledger.model.dto;

import com.banking.platform.ledger.model.entity.GlAccountType;
import com.banking.platform.ledger.model.entity.NormalBalance;

import java.time.Instant;
import java.util.UUID;

public record GlAccountResponse(
        UUID id,
        String accountCode,
        String name,
        String description,
        GlAccountType accountType,
        NormalBalance normalBalance,
        UUID parentId,
        boolean active,
        Instant createdAt
) {
}

