package com.banking.platform.debitnetwork.controller;

import com.banking.platform.debitnetwork.model.dto.*;
import com.banking.platform.debitnetwork.service.DebitCardService;
import com.banking.platform.debitnetwork.service.SettlementService;
import com.banking.platform.shared.util.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/debit-network")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Debit Network", description = "Debit card management, authorization, and settlement")
public class DebitNetworkController {

    private final DebitCardService debitCardService;
    private final SettlementService settlementService;

    @PostMapping("/cards")
    @Operation(summary = "Issue a new debit card")
    public ResponseEntity<DebitCardResponse> issueCard(@Valid @RequestBody IssueCardRequest request) {
        log.info("POST /debit-network/cards - Issue card for account: {}", request.accountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(debitCardService.issueCard(request));
    }

    @GetMapping("/cards/{cardId}")
    @Operation(summary = "Get debit card details")
    public ResponseEntity<DebitCardResponse> getCard(@PathVariable UUID cardId) {
        return ResponseEntity.ok(debitCardService.getCard(cardId));
    }

    @GetMapping("/cards/customer/{customerId}")
    @Operation(summary = "Get all cards for a customer")
    public ResponseEntity<List<DebitCardResponse>> getCustomerCards(@PathVariable UUID customerId) {
        return ResponseEntity.ok(debitCardService.getCustomerCards(customerId));
    }

    @PostMapping("/cards/{cardId}/block")
    @Operation(summary = "Block a debit card")
    public ResponseEntity<DebitCardResponse> blockCard(@PathVariable UUID cardId) {
        return ResponseEntity.ok(debitCardService.blockCard(cardId));
    }

    @PostMapping("/cards/{cardId}/unblock")
    @Operation(summary = "Unblock a debit card")
    public ResponseEntity<DebitCardResponse> unblockCard(@PathVariable UUID cardId) {
        return ResponseEntity.ok(debitCardService.unblockCard(cardId));
    }

    @PatchMapping("/cards/{cardId}/limits")
    @Operation(summary = "Update card spending limits")
    public ResponseEntity<DebitCardResponse> updateLimits(
            @PathVariable UUID cardId,
            @RequestParam(required = false) BigDecimal dailyLimit,
            @RequestParam(required = false) BigDecimal monthlyLimit) {
        return ResponseEntity.ok(debitCardService.updateLimits(cardId, dailyLimit, monthlyLimit));
    }

    @PostMapping("/authorize")
    @Operation(summary = "Authorize a debit transaction")
    public ResponseEntity<AuthorizationResponse> authorize(@Valid @RequestBody AuthorizationRequest request) {
        log.info("POST /debit-network/authorize - card={}, amount={}", request.debitCardId(), request.amount());
        return ResponseEntity.ok(debitCardService.authorize(request));
    }

    @GetMapping("/transactions/card/{cardId}")
    @Operation(summary = "Get transactions for a debit card")
    public ResponseEntity<PagedResponse<DebitTransactionResponse>> getCardTransactions(
            @PathVariable UUID cardId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(debitCardService.getCardTransactions(cardId, page, size));
    }

    @GetMapping("/transactions/account/{accountId}")
    @Operation(summary = "Get debit transactions for an account")
    public ResponseEntity<PagedResponse<DebitTransactionResponse>> getAccountTransactions(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(debitCardService.getAccountTransactions(accountId, page, size));
    }

    @PostMapping("/transactions/{transactionId}/reverse")
    @Operation(summary = "Reverse a debit transaction")
    public ResponseEntity<Void> reverseTransaction(@PathVariable UUID transactionId) {
        settlementService.reverseTransaction(transactionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/settlements/batch")
    @Operation(summary = "Batch settle all authorized transactions")
    public ResponseEntity<SettlementResponse> settleTransactions() {
        return ResponseEntity.status(HttpStatus.CREATED).body(settlementService.settleAuthorizedTransactions());
    }

    @GetMapping("/settlements/{settlementId}")
    @Operation(summary = "Get settlement details")
    public ResponseEntity<SettlementResponse> getSettlement(@PathVariable UUID settlementId) {
        return ResponseEntity.ok(settlementService.getSettlement(settlementId));
    }

    @GetMapping("/settlements")
    @Operation(summary = "List all settlements")
    public ResponseEntity<PagedResponse<SettlementResponse>> listSettlements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(settlementService.listSettlements(page, size));
    }
}

