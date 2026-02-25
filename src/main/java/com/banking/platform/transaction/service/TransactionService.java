package com.banking.platform.transaction.service;

import com.banking.platform.shared.exception.EntityNotFoundException;
import com.banking.platform.shared.exception.ValidationException;
import com.banking.platform.shared.util.PagedResponse;
import com.banking.platform.transaction.mapper.TransactionMapper;
import com.banking.platform.transaction.model.dto.CreateTransactionRequest;
import com.banking.platform.transaction.model.dto.TransactionResponse;
import com.banking.platform.transaction.model.dto.TransactionSearchRequest;
import com.banking.platform.transaction.model.dto.TransactionSummaryResponse;
import com.banking.platform.transaction.model.entity.Transaction;
import com.banking.platform.transaction.model.entity.TransactionStatus;
import com.banking.platform.transaction.model.event.TransactionEvent;
import com.banking.platform.transaction.repository.TransactionRepository;
import com.banking.platform.transaction.repository.TransactionSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for managing transactions.
 * Handles business logic for transaction creation, retrieval, searching, and reversal.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    private static final String TRANSACTION_TOPIC = "banking.transactions";
    private static final String REFERENCE_PREFIX = "TXN";

    /**
     * Create a new transaction.
     * Generates a unique reference number, saves the transaction, and publishes an event.
     *
     * @param request the create transaction request
     * @return the created transaction response
     */
    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        log.info("Creating transaction for account: {}", request.accountId());

        // Convert request to entity
        Transaction transaction = transactionMapper.toEntity(request);

        // Generate unique reference number
        transaction.setReferenceNumber(generateReferenceNumber());

        // Set initial status
        transaction.setStatus(TransactionStatus.PENDING);

        // Validate transaction
        validateTransaction(transaction);

        // Save transaction
        Transaction saved = transactionRepository.save(transaction);
        log.info("Transaction created with ID: {} and reference: {}", saved.getId(), saved.getReferenceNumber());

        // Publish event
        publishTransactionEvent(saved, "transaction.created");

        return transactionMapper.toResponse(saved);
    }

    /**
     * Retrieve a transaction by its ID.
     *
     * @param transactionId the transaction ID
     * @return the transaction response
     * @throws EntityNotFoundException if transaction not found
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID transactionId) {
        log.debug("Fetching transaction: {}", transactionId);
        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new EntityNotFoundException("Transaction not found with ID: " + transactionId));
        return transactionMapper.toResponse(transaction);
    }

    /**
     * Retrieve a transaction by its unique reference number.
     *
     * @param referenceNumber the reference number
     * @return the transaction response
     * @throws EntityNotFoundException if transaction not found
     */
    @Transactional(readOnly = true)
    public TransactionResponse getByReference(String referenceNumber) {
        log.debug("Fetching transaction by reference: {}", referenceNumber);
        Transaction transaction = transactionRepository.findByReferenceNumber(referenceNumber)
            .orElseThrow(() -> new EntityNotFoundException("Transaction not found with reference: " + referenceNumber));
        return transactionMapper.toResponse(transaction);
    }

    /**
     * List transactions for an account with pagination.
     *
     * @param accountId the account ID
     * @param page the page number (zero-indexed)
     * @param size the page size
     * @return a paged response containing transactions
     */
    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> listTransactions(UUID accountId, int page, int size) {
        log.debug("Listing transactions for account: {}, page: {}, size: {}", accountId, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Transaction> transactionPage = transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);

        return PagedResponse.of(
            transactionPage.map(transactionMapper::toResponse),
            page,
            size,
            transactionPage.getTotalElements(),
            transactionPage.getTotalPages()
        );
    }

    /**
     * Search transactions with advanced filtering.
     * Supports optional filters for type, category, status, amount range, and keyword.
     *
     * @param request the search request with filter criteria
     * @param page the page number (zero-indexed)
     * @param size the page size
     * @return a paged response of matching transactions
     */
    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> searchTransactions(TransactionSearchRequest request, int page, int size) {
        log.debug("Searching transactions with criteria: {}", request);

        Pageable pageable = PageRequest.of(page, size, Sort.by("postDate").descending());

        // Build specification from request
        var specification = TransactionSpecification.buildSearchSpecification(request);

        // Execute search
        Page<Transaction> transactionPage = transactionRepository.findAll(specification, pageable);

        return PagedResponse.of(
            transactionPage.map(transactionMapper::toResponse),
            page,
            size,
            transactionPage.getTotalElements(),
            transactionPage.getTotalPages()
        );
    }

    /**
     * Get a summary of transactions for an account over a date range.
     * Provides totals for credits, debits, and transaction count.
     *
     * @param accountId the account ID
     * @param fromDate the start date (inclusive)
     * @param toDate the end date (inclusive)
     * @return the transaction summary
     */
    @Transactional(readOnly = true)
    public TransactionSummaryResponse getTransactionSummary(UUID accountId, LocalDate fromDate, LocalDate toDate) {
        log.debug("Getting transaction summary for account: {} from {} to {}", accountId, fromDate, toDate);

        BigDecimal totalCredits = transactionRepository.sumByAccountIdAndTypeAndDateRange(
            accountId,
            com.banking.platform.transaction.model.entity.TransactionType.CREDIT,
            fromDate,
            toDate
        );

        BigDecimal totalDebits = transactionRepository.sumByAccountIdAndTypeAndDateRange(
            accountId,
            com.banking.platform.transaction.model.entity.TransactionType.DEBIT,
            fromDate,
            toDate
        );

        int transactionCount = transactionRepository.countByAccountIdAndPostDateBetween(accountId, fromDate, toDate);

        BigDecimal netChange = totalCredits.subtract(totalDebits);

        return new TransactionSummaryResponse(
            accountId,
            totalCredits,
            totalDebits,
            netChange,
            transactionCount,
            fromDate,
            toDate
        );
    }

    /**
     * Reverse a transaction.
     * Creates a reversal entry and updates the original transaction status.
     *
     * @param transactionId the transaction ID to reverse
     * @return the reversed transaction response
     * @throws EntityNotFoundException if transaction not found
     * @throws ValidationException if transaction cannot be reversed
     */
    @Transactional
    public TransactionResponse reverseTransaction(UUID transactionId) {
        log.info("Reversing transaction: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new EntityNotFoundException("Transaction not found with ID: " + transactionId));

        // Validate reversibility
        if (transaction.getStatus() == TransactionStatus.REVERSED) {
            throw new ValidationException("Transaction is already reversed");
        }

        if (transaction.getStatus() == TransactionStatus.FAILED) {
            throw new ValidationException("Cannot reverse a failed transaction");
        }

        // Update original transaction status
        transaction.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(transaction);

        log.info("Transaction reversed: {}", transactionId);

        // Publish reversal event
        publishTransactionEvent(transaction, "transaction.reversed");

        return transactionMapper.toResponse(transaction);
    }

    /**
     * Validate transaction data.
     * Ensures required fields are properly set and consistent.
     *
     * @param transaction the transaction to validate
     * @throws ValidationException if validation fails
     */
    private void validateTransaction(Transaction transaction) {
        if (transaction.getAccountId() == null) {
            throw new ValidationException("Account ID is required");
        }

        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be positive");
        }

        if (transaction.getType() == null) {
            throw new ValidationException("Transaction type is required");
        }

        if (transaction.getCategory() == null) {
            throw new ValidationException("Transaction category is required");
        }

        if (transaction.getPostDate() == null) {
            throw new ValidationException("Post date is required");
        }

        if (transaction.getEffectiveDate() == null) {
            throw new ValidationException("Effective date is required");
        }
    }

    /**
     * Generate a unique reference number for a transaction.
     * Format: TXN-TIMESTAMP-UUID (first 8 chars)
     *
     * @return a unique reference number
     */
    private String generateReferenceNumber() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("%s-%s-%s", REFERENCE_PREFIX, timestamp, uuid);
    }

    /**
     * Publish a transaction event to Kafka.
     *
     * @param transaction the transaction entity
     * @param eventType the type of event
     */
    private void publishTransactionEvent(Transaction transaction, String eventType) {
        TransactionEvent event = TransactionEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(eventType)
            .timestamp(Instant.now())
            .transactionId(transaction.getId())
            .accountId(transaction.getAccountId())
            .type(transaction.getType())
            .category(transaction.getCategory())
            .amount(transaction.getAmount())
            .status(transaction.getStatus())
            .build();

        try {
            kafkaTemplate.send(TRANSACTION_TOPIC, event.getTransactionId().toString(), event);
            log.debug("Published {} event for transaction: {}", eventType, transaction.getId());
        } catch (Exception e) {
            log.error("Failed to publish event for transaction: {}", transaction.getId(), e);
            // Log error but don't fail the transaction - events are best-effort
        }
    }
}
