package com.banking.platform.shared.model.dto;

import java.math.BigDecimal;

public record MoneyDto(BigDecimal amount, String currency) {

    public static MoneyDto of(BigDecimal amount, String currency) {
        return new MoneyDto(amount, currency);
    }
}
