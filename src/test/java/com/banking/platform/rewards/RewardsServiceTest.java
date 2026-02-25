package com.banking.platform.rewards;

import com.banking.platform.rewards.mapper.RewardsMapper;
import com.banking.platform.rewards.model.dto.EarnPointsRequest;
import com.banking.platform.rewards.model.dto.RedeemPointsRequest;
import com.banking.platform.rewards.model.dto.RewardsTransactionResponse;
import com.banking.platform.rewards.model.entity.RewardsAccount;
import com.banking.platform.rewards.model.entity.RewardsTransaction;
import com.banking.platform.rewards.model.entity.RewardsTier;
import com.banking.platform.rewards.model.entity.RewardsTransactionType;
import com.banking.platform.rewards.repository.RewardsAccountRepository;
import com.banking.platform.rewards.repository.RewardsOfferRepository;
import com.banking.platform.rewards.repository.RewardsTransactionRepository;
import com.banking.platform.rewards.service.RewardsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardsServiceTest {

    @Mock
    private RewardsAccountRepository rewardsAccountRepository;

    @Mock
    private RewardsTransactionRepository rewardsTransactionRepository;

    @Mock
    private RewardsOfferRepository rewardsOfferRepository;

    @Mock
    private RewardsMapper rewardsMapper;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private RewardsService rewardsService;

    private UUID customerId;
    private UUID accountId;
    private RewardsAccount rewardsAccount;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        rewardsAccount = RewardsAccount.builder()
                .id(accountId)
                .customerId(customerId)
                .tier(RewardsTier.SILVER)
                .totalPointsEarned(7500)
                .totalPointsRedeemed(2000)
                .currentBalance(5500)
                .lifetimeValue(BigDecimal.valueOf(55.00))
                .tierExpiryDate(LocalDate.now().plusYears(1))
                .tierEvaluationDate(LocalDate.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void testEarnPointsWithTierMultiplier() {
        // Arrange
        EarnPointsRequest request = new EarnPointsRequest(
                customerId,
                null,
                1000,
                "Test purchase"
        );

        when(rewardsAccountRepository.findByCustomerId(customerId))
                .thenReturn(Optional.of(rewardsAccount));
        when(rewardsAccountRepository.save(any(RewardsAccount.class)))
                .thenReturn(rewardsAccount);

        RewardsTransaction transaction = RewardsTransaction.builder()
                .id(UUID.randomUUID())
                .rewardsAccountId(accountId)
                .type(RewardsTransactionType.EARN_PURCHASE)
                .points(1250)
                .runningBalance(6750)
                .description("Test purchase")
                .createdAt(Instant.now())
                .build();

        when(rewardsTransactionRepository.save(any(RewardsTransaction.class)))
                .thenReturn(transaction);

        when(rewardsMapper.toTransactionResponse(any(RewardsTransaction.class)))
                .thenReturn(new RewardsTransactionResponse(
                        transaction.getId(),
                        transaction.getType(),
                        transaction.getPoints(),
                        transaction.getRunningBalance(),
                        transaction.getDescription(),
                        transaction.getCreatedAt()
                ));

        // Act
        RewardsTransactionResponse response = rewardsService.earnPoints(request);

        // Assert
        assertEquals(1250, response.points());
        assertEquals(6750, response.runningBalance());
        assertEquals(RewardsTransactionType.EARN_PURCHASE, response.type());
    }

    @Test
    void testRedeemPointsSuccess() {
        // Arrange
        RedeemPointsRequest request = new RedeemPointsRequest(
                customerId,
                2000,
                RewardsTransactionType.REDEEM_STATEMENT_CREDIT,
                "Statement credit redemption"
        );

        when(rewardsAccountRepository.findByCustomerId(customerId))
                .thenReturn(Optional.of(rewardsAccount));
        when(rewardsAccountRepository.save(any(RewardsAccount.class)))
                .thenReturn(rewardsAccount);

        RewardsTransaction transaction = RewardsTransaction.builder()
                .id(UUID.randomUUID())
                .rewardsAccountId(accountId)
                .type(RewardsTransactionType.REDEEM_STATEMENT_CREDIT)
                .points(-2000)
                .runningBalance(3500)
                .description("Statement credit redemption")
                .createdAt(Instant.now())
                .build();

        when(rewardsTransactionRepository.save(any(RewardsTransaction.class)))
                .thenReturn(transaction);

        when(rewardsMapper.toTransactionResponse(any(RewardsTransaction.class)))
                .thenReturn(new RewardsTransactionResponse(
                        transaction.getId(),
                        transaction.getType(),
                        transaction.getPoints(),
                        transaction.getRunningBalance(),
                        transaction.getDescription(),
                        transaction.getCreatedAt()
                ));

        // Act
        RewardsTransactionResponse response = rewardsService.redeemPoints(request);

        // Assert
        assertEquals(-2000, response.points());
        assertEquals(3500, response.runningBalance());
        assertEquals(RewardsTransactionType.REDEEM_STATEMENT_CREDIT, response.type());
    }

    @Test
    void testRedeemPointsInsufficientBalance() {
        // Arrange
        RedeemPointsRequest request = new RedeemPointsRequest(
                customerId,
                10000, // More than available balance (5500)
                RewardsTransactionType.REDEEM_STATEMENT_CREDIT,
                "Statement credit redemption"
        );

        when(rewardsAccountRepository.findByCustomerId(customerId))
                .thenReturn(Optional.of(rewardsAccount));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> rewardsService.redeemPoints(request),
                "Should throw IllegalArgumentException for insufficient balance");
    }

    @Test
    void testTierUpgradeEvaluation() {
        // Arrange
        RewardsAccount bronzeAccount = RewardsAccount.builder()
                .id(accountId)
                .customerId(customerId)
                .tier(RewardsTier.BRONZE)
                .totalPointsEarned(6000) // Enough for SILVER (5000 required)
                .totalPointsRedeemed(0)
                .currentBalance(6000)
                .lifetimeValue(BigDecimal.valueOf(60.00))
                .tierExpiryDate(LocalDate.now().plusYears(1))
                .tierEvaluationDate(LocalDate.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(rewardsAccountRepository.findByCustomerId(customerId))
                .thenReturn(Optional.of(bronzeAccount));
        when(rewardsAccountRepository.save(any(RewardsAccount.class)))
                .thenReturn(bronzeAccount);

        // Act
        rewardsService.evaluateTierUpgrade(customerId);

        // Assert - Verify that the account was updated with SILVER tier
        assertEquals(RewardsTier.SILVER, bronzeAccount.getTier());
    }
}
