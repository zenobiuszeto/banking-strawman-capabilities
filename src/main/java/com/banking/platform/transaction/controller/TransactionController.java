package com.banking.platform.transaction.controller;

import com.banking.platform.shared.util.PagedResponse;
import com.banking.platform.transaction.model.dto.CreateTransactionRequest;
import com.banking.platform.transaction.model.dto.TransactionResponse;
import com.banking.platform.transaction.model.dto.TransactionSearchRequest;
import com.banking.platform.transaction.model.dto.TransactionSummaryResponse;
import com.banking.platform.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST Controller for transaction operations.
 * Provides endpoints for creating, retrieving, searching, and reversing transactions.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Create a new transaction.
     *
     * @param request the create transaction request
     * @return 201 Created with the created transaction
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse response = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get a transaction by ID.
     *
     * @param id the transaction ID
     * @return 200 OK with the transaction details
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable UUID id) {
        TransactionResponse response = transactionService.getTransaction(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a transaction by its reference number.
     *
     * @param refNumber the reference number
     * @return 200 OK with the transaction details
     */
    @GetMapping("/reference/{refNumber}")
    public ResponseEntity<TransactionResponse> getByReference(@PathVariable String refNumber) {
        TransactionResponse response = transactionService.getByReference(refNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * List transactions for an account with pagination.
     *
     * @param accountId the account ID
     * @param page the page number (zero-indexed, default: 0)
     * @param size the page size (default: 20)
     * @return 200 OK with paginated transactions
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<PagedResponse<TransactionResponse>> listByAccount(
        @PathVariable UUID accountId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<TransactionResponse> response = transactionService.listTransactions(accountId, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Search transactions with advanced filtering.
     * Supports optional filters for type, category, status, amount range, and keyword.
     *
     * @param request the search request with filter criteria
     * @param page the page number (zero-indexed, default: 0)
     * @param size the page size (default: 20)
     * @return 200 OK with paginated search results
     */
    @PostMapping("/search")
    public ResponseEntity<PagedResponse<TransactionResponse>> search(
        @Valid @RequestBody TransactionSearchRequest request,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<TransactionResponse> response = transactionService.searchTransactions(request, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a summary of transactions for an account over a date range.
     *
     * @param accountId the account ID
     * @param from the start date (required)
     * @param to the end date (required)
     * @return 200 OK with the transaction summary
     */
    @GetMapping("/account/{accountId}/summary")
    public ResponseEntity<TransactionSummaryResponse> getSummary(
        @PathVariable UUID accountId,
        @RequestParam LocalDate from,
        @RequestParam LocalDate to
    ) {
        TransactionSummaryResponse response = transactionService.getTransactionSummary(accountId, from, to);
        return ResponseEntity.ok(response);
    }

    /**
     * Reverse a transaction.
     *
     * @param id the transaction ID to reverse
     * @return 200 OK with the reversed transaction details
     */
    @PostMapping("/{id}/reverse")
    public ResponseEntity<TransactionResponse> reverseTransaction(@PathVariable UUID id) {
        TransactionResponse response = transactionService.reverseTransaction(id);
        return ResponseEntity.ok(response);
    }
}
