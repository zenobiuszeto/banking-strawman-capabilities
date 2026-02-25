package com.banking.platform.account.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceDetailResponse(
    UUID accountId,
    BigDecimal currentBalance,
    BigDecimal availableBalance,
    BigDecimal pendingBalance,
    BigDecimal holdAmount,
    BigDecimal ledgerBalance,
    BigDecimal interestAccrued,
    BigDecimal overdraftLimit,
    BigDecimal overdraftUsed,
    String currency
) {}
