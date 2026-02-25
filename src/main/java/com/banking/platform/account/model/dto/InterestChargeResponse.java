package com.banking.platform.account.model.dto;

import com.banking.platform.account.model.entity.ChargeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InterestChargeResponse(
    UUID id,
    ChargeType type,
    BigDecimal amount,
    BigDecimal runningBalance,
    String description,
    LocalDate postDate,
    LocalDate effectiveDate
) {}
