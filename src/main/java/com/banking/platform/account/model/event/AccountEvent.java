package com.banking.platform.account.model.event;

import com.banking.platform.account.model.entity.AccountStatus;
import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class AccountEvent {
    String eventId;
    String eventType;
    Instant timestamp;
    UUID accountId;
    UUID customerId;
    String accountNumber;
    AccountStatus status;
    BigDecimal balance;
}
