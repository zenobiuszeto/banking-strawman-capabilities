package com.banking.platform.ledger.model.dto;

import com.banking.platform.ledger.model.entity.JournalEntryStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JournalEntryResponse(
        UUID id,
        String entryNumber,
        String description,
        LocalDate postingDate,
        JournalEntryStatus status,
        UUID referenceId,
        String referenceType,
        String createdBy,
        UUID reversalOfId,
        List<JournalEntryLineResponse> lines,
        Instant createdAt
) {
}

