package com.banking.platform.ledger.controller;

import com.banking.platform.ledger.model.dto.*;
import com.banking.platform.ledger.model.entity.JournalEntryStatus;
import com.banking.platform.ledger.service.LedgerService;
import com.banking.platform.shared.util.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ledger", description = "Double-entry accounting ledger management")
public class LedgerController {

    private final LedgerService ledgerService;

    // ==================== GL Accounts ====================

    @PostMapping("/gl-accounts")
    @Operation(summary = "Create a GL account")
    public ResponseEntity<GlAccountResponse> createGlAccount(@Valid @RequestBody CreateGlAccountRequest request) {
        log.info("POST /ledger/gl-accounts - Create GL account: {}", request.accountCode());
        GlAccountResponse response = ledgerService.createGlAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/gl-accounts")
    @Operation(summary = "Get all active GL accounts (chart of accounts)")
    public ResponseEntity<List<GlAccountResponse>> getAllGlAccounts() {
        log.info("GET /ledger/gl-accounts");
        List<GlAccountResponse> response = ledgerService.getAllGlAccounts();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/gl-accounts/{id}")
    @Operation(summary = "Get GL account by ID")
    public ResponseEntity<GlAccountResponse> getGlAccount(@PathVariable UUID id) {
        log.info("GET /ledger/gl-accounts/{}", id);
        GlAccountResponse response = ledgerService.getGlAccount(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/gl-accounts/code/{accountCode}")
    @Operation(summary = "Get GL account by code")
    public ResponseEntity<GlAccountResponse> getGlAccountByCode(@PathVariable String accountCode) {
        log.info("GET /ledger/gl-accounts/code/{}", accountCode);
        GlAccountResponse response = ledgerService.getGlAccountByCode(accountCode);
        return ResponseEntity.ok(response);
    }

    // ==================== Journal Entries ====================

    @PostMapping("/journal-entries")
    @Operation(summary = "Create a journal entry")
    public ResponseEntity<JournalEntryResponse> createJournalEntry(
            @Valid @RequestBody CreateJournalEntryRequest request) {
        log.info("POST /ledger/journal-entries - {}", request.description());
        JournalEntryResponse response = ledgerService.createJournalEntry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/journal-entries/{id}")
    @Operation(summary = "Get journal entry by ID")
    public ResponseEntity<JournalEntryResponse> getJournalEntry(@PathVariable UUID id) {
        log.info("GET /ledger/journal-entries/{}", id);
        JournalEntryResponse response = ledgerService.getJournalEntry(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/journal-entries")
    @Operation(summary = "List journal entries with optional status filter")
    public ResponseEntity<PagedResponse<JournalEntryResponse>> listJournalEntries(
            @RequestParam(required = false) JournalEntryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /ledger/journal-entries - status: {}, page: {}, size: {}", status, page, size);
        PagedResponse<JournalEntryResponse> response = ledgerService.getJournalEntries(status, page, size);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/journal-entries/{id}/post")
    @Operation(summary = "Post a draft journal entry")
    public ResponseEntity<JournalEntryResponse> postJournalEntry(@PathVariable UUID id) {
        log.info("POST /ledger/journal-entries/{}/post", id);
        JournalEntryResponse response = ledgerService.postJournalEntry(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/journal-entries/{id}/reverse")
    @Operation(summary = "Reverse a posted journal entry")
    public ResponseEntity<JournalEntryResponse> reverseJournalEntry(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "Manual reversal") String reason) {
        log.info("POST /ledger/journal-entries/{}/reverse - reason: {}", id, reason);
        JournalEntryResponse response = ledgerService.reverseJournalEntry(id, reason);
        return ResponseEntity.ok(response);
    }

    // ==================== Trial Balance ====================

    @GetMapping("/trial-balance")
    @Operation(summary = "Generate trial balance from posted journal entries")
    public ResponseEntity<TrialBalanceResponse> getTrialBalance() {
        log.info("GET /ledger/trial-balance");
        TrialBalanceResponse response = ledgerService.getTrialBalance();
        return ResponseEntity.ok(response);
    }

    // ==================== Posting Rules ====================

    @PostMapping("/posting-rules")
    @Operation(summary = "Create a posting rule for automated journal entries")
    public ResponseEntity<PostingRuleResponse> createPostingRule(
            @Valid @RequestBody PostingRuleRequest request) {
        log.info("POST /ledger/posting-rules - {}", request.ruleCode());
        PostingRuleResponse response = ledgerService.createPostingRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/posting-rules")
    @Operation(summary = "Get all active posting rules")
    public ResponseEntity<List<PostingRuleResponse>> getAllPostingRules() {
        log.info("GET /ledger/posting-rules");
        List<PostingRuleResponse> response = ledgerService.getAllPostingRules();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/posting-rules/{id}/toggle")
    @Operation(summary = "Activate or deactivate a posting rule")
    public ResponseEntity<Void> togglePostingRule(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        log.info("PATCH /ledger/posting-rules/{}/toggle - active={}", id, active);
        ledgerService.togglePostingRule(id, active);
        return ResponseEntity.ok().build();
    }
}

