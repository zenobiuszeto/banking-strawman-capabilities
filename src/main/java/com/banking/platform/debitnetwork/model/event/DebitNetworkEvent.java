package com.banking.platform.debitnetwork.model.event;

import com.banking.platform.debitnetwork.model.entity.DebitTransactionStatus;
import com.banking.platform.debitnetwork.model.entity.DebitTransactionType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class DebitNetworkEvent {
    String eventId;
    String eventType;
    Instant timestamp;
    UUID debitCardId;
    UUID accountId;
    UUID transactionId;
    String authorizationCode;
    BigDecimal amount;
    String merchantName;
    DebitTransactionType transactionType;
    DebitTransactionStatus transactionStatus;
}

