package com.banking.platform.debitnetwork.model.dto;

import com.banking.platform.debitnetwork.model.entity.SettlementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementResponse(
        UUID id,
        LocalDate settlementDate,
        BigDecimal totalAmount,
        int transactionCount,
        SettlementStatus status,
        String batchId,
        Instant createdAt
) {
}

