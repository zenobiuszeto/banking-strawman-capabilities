package com.banking.platform.shared.saga;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Temporal Activity interface for Transaction Saga steps.
 *
 * Each method is a durable activity step. Temporal retries failed activities
 * automatically per the options in TransactionSagaWorkflowImpl.
 *
 * Compensating methods (unreserve*, reverse*, void*) are registered as
 * saga compensations before each forward step is executed.
 */
@ActivityInterface
public interface TransactionSagaActivities {

    // ── Forward steps ────────────────────────────────────────────────────────

    @ActivityMethod
    void reserveFunds(UUID accountId, BigDecimal amount);

    @ActivityMethod
    String submitToPaymentNetwork(UUID referenceId, BigDecimal amount, String currency);

    @ActivityMethod
    UUID postToGeneralLedger(UUID accountId, UUID destinationAccountId,
                             BigDecimal amount, String description, UUID referenceId);

    @ActivityMethod
    void publishDomainEvent(UUID transactionId, String eventType, String referenceId);

    // ── Compensating steps ───────────────────────────────────────────────────

    @ActivityMethod
    void unreserveFunds(UUID accountId, BigDecimal amount);

    @ActivityMethod
    void voidNetworkSubmission(String networkReference);

    @ActivityMethod
    void reverseJournalEntry(UUID journalEntryId, String reason);
}

