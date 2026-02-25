package com.banking.platform.ach.model.entity;

/**
 * ACH direction enum representing the direction of the transfer.
 */
public enum AchDirection {
    /**
     * Incoming ACH transfer (debit to our account)
     */
    INBOUND,

    /**
     * Outgoing ACH transfer (credit from our account)
     */
    OUTBOUND
}
