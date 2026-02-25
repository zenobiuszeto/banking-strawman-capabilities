package com.banking.platform.account.model.dto;

import com.banking.platform.account.model.entity.AccountStatus;
import com.banking.platform.onboarding.model.entity.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    String accountNumber,
    AccountType type,
    AccountStatus status,
    BigDecimal currentBalance,
    BigDecimal availableBalance,
    BigDecimal pendingBalance,
    BigDecimal holdAmount,
    String currency,
    LocalDate openedDate
) {}
