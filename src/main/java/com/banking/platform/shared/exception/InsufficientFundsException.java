package com.banking.platform.shared.exception;

public class InsufficientFundsException extends BusinessException {

    public InsufficientFundsException(String message) {
        super("INSUFFICIENT_FUNDS", message);
    }
}
