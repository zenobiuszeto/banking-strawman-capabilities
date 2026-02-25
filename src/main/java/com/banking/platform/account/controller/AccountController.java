package com.banking.platform.account.controller;

import com.banking.platform.account.model.dto.*;
import com.banking.platform.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        log.info("Received request to create account for customer: {}", request.customerId());

        AccountResponse response = accountService.createAccount(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id) {
        log.info("Received request to get account: {}", id);

        AccountResponse response = accountService.getAccount(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccountByNumber(@PathVariable String accountNumber) {
        log.info("Received request to get account by number: {}", accountNumber);

        AccountResponse response = accountService.getAccountByNumber(accountNumber);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<AccountResponse>> getCustomerAccounts(@PathVariable UUID customerId) {
        log.info("Received request to get accounts for customer: {}", customerId);

        List<AccountResponse> response = accountService.getCustomerAccounts(customerId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        log.info("Received request to update account status for: {}", id);

        AccountResponse response = accountService.updateAccountStatus(id, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceDetailResponse> getBalanceDetails(@PathVariable UUID id) {
        log.info("Received request to get balance details for account: {}", id);

        BalanceDetailResponse response = accountService.getBalanceDetails(id);

        return ResponseEntity.ok(response);
    }
}
