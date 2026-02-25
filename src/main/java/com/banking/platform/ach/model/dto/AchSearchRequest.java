package com.banking.platform.ach.model.dto;

import com.banking.platform.ach.model.entity.AchDirection;
import com.banking.platform.ach.model.entity.AchStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching ACH transfers with various filter criteria.
 */
public record AchSearchRequest(
    /**
     * Filter by account ID
     */
    UUID accountId,

    /**
     * Filter by transfer direction
     */
    AchDirection direction,

    /**
     * Filter by transfer status
     */
    AchStatus status,

    /**
     * Filter from date (inclusive)
     */
    LocalDate fromDate,

    /**
     * Filter to date (inclusive)
     */
    LocalDate toDate
) {
}
