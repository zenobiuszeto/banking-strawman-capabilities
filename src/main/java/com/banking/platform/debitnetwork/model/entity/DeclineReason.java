package com.banking.platform.debitnetwork.model.entity;

public enum DeclineReason {
    INSUFFICIENT_FUNDS,
    CARD_BLOCKED,
    CARD_EXPIRED,
    DAILY_LIMIT_EXCEEDED,
    MONTHLY_LIMIT_EXCEEDED,
    INVALID_CARD,
    SUSPECTED_FRAUD,
    MERCHANT_NOT_ALLOWED
}

