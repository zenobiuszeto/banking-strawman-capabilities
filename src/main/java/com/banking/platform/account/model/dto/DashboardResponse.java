package com.banking.platform.account.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
    UUID customerId,
    String customerName,
    BigDecimal totalNetWorth,
    List<AccountSummaryResponse> accounts,
    int totalAccounts,
    Instant lastUpdated
) {}
