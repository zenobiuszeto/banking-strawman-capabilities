package com.banking.platform.ach.model.entity;

/**
 * ACH status enum representing the lifecycle state of an ACH transfer.
 */
public enum AchStatus {
    /**
     * Transfer has been initiated but not yet submitted for approval
     */
    INITIATED,

    /**
     * Transfer is pending approval from authorized personnel
     */
    PENDING_APPROVAL,

    /**
     * Transfer has been approved and ready for submission
     */
    APPROVED,

    /**
     * Transfer has been submitted to the Federal Reserve
     */
    SUBMITTED_TO_FED,

    /**
     * Transfer is currently being processed
     */
    PROCESSING,

    /**
     * Transfer has been settled/completed
     */
    SETTLED,

    /**
     * Transfer has been returned by the receiving bank
     */
    RETURNED,

    /**
     * Transfer failed to process
     */
    FAILED,

    /**
     * Transfer has been cancelled by the user
     */
    CANCELLED
}
