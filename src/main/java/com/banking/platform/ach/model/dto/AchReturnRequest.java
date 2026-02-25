package com.banking.platform.ach.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for processing an ACH return.
 */
public record AchReturnRequest(
    /**
     * The trace number of the ACH transfer being returned
     */
    @NotBlank(message = "Trace number is required")
    String traceNumber,

    /**
     * The return reason code (typically 3 characters)
     */
    @NotBlank(message = "Return reason code is required")
    String returnReasonCode,

    /**
     * Optional detailed description of the return reason
     */
    String returnDescription
) {
}
