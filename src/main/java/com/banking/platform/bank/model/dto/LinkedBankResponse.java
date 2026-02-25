package com.banking.platform.bank.model.dto;

import com.banking.platform.bank.model.entity.BankAccountType;
import com.banking.platform.bank.model.entity.LinkStatus;

import java.time.Instant;
import java.util.UUID;

public record LinkedBankResponse(
        UUID id,
        String bankName,
        String maskedAccountNumber,
        BankAccountType type,
        LinkStatus status,
        String nickname,
        boolean isPrimary,
        Instant verifiedAt
) {
}
