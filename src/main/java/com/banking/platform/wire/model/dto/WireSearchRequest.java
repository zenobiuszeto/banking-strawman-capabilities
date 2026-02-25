package com.banking.platform.wire.model.dto;

import com.banking.platform.wire.model.entity.WireStatus;
import com.banking.platform.wire.model.entity.WireType;

import java.time.LocalDate;
import java.util.UUID;

public record WireSearchRequest(
    UUID accountId,
    WireType type,
    WireStatus status,
    LocalDate fromDate,
    LocalDate toDate
) {
}
