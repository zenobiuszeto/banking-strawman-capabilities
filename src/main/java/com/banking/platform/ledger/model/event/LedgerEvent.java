package com.banking.platform.ledger.model.event;

import com.banking.platform.ledger.model.entity.JournalEntryStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class LedgerEvent {
    String eventId;
    String eventType; // "journal_entry.created", "journal_entry.posted", "journal_entry.reversed"
    Instant timestamp;
    UUID journalEntryId;
    String entryNumber;
    JournalEntryStatus status;
    BigDecimal totalAmount;
    String referenceType;
    UUID referenceId;
}

