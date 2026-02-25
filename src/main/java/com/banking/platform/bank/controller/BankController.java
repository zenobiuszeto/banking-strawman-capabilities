package com.banking.platform.bank.controller;

import com.banking.platform.bank.model.dto.BankDirectoryResponse;
import com.banking.platform.bank.model.dto.LinkBankRequest;
import com.banking.platform.bank.model.dto.LinkedBankResponse;
import com.banking.platform.bank.model.dto.VerifyMicroDepositsRequest;
import com.banking.platform.bank.service.BankService;
import com.banking.platform.shared.util.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/banks")
@RequiredArgsConstructor
@Slf4j
@Validated
public class BankController {

    private final BankService bankService;

    @PostMapping("/linked")
    public ResponseEntity<LinkedBankResponse> linkBank(@Valid @RequestBody LinkBankRequest request) {
        log.info("POST /api/v1/banks/linked - Linking bank for customer: {}", request.customerId());
        LinkedBankResponse response = bankService.linkBank(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/linked/customer/{customerId}")
    public ResponseEntity<List<LinkedBankResponse>> getLinkedBanks(@PathVariable UUID customerId) {
        log.info("GET /api/v1/banks/linked/customer/{} - Fetching linked banks", customerId);
        List<LinkedBankResponse> response = bankService.getLinkedBanks(customerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/linked/{id}")
    public ResponseEntity<LinkedBankResponse> getLinkedBank(@PathVariable UUID id) {
        log.info("GET /api/v1/banks/linked/{} - Fetching linked bank details", id);
        LinkedBankResponse response = bankService.getLinkedBank(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/linked/{id}/verify")
    public ResponseEntity<LinkedBankResponse> verifyMicroDeposits(
            @PathVariable UUID id,
            @Valid @RequestBody VerifyMicroDepositsRequest request) {
        log.info("POST /api/v1/banks/linked/{}/verify - Verifying micro-deposits", id);
        LinkedBankResponse response = bankService.verifyMicroDeposits(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/linked/{id}/primary")
    public ResponseEntity<Void> setPrimary(
            @PathVariable UUID id,
            @RequestParam UUID customerId) {
        log.info("PUT /api/v1/banks/linked/{}/primary - Setting as primary for customer: {}", id, customerId);
        bankService.setPrimary(customerId, id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/linked/{id}")
    public ResponseEntity<Void> removeBank(@PathVariable UUID id) {
        log.info("DELETE /api/v1/banks/linked/{} - Removing bank account", id);
        bankService.removeBank(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/directory/routing/{routingNumber}")
    public ResponseEntity<BankDirectoryResponse> lookupRouting(@PathVariable String routingNumber) {
        log.info("GET /api/v1/banks/directory/routing/{} - Looking up routing number", routingNumber);
        BankDirectoryResponse response = bankService.lookupRouting(routingNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/directory/search")
    public ResponseEntity<PagedResponse<BankDirectoryResponse>> searchBanks(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {
        log.info("GET /api/v1/banks/directory/search - Searching banks with query: {}", query);
        PagedResponse<BankDirectoryResponse> response = bankService.searchBanks(query, page, size);
        return ResponseEntity.ok(response);
    }
}
