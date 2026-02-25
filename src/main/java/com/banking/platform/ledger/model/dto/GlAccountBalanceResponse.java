package com.banking.platform.ledger.model.dto;

import com.banking.platform.ledger.model.entity.GlAccountType;

import java.math.BigDecimal;

public record GlAccountBalanceResponse(
        String accountCode,
        String accountName,
        GlAccountType accountType,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal balance
) {
}

