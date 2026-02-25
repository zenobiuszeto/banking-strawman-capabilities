package com.banking.platform.debitnetwork.model.dto;

import com.banking.platform.debitnetwork.model.entity.DeclineReason;
import com.banking.platform.debitnetwork.model.entity.DebitTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuthorizationResponse(
        UUID transactionId,
        String authorizationCode,
        DebitTransactionStatus status,
        BigDecimal amount,
        String merchantName,
        DeclineReason declineReason,
        Instant authorizedAt
) {
}

