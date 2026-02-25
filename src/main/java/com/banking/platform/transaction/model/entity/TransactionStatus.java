package com.banking.platform.transaction.model.entity;

/**
 * Enumeration of transaction statuses.
 * Represents the state of a transaction through its lifecycle.
 */
public enum TransactionStatus {
    /**
     * Transaction has been initiated but not yet processed.
     */
    PENDING,

    /**
     * Transaction has been posted to the account.
     */
    POSTED,

    /**
     * Transaction processing failed.
     */
    FAILED,

    /**
     * Transaction has been reversed or cancelled.
     */
    REVERSED,

    /**
     * Transaction is on hold pending review or clearance.
     */
    ON_HOLD
}
