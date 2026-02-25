package com.banking.platform.rewards.controller;

import com.banking.platform.rewards.model.dto.EarnPointsRequest;
import com.banking.platform.rewards.model.dto.RedeemPointsRequest;
import com.banking.platform.rewards.model.dto.RewardsAccountResponse;
import com.banking.platform.rewards.model.dto.RewardsOfferResponse;
import com.banking.platform.rewards.model.dto.RewardsSummaryResponse;
import com.banking.platform.rewards.model.dto.RewardsTransactionResponse;
import com.banking.platform.rewards.service.RewardsService;
import com.banking.platform.shared.util.PagedResponse;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rewards")
@Slf4j
@RequiredArgsConstructor
public class RewardsController {

    private final RewardsService rewardsService;

    @GetMapping("/account/{customerId}")
    public ResponseEntity<RewardsAccountResponse> getRewardsAccount(
            @PathVariable UUID customerId) {
        log.info("GET /account/{} - Fetching rewards account", customerId);
        RewardsAccountResponse response = rewardsService.getRewardsAccount(customerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/account/{customerId}/summary")
    public ResponseEntity<RewardsSummaryResponse> getRewardsSummary(
            @PathVariable UUID customerId) {
        log.info("GET /account/{}/summary - Fetching rewards summary", customerId);
        RewardsSummaryResponse response = rewardsService.getRewardsSummary(customerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/earn")
    public ResponseEntity<RewardsTransactionResponse> earnPoints(
            @Valid @RequestBody EarnPointsRequest request) {
        log.info("POST /earn - Earning points for customer: {}", request.customerId());
        RewardsTransactionResponse response = rewardsService.earnPoints(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/redeem")
    public ResponseEntity<RewardsTransactionResponse> redeemPoints(
            @Valid @RequestBody RedeemPointsRequest request) {
        log.info("POST /redeem - Redeeming points for customer: {}", request.customerId());
        RewardsTransactionResponse response = rewardsService.redeemPoints(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/transactions/{customerId}")
    public ResponseEntity<PagedResponse<RewardsTransactionResponse>> getTransactionHistory(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /transactions/{} - Fetching transaction history, page: {}, size: {}", customerId, page, size);
        PagedResponse<RewardsTransactionResponse> response = rewardsService.getTransactionHistory(customerId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/offers/{customerId}")
    public ResponseEntity<List<RewardsOfferResponse>> getActiveOffers(
            @PathVariable UUID customerId) {
        log.info("GET /offers/{} - Fetching active offers", customerId);
        List<RewardsOfferResponse> response = rewardsService.getActiveOffers(customerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/account/{customerId}/evaluate-tier")
    public ResponseEntity<Void> evaluateTierUpgrade(
            @PathVariable UUID customerId) {
        log.info("POST /account/{}/evaluate-tier - Evaluating tier upgrade", customerId);
        rewardsService.evaluateTierUpgrade(customerId);
        return ResponseEntity.ok().build();
    }
}
