package com.banking.platform.ledger.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record TrialBalanceResponse(
        List<GlAccountBalanceResponse> accounts,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        boolean balanced
) {
}

