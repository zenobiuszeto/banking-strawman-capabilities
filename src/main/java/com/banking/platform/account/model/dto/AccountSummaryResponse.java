package com.banking.platform.account.model.dto;

import com.banking.platform.account.model.entity.AccountStatus;
import com.banking.platform.onboarding.model.entity.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountSummaryResponse(
    UUID accountId,
    String accountNumber,
    AccountType type,
    AccountStatus status,
    BigDecimal currentBalance,
    BigDecimal availableBalance,
    BigDecimal totalPendingCredits,
    BigDecimal totalPendingDebits
) {}
