package com.banking.platform.wire.model.dto;

import com.banking.platform.wire.model.entity.WireType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record InitiateWireRequest(
    @NotNull(message = "Account ID is required")
    UUID accountId,

    @NotNull(message = "Wire type is required")
    WireType wireType,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    BigDecimal amount,

    String currency,

    @NotBlank(message = "Beneficiary name is required")
    String beneficiaryName,

    @NotBlank(message = "Beneficiary account number is required")
    String beneficiaryAccountNumber,

    String beneficiaryRoutingNumber,

    @NotBlank(message = "Beneficiary bank name is required")
    String beneficiaryBankName,

    String beneficiarySwiftCode,

    String beneficiaryIban,

    String intermediaryBankName,

    String intermediarySwiftCode,

    String purposeOfWire,

    String memo
) {
    public InitiateWireRequest {
        if (wireType == WireType.INTERNATIONAL) {
            if (beneficiarySwiftCode == null || beneficiarySwiftCode.isBlank()) {
                throw new IllegalArgumentException("SWIFT code is required for international wire transfers");
            }
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
    }
}
