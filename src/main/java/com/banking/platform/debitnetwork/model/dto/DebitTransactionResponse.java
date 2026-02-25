package com.banking.platform.debitnetwork.model.dto;

import com.banking.platform.debitnetwork.model.entity.DeclineReason;
import com.banking.platform.debitnetwork.model.entity.DebitTransactionStatus;
import com.banking.platform.debitnetwork.model.entity.DebitTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DebitTransactionResponse(
        UUID id,
        UUID debitCardId,
        UUID accountId,
        String authorizationCode,
        String networkReferenceId,
        String merchantName,
        String merchantCategoryCode,
        BigDecimal amount,
        String currency,
        DebitTransactionType transactionType,
        DebitTransactionStatus status,
        DeclineReason declineReason,
        Instant authorizedAt,
        Instant settledAt
) {
}

