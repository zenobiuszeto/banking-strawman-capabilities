package com.banking.platform.wire.model.dto;

import com.banking.platform.wire.model.entity.WireStatus;
import com.banking.platform.wire.model.entity.WireType;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WireTransferResponse(
    UUID id,
    UUID accountId,
    String wireReferenceNumber,
    String fedReferenceNumber,
    WireType wireType,
    WireStatus status,
    BigDecimal amount,
    BigDecimal fee,
    String currency,
    String senderName,
    String senderAccountNumber,
    String senderRoutingNumber,
    String senderBankName,
    String beneficiaryName,
    String beneficiaryAccountNumber,
    String beneficiaryRoutingNumber,
    String beneficiaryBankName,
    String beneficiaryBankAddress,
    String intermediaryBankName,
    String intermediarySwiftCode,
    String beneficiarySwiftCode,
    String beneficiaryIban,
    String purposeOfWire,
    String memo,
    String failureReason,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    Instant updatedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    Instant submittedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    Instant completedAt
) {
}
