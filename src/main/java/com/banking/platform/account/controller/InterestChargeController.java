package com.banking.platform.account.controller;

import com.banking.platform.account.model.dto.InterestChargeResponse;
import com.banking.platform.account.service.InterestChargeService;
import com.banking.platform.shared.util.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/charges")
@RequiredArgsConstructor
@Slf4j
public class InterestChargeController {

    private final InterestChargeService interestChargeService;

    @GetMapping
    public ResponseEntity<PagedResponse<InterestChargeResponse>> listCharges(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Received request to list charges for account: {} with page: {} and size: {}", accountId, page, size);

        PagedResponse<InterestChargeResponse> response = interestChargeService.getInterestCharges(accountId, page, size);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/range")
    public ResponseEntity<List<InterestChargeResponse>> getChargesByDateRange(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("Received request to get charges for account: {} between {} and {}", accountId, from, to);

        List<InterestChargeResponse> response = interestChargeService.getChargesByDateRange(accountId, from, to);

        return ResponseEntity.ok(response);
    }
}
