package com.banking.platform.ach.controller;

import com.banking.platform.ach.model.dto.AchReturnRequest;
import com.banking.platform.ach.model.dto.AchSearchRequest;
import com.banking.platform.ach.model.dto.AchTransferResponse;
import com.banking.platform.ach.model.dto.InitiateAchRequest;
import com.banking.platform.ach.service.AchService;
import com.banking.platform.shared.util.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.UUID;

/**
 * REST API controller for ACH transfer operations.
 * Provides endpoints for initiating, managing, and tracking ACH transfers.
 */
@RestController
@RequestMapping("/api/v1/ach")
@Slf4j
@RequiredArgsConstructor
public class AchController {

    private final AchService achService;

    /**
     * Initiate a new ACH transfer.
     * Creates a new ACH transfer record and publishes an initiation event.
     *
     * @param request the transfer initiation request
     * @return 201 Created with the new transfer details
     */
    @PostMapping("/transfers")
    public ResponseEntity<AchTransferResponse> initiateTransfer(@Valid @RequestBody InitiateAchRequest request) {
        log.info("Received request to initiate ACH transfer for account: {}", request.accountId());
        AchTransferResponse response = achService.initiateTransfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get an ACH transfer by its ID.
     *
     * @param id the transfer ID
     * @return 200 OK with transfer details
     */
    @GetMapping("/transfers/{id}")
    public ResponseEntity<AchTransferResponse> getTransferById(@PathVariable UUID id) {
        log.debug("Received request to fetch ACH transfer: {}", id);
        AchTransferResponse response = achService.getTransfer(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Get an ACH transfer by its trace number.
     *
     * @param traceNumber the trace number
     * @return 200 OK with transfer details
     */
    @GetMapping("/transfers/trace/{traceNumber}")
    public ResponseEntity<AchTransferResponse> getTransferByTraceNumber(@PathVariable String traceNumber) {
        log.debug("Received request to fetch ACH transfer by trace number: {}", traceNumber);
        AchTransferResponse response = achService.getByTraceNumber(traceNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * List ACH transfers for an account with pagination.
     *
     * @param accountId the account ID
     * @param page zero-indexed page number (default: 0)
     * @param size page size (default: 20)
     * @return 200 OK with paginated transfer list
     */
    @GetMapping("/transfers/account/{accountId}")
    public ResponseEntity<PagedResponse<AchTransferResponse>> listTransfers(
        @PathVariable UUID accountId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("Received request to list ACH transfers for account: {}", accountId);
        PagedResponse<AchTransferResponse> response = achService.listTransfers(accountId, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Search ACH transfers based on filter criteria.
     * Supports filtering by account ID, direction, status, and date range.
     *
     * @param searchRequest the search criteria
     * @param page zero-indexed page number (default: 0)
     * @param size page size (default: 20)
     * @return 200 OK with paginated search results
     */
    @PostMapping("/transfers/search")
    public ResponseEntity<PagedResponse<AchTransferResponse>> searchTransfers(
        @Valid @RequestBody AchSearchRequest searchRequest,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("Received request to search ACH transfers with criteria: {}", searchRequest);
        PagedResponse<AchTransferResponse> response = achService.searchTransfers(searchRequest, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel an ACH transfer.
     * Only transfers in INITIATED or PENDING_APPROVAL status can be cancelled.
     *
     * @param id the transfer ID
     * @return 200 OK with updated transfer details
     */
    @PostMapping("/transfers/{id}/cancel")
    public ResponseEntity<AchTransferResponse> cancelTransfer(@PathVariable UUID id) {
        log.info("Received request to cancel ACH transfer: {}", id);
        AchTransferResponse response = achService.cancelTransfer(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Approve an ACH transfer.
     * Transitions transfer from INITIATED/PENDING_APPROVAL to APPROVED status.
     *
     * @param id the transfer ID
     * @return 200 OK with updated transfer details
     */
    @PostMapping("/transfers/{id}/approve")
    public ResponseEntity<AchTransferResponse> approveTransfer(@PathVariable UUID id) {
        log.info("Received request to approve ACH transfer: {}", id);
        AchTransferResponse response = achService.approveTransfer(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Process a return for an ACH transfer.
     * Updates the transfer to RETURNED status with reason information.
     *
     * @param request the return request with trace number and reason
     * @return 200 OK with updated transfer details
     */
    @PostMapping("/returns")
    public ResponseEntity<AchTransferResponse> processReturn(@Valid @RequestBody AchReturnRequest request) {
        log.info("Received request to process return for ACH transfer: {}", request.traceNumber());
        AchTransferResponse response = achService.processReturn(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Submit an approved ACH transfer to the Federal Reserve.
     *
     * @param id the transfer ID
     * @return 200 OK with updated transfer details
     */
    @PostMapping("/transfers/{id}/submit")
    public ResponseEntity<AchTransferResponse> submitToFed(@PathVariable UUID id) {
        log.info("Received request to submit ACH transfer to Federal Reserve: {}", id);
        AchTransferResponse response = achService.submitToFed(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark an ACH transfer as settled.
     *
     * @param id the transfer ID
     * @return 200 OK with updated transfer details
     */
    @PostMapping("/transfers/{id}/settle")
    public ResponseEntity<AchTransferResponse> settleTransfer(@PathVariable UUID id) {
        log.info("Received request to settle ACH transfer: {}", id);
        AchTransferResponse response = achService.settleTransfer(id);
        return ResponseEntity.ok(response);
    }
}
