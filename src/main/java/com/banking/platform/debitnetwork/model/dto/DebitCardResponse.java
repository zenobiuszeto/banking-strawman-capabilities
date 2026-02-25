package com.banking.platform.debitnetwork.model.dto;

import com.banking.platform.debitnetwork.model.entity.CardStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DebitCardResponse(
        UUID id,
        UUID accountId,
        UUID customerId,
        String cardNumberMasked,
        String cardHolderName,
        LocalDate expiryDate,
        CardStatus status,
        BigDecimal dailyLimit,
        BigDecimal monthlyLimit,
        BigDecimal dailyUsed,
        BigDecimal monthlyUsed,
        Instant createdAt
) {
}

