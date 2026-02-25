package com.banking.platform.transaction.model.entity;

/**
 * Enumeration of transaction types.
 * Indicates whether a transaction is a credit (money in) or debit (money out).
 */
public enum TransactionType {
    /**
     * Money flowing into the account.
     */
    CREDIT,

    /**
     * Money flowing out of the account.
     */
    DEBIT
}
