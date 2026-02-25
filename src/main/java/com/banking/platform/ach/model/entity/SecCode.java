package com.banking.platform.ach.model.entity;

/**
 * Standard Entry Class (SEC) codes for ACH transfers.
 * These codes identify the specific type of ACH entry.
 */
public enum SecCode {
    /**
     * Prearranged Payment and Deposit - used for recurring bill payments and payroll deposits
     */
    PPD,

    /**
     * Corporate Credit or Debit - used for business-to-business transactions
     */
    CCD,

    /**
     * Internet-initiated entry - used for internet-initiated debit transactions
     */
    WEB,

    /**
     * Telephone-initiated entry - used for telephone-initiated debit transactions
     */
    TEL
}
