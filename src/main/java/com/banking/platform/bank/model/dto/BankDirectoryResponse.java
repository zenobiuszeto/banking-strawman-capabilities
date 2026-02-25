package com.banking.platform.bank.model.dto;

public record BankDirectoryResponse(
        String routingNumber,
        String bankName,
        String city,
        String state,
        boolean isActive
) {
}
