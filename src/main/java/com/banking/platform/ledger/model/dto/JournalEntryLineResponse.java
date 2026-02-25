package com.banking.platform.ledger.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record JournalEntryLineResponse(
        UUID id,
        UUID glAccountId,
        String accountCode,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String description
) {
}

