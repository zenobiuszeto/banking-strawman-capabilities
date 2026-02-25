package com.banking.platform.ach.model.entity;

/**
 * ACH type enum representing different processing types.
 */
public enum AchType {
    /**
     * Standard ACH processing (1-2 business days)
     */
    STANDARD,

    /**
     * Same-day ACH processing (higher limits and costs)
     */
    SAME_DAY,

    /**
     * Next-day ACH processing
     */
    NEXT_DAY
}
