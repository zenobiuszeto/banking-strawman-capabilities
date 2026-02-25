package com.banking.platform.transaction.model.entity;

/**
 * Enumeration of transaction categories.
 * Provides detailed classification of transaction purpose and method.
 */
public enum TransactionCategory {
    /**
     * Direct deposit or cash deposit to account.
     */
    DEPOSIT,

    /**
     * Cash withdrawal or debit card withdrawal.
     */
    WITHDRAWAL,

    /**
     * Transfer received from another account.
     */
    TRANSFER_IN,

    /**
     * Transfer sent to another account.
     */
    TRANSFER_OUT,

    /**
     * Automated Clearing House credit (typically payroll).
     */
    ACH_CREDIT,

    /**
     * Automated Clearing House debit (typically bill payment).
     */
    ACH_DEBIT,

    /**
     * Wire transfer received.
     */
    WIRE_IN,

    /**
     * Wire transfer sent.
     */
    WIRE_OUT,

    /**
     * Interest earned on account.
     */
    INTEREST,

    /**
     * Account fee or service charge.
     */
    FEE,

    /**
     * Refund received.
     */
    REFUND,

    /**
     * Manual adjustment by administrator.
     */
    ADJUSTMENT,

    /**
     * Point-of-sale debit card purchase.
     */
    POS_PURCHASE,

    /**
     * ATM cash withdrawal.
     */
    ATM_WITHDRAWAL
}
