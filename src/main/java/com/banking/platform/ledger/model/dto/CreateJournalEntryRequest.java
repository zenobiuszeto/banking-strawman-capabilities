package com.banking.platform.ledger.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateJournalEntryRequest(
        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Posting date is required")
        LocalDate postingDate,

        UUID referenceId,

        String referenceType,

        String createdBy,

        @NotEmpty(message = "At least one line is required")
        List<JournalEntryLineRequest> lines
) {
    public record JournalEntryLineRequest(
            @NotBlank(message = "Account code is required")
            String accountCode,

            java.math.BigDecimal debitAmount,

            java.math.BigDecimal creditAmount,

            String description
    ) {
    }
}

