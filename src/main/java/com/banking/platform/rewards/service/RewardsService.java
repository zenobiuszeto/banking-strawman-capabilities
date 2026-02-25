package com.banking.platform.rewards.service;

import com.banking.platform.rewards.mapper.RewardsMapper;
import com.banking.platform.rewards.model.dto.EarnPointsRequest;
import com.banking.platform.rewards.model.dto.RedeemPointsRequest;
import com.banking.platform.rewards.model.dto.RewardsAccountResponse;
import com.banking.platform.rewards.model.dto.RewardsOfferResponse;
import com.banking.platform.rewards.model.dto.RewardsSummaryResponse;
import com.banking.platform.rewards.model.dto.RewardsTransactionResponse;
import com.banking.platform.rewards.model.entity.RewardsAccount;
import com.banking.platform.rewards.model.entity.RewardsOffer;
import com.banking.platform.rewards.model.entity.RewardsTransaction;
import com.banking.platform.rewards.model.entity.RewardsTier;
import com.banking.platform.rewards.model.entity.RewardsTransactionType;
import com.banking.platform.rewards.model.event.RewardsEvent;
import com.banking.platform.rewards.repository.RewardsAccountRepository;
import com.banking.platform.rewards.repository.RewardsOfferRepository;
import com.banking.platform.rewards.repository.RewardsTransactionRepository;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import com.banking.platform.shared.util.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RewardsService {

    private final RewardsAccountRepository rewardsAccountRepository;
    private final RewardsTransactionRepository rewardsTransactionRepository;
    private final RewardsOfferRepository rewardsOfferRepository;
    private final RewardsMapper rewardsMapper;
    private final KafkaTemplate<String, RewardsEvent> kafkaTemplate;

    private static final String REWARDS_TOPIC = "rewards.events";

    @Cacheable(value = "rewards", key = "#customerId")
    public RewardsAccountResponse getRewardsAccount(UUID customerId) {
        log.debug("Fetching rewards account for customer: {}", customerId);

        RewardsAccount account = rewardsAccountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Rewards account not found for customer: " + customerId));

        return rewardsMapper.toAccountResponse(account);
    }

    public RewardsSummaryResponse getRewardsSummary(UUID customerId) {
        log.debug("Fetching rewards summary for customer: {}", customerId);

        RewardsAccount account = rewardsAccountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Rewards account not found for customer: " + customerId));

        // Calculate points to next tier
        RewardsTier nextTier = getNextTier(account.getTier());
        long pointsToNextTier = 0;
        if (nextTier != null) {
            pointsToNextTier = Math.max(0, nextTier.getMinPointsRequired() - account.getTotalPointsEarned());
        }

        // Calculate earned and redeemed this month
        YearMonth currentMonth = YearMonth.now();
        LocalDate startOfMonth = currentMonth.atDay(1);
        LocalDate endOfMonth = currentMonth.atEndOfMonth();

        long earnedThisMonth = rewardsTransactionRepository
                .findByRewardsAccountIdAndCreatedAtBetween(
                        account.getId(),
                        startOfMonth.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                        endOfMonth.atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant()
                )
                .stream()
                .filter(t -> t.getPoints() > 0)
                .mapToLong(RewardsTransaction::getPoints)
                .sum();

        long redeemedThisMonth = rewardsTransactionRepository
                .findByRewardsAccountIdAndCreatedAtBetween(
                        account.getId(),
                        startOfMonth.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                        endOfMonth.atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant()
                )
                .stream()
                .filter(t -> t.getPoints() < 0)
                .mapToLong(t -> Math.abs(t.getPoints()))
                .sum();

        BigDecimal cashEquivalent = BigDecimal.valueOf(account.getCurrentBalance())
                .multiply(BigDecimal.valueOf(0.01));

        return new RewardsSummaryResponse(
                customerId,
                account.getTier(),
                account.getCurrentBalance(),
                cashEquivalent,
                pointsToNextTier,
                nextTier,
                earnedThisMonth,
                redeemedThisMonth
        );
    }

    @Transactional
    @CacheEvict(value = "rewards", key = "#request.customerId")
    public RewardsTransactionResponse earnPoints(EarnPointsRequest request) {
        log.info("Earning points for customer: {}, basePoints: {}", request.customerId(), request.basePoints());

        RewardsAccount account = rewardsAccountRepository.findByCustomerId(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Rewards account not found for customer: " + request.customerId()));

        // Apply tier multiplier
        double multiplier = account.getTier().getPointsMultiplier();
        long earnedPoints = (long) (request.basePoints() * multiplier);

        // Update account
        long newBalance = account.getCurrentBalance() + earnedPoints;
        long newTotalEarned = account.getTotalPointsEarned() + earnedPoints;
        BigDecimal newLifetimeValue = account.getLifetimeValue()
                .add(BigDecimal.valueOf(earnedPoints).multiply(BigDecimal.valueOf(0.01)));

        account.setCurrentBalance(newBalance);
        account.setTotalPointsEarned(newTotalEarned);
        account.setLifetimeValue(newLifetimeValue);

        RewardsAccount updatedAccount = rewardsAccountRepository.save(account);

        // Create transaction record
        RewardsTransaction transaction = RewardsTransaction.builder()
                .id(UUID.randomUUID())
                .rewardsAccountId(account.getId())
                .sourceTransactionId(request.sourceTransactionId())
                .type(RewardsTransactionType.EARN_PURCHASE)
                .points(earnedPoints)
                .runningBalance(newBalance)
                .description(request.description())
                .createdAt(Instant.now())
                .build();

        RewardsTransaction savedTransaction = rewardsTransactionRepository.save(transaction);

        // Check for tier upgrade
        evaluateTierUpgrade(request.customerId());

        // Publish event
        publishRewardsEvent(
                UUID.randomUUID().toString(),
                "rewards.earned",
                Instant.now(),
                request.customerId(),
                account.getId(),
                earnedPoints,
                newBalance,
                account.getTier()
        );

        log.info("Points earned successfully. Account: {}, newBalance: {}", account.getId(), newBalance);

        return rewardsMapper.toTransactionResponse(savedTransaction);
    }

    @Transactional
    @CacheEvict(value = "rewards", key = "#request.customerId")
    public RewardsTransactionResponse redeemPoints(RedeemPointsRequest request) {
        log.info("Redeeming points for customer: {}, points: {}", request.customerId(), request.points());

        RewardsAccount account = rewardsAccountRepository.findByCustomerId(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Rewards account not found for customer: " + request.customerId()));

        // Validate sufficient balance
        if (account.getCurrentBalance() < request.points()) {
            log.warn("Insufficient points balance. Customer: {}, required: {}, available: {}",
                    request.customerId(), request.points(), account.getCurrentBalance());
            throw new IllegalArgumentException("Insufficient points balance. Available: " + account.getCurrentBalance());
        }

        // Update account
        long newBalance = account.getCurrentBalance() - request.points();
        long newTotalRedeemed = account.getTotalPointsRedeemed() + request.points();

        account.setCurrentBalance(newBalance);
        account.setTotalPointsRedeemed(newTotalRedeemed);

        RewardsAccount updatedAccount = rewardsAccountRepository.save(account);

        // Create transaction record (negative points for redemption)
        RewardsTransaction transaction = RewardsTransaction.builder()
                .id(UUID.randomUUID())
                .rewardsAccountId(account.getId())
                .type(request.redemptionType())
                .points(-request.points())
                .runningBalance(newBalance)
                .description(request.description())
                .createdAt(Instant.now())
                .build();

        RewardsTransaction savedTransaction = rewardsTransactionRepository.save(transaction);

        // Publish event
        publishRewardsEvent(
                UUID.randomUUID().toString(),
                "rewards.redeemed",
                Instant.now(),
                request.customerId(),
                account.getId(),
                -request.points(),
                newBalance,
                account.getTier()
        );

        log.info("Points redeemed successfully. Account: {}, newBalance: {}", account.getId(), newBalance);

        return rewardsMapper.toTransactionResponse(savedTransaction);
    }

    public PagedResponse<RewardsTransactionResponse> getTransactionHistory(UUID customerId, int page, int size) {
        log.debug("Fetching transaction history for customer: {}, page: {}, size: {}", customerId, page, size);

        RewardsAccount account = rewardsAccountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Rewards account not found for customer: " + customerId));

        Pageable pageable = PageRequest.of(page, size);
        Page<RewardsTransaction> transactionPage = rewardsTransactionRepository
                .findByRewardsAccountIdOrderByCreatedAtDesc(account.getId(), pageable);

        List<RewardsTransactionResponse> content = transactionPage.getContent()
                .stream()
                .map(rewardsMapper::toTransactionResponse)
                .collect(Collectors.toList());

        return PagedResponse.<RewardsTransactionResponse>builder()
                .content(content)
                .pageNumber(page)
                .pageSize(size)
                .totalElements(transactionPage.getTotalElements())
                .totalPages(transactionPage.getTotalPages())
                .isLast(transactionPage.isLast())
                .build();
    }

    public List<RewardsOfferResponse> getActiveOffers(UUID customerId) {
        log.debug("Fetching active offers for customer: {}", customerId);

        RewardsAccount account = rewardsAccountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Rewards account not found for customer: " + customerId));

        LocalDate today = LocalDate.now();
        List<RewardsOffer> offers = rewardsOfferRepository
                .findByIsActiveTrueAndStartDateBeforeAndEndDateAfter(today, today);

        // Filter by tier eligibility
        return offers.stream()
                .filter(offer -> offer.getMinimumTier() == null ||
                        account.getTier().getMinPointsRequired() >= offer.getMinimumTier().getMinPointsRequired())
                .map(rewardsMapper::toOfferResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void evaluateTierUpgrade(UUID customerId) {
        log.debug("Evaluating tier upgrade for customer: {}", customerId);

        RewardsAccount account = rewardsAccountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Rewards account not found for customer: " + customerId));

        RewardsTier currentTier = account.getTier();
        RewardsTier nextTier = getNextTier(currentTier);

        if (nextTier != null && account.getTotalPointsEarned() >= nextTier.getMinPointsRequired()) {
            log.info("Tier upgrade detected for customer: {} from {} to {}", customerId, currentTier, nextTier);

            account.setTier(nextTier);
            account.setTierEvaluationDate(LocalDate.now());
            account.setTierExpiryDate(LocalDate.now().plusYears(1));

            RewardsAccount updatedAccount = rewardsAccountRepository.save(account);

            // Publish tier upgrade event
            publishRewardsEvent(
                    UUID.randomUUID().toString(),
                    "rewards.tier_upgraded",
                    Instant.now(),
                    customerId,
                    account.getId(),
                    0,
                    account.getCurrentBalance(),
                    nextTier
            );

            log.info("Tier upgrade completed for customer: {}", customerId);
        }
    }

    private RewardsTier getNextTier(RewardsTier currentTier) {
        return switch (currentTier) {
            case BRONZE -> RewardsTier.SILVER;
            case SILVER -> RewardsTier.GOLD;
            case GOLD -> RewardsTier.PLATINUM;
            case PLATINUM -> RewardsTier.DIAMOND;
            case DIAMOND -> null;
        };
    }

    private void publishRewardsEvent(String eventId, String eventType, Instant timestamp,
                                     UUID customerId, UUID rewardsAccountId, long points,
                                     long newBalance, RewardsTier tier) {
        try {
            RewardsEvent event = RewardsEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .timestamp(timestamp)
                    .customerId(customerId)
                    .rewardsAccountId(rewardsAccountId)
                    .points(points)
                    .newBalance(newBalance)
                    .tier(tier)
                    .build();

            kafkaTemplate.send(REWARDS_TOPIC, eventId, event);
            log.debug("Rewards event published: eventType={}, customerId={}", eventType, customerId);
        } catch (Exception e) {
            log.error("Failed to publish rewards event: eventType={}, customerId={}", eventType, customerId, e);
            // Don't throw exception to avoid transaction rollback
        }
    }
}
