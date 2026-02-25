package com.banking.platform.debitnetwork;

import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.model.entity.AccountStatus;
import com.banking.platform.account.repository.AccountRepository;
import com.banking.platform.debitnetwork.mapper.DebitNetworkMapper;
import com.banking.platform.debitnetwork.model.dto.*;
import com.banking.platform.debitnetwork.model.entity.*;
import com.banking.platform.debitnetwork.repository.DebitCardRepository;
import com.banking.platform.debitnetwork.repository.DebitTransactionRepository;
import com.banking.platform.debitnetwork.service.DebitCardService;
import com.banking.platform.shared.exception.BusinessException;
import com.banking.platform.shared.exception.ResourceNotFoundException;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DebitCardServiceTest {

    @Mock
    private DebitCardRepository debitCardRepository;

    @Mock
    private DebitTransactionRepository debitTransactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DebitNetworkMapper mapper;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private DebitCardService debitCardService;

    private UUID accountId;
    private UUID customerId;
    private UUID cardId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        cardId = UUID.randomUUID();
    }

    @Test
    void testIssueCard_Success() {
        IssueCardRequest request = new IssueCardRequest(
                accountId, customerId, "John Doe",
                new BigDecimal("2500.00"), new BigDecimal("25000.00")
        );

        Account account = Account.builder()
                .id(accountId)
                .status(AccountStatus.ACTIVE)
                .build();

        DebitCard savedCard = DebitCard.builder()
                .id(cardId)
                .accountId(accountId)
                .customerId(customerId)
                .cardNumberMasked("**** **** **** 1234")
                .cardHolderName("John Doe")
                .expiryDate(LocalDate.now().plusYears(3))
                .status(CardStatus.ACTIVE)
                .dailyLimit(new BigDecimal("2500.00"))
                .monthlyLimit(new BigDecimal("25000.00"))
                .dailyUsed(BigDecimal.ZERO)
                .monthlyUsed(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        DebitCardResponse expectedResponse = new DebitCardResponse(
                cardId, accountId, customerId, "**** **** **** 1234", "John Doe",
                LocalDate.now().plusYears(3), CardStatus.ACTIVE,
                new BigDecimal("2500.00"), new BigDecimal("25000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, Instant.now()
        );

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(debitCardRepository.save(any(DebitCard.class))).thenReturn(savedCard);
        when(mapper.toDebitCardResponse(savedCard)).thenReturn(expectedResponse);

        DebitCardResponse response = debitCardService.issueCard(request);

        assertNotNull(response);
        assertEquals(CardStatus.ACTIVE, response.status());
        assertEquals("John Doe", response.cardHolderName());
        verify(debitCardRepository, times(1)).save(any(DebitCard.class));
    }

    @Test
    void testIssueCard_AccountNotActive() {
        IssueCardRequest request = new IssueCardRequest(
                accountId, customerId, "John Doe", null, null
        );

        Account account = Account.builder()
                .id(accountId)
                .status(AccountStatus.CLOSED)
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThrows(BusinessException.class, () -> debitCardService.issueCard(request));
        verify(debitCardRepository, never()).save(any());
    }

    @Test
    void testIssueCard_AccountNotFound() {
        IssueCardRequest request = new IssueCardRequest(
                accountId, customerId, "John Doe", null, null
        );

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> debitCardService.issueCard(request));
    }

    @Test
    void testBlockCard_Success() {
        DebitCard card = DebitCard.builder()
                .id(cardId)
                .accountId(accountId)
                .status(CardStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        DebitCard blockedCard = DebitCard.builder()
                .id(cardId)
                .accountId(accountId)
                .status(CardStatus.BLOCKED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        DebitCardResponse expectedResponse = new DebitCardResponse(
                cardId, accountId, customerId, "**** 1234", "John",
                LocalDate.now().plusYears(3), CardStatus.BLOCKED,
                new BigDecimal("2500"), new BigDecimal("25000"),
                BigDecimal.ZERO, BigDecimal.ZERO, Instant.now()
        );

        when(debitCardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(debitCardRepository.save(any(DebitCard.class))).thenReturn(blockedCard);
        when(mapper.toDebitCardResponse(blockedCard)).thenReturn(expectedResponse);

        DebitCardResponse response = debitCardService.blockCard(cardId);

        assertNotNull(response);
        assertEquals(CardStatus.BLOCKED, response.status());
    }

    @Test
    void testUnblockCard_NotBlocked() {
        DebitCard card = DebitCard.builder()
                .id(cardId)
                .status(CardStatus.ACTIVE)
                .accountId(accountId)
                .build();

        when(debitCardRepository.findById(cardId)).thenReturn(Optional.of(card));

        assertThrows(BusinessException.class, () -> debitCardService.unblockCard(cardId));
    }

    @Test
    void testAuthorize_Success() {
        AuthorizationRequest request = new AuthorizationRequest(
                cardId, "Amazon", "5411",
                new BigDecimal("50.00"), "USD", DebitTransactionType.PURCHASE
        );

        DebitCard card = DebitCard.builder()
                .id(cardId)
                .accountId(accountId)
                .status(CardStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusYears(2))
                .dailyLimit(new BigDecimal("2500.00"))
                .monthlyLimit(new BigDecimal("25000.00"))
                .dailyUsed(BigDecimal.ZERO)
                .monthlyUsed(BigDecimal.ZERO)
                .dailyUsedResetDate(LocalDate.now())
                .monthlyUsedResetDate(LocalDate.now().withDayOfMonth(1))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Account account = Account.builder()
                .id(accountId)
                .availableBalance(new BigDecimal("1000.00"))
                .holdAmount(BigDecimal.ZERO)
                .build();

        DebitTransaction savedTx = DebitTransaction.builder()
                .id(UUID.randomUUID())
                .debitCardId(cardId)
                .accountId(accountId)
                .authorizationCode("AUTH123456")
                .merchantName("Amazon")
                .amount(new BigDecimal("50.00"))
                .status(DebitTransactionStatus.AUTHORIZED)
                .authorizedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        AuthorizationResponse expectedResponse = new AuthorizationResponse(
                savedTx.getId(), "AUTH123456", DebitTransactionStatus.AUTHORIZED,
                new BigDecimal("50.00"), "Amazon", null, Instant.now()
        );

        when(debitCardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(debitCardRepository.save(any(DebitCard.class))).thenReturn(card);
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(debitTransactionRepository.save(any(DebitTransaction.class))).thenReturn(savedTx);
        when(mapper.toAuthorizationResponse(savedTx)).thenReturn(expectedResponse);

        AuthorizationResponse response = debitCardService.authorize(request);

        assertNotNull(response);
        assertEquals(DebitTransactionStatus.AUTHORIZED, response.status());
        assertNull(response.declineReason());
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void testAuthorize_CardBlocked() {
        AuthorizationRequest request = new AuthorizationRequest(
                cardId, "Amazon", "5411",
                new BigDecimal("50.00"), "USD", DebitTransactionType.PURCHASE
        );

        DebitCard card = DebitCard.builder()
                .id(cardId)
                .accountId(accountId)
                .status(CardStatus.BLOCKED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        DebitTransaction declinedTx = DebitTransaction.builder()
                .id(UUID.randomUUID())
                .debitCardId(cardId)
                .accountId(accountId)
                .authorizationCode("AUTH999")
                .status(DebitTransactionStatus.DECLINED)
                .declineReason(DeclineReason.CARD_BLOCKED)
                .amount(new BigDecimal("50.00"))
                .merchantName("Amazon")
                .authorizedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        AuthorizationResponse expectedResponse = new AuthorizationResponse(
                declinedTx.getId(), "AUTH999", DebitTransactionStatus.DECLINED,
                new BigDecimal("50.00"), "Amazon", DeclineReason.CARD_BLOCKED, Instant.now()
        );

        when(debitCardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(debitTransactionRepository.save(any(DebitTransaction.class))).thenReturn(declinedTx);
        when(mapper.toAuthorizationResponse(declinedTx)).thenReturn(expectedResponse);

        AuthorizationResponse response = debitCardService.authorize(request);

        assertNotNull(response);
        assertEquals(DebitTransactionStatus.DECLINED, response.status());
        assertEquals(DeclineReason.CARD_BLOCKED, response.declineReason());
        verify(accountRepository, never()).save(any()); // No hold placed
    }

    @Test
    void testAuthorize_InsufficientFunds() {
        AuthorizationRequest request = new AuthorizationRequest(
                cardId, "Amazon", "5411",
                new BigDecimal("5000.00"), "USD", DebitTransactionType.PURCHASE
        );

        DebitCard card = DebitCard.builder()
                .id(cardId)
                .accountId(accountId)
                .status(CardStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusYears(2))
                .dailyLimit(new BigDecimal("10000.00"))
                .monthlyLimit(new BigDecimal("100000.00"))
                .dailyUsed(BigDecimal.ZERO)
                .monthlyUsed(BigDecimal.ZERO)
                .dailyUsedResetDate(LocalDate.now())
                .monthlyUsedResetDate(LocalDate.now().withDayOfMonth(1))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Account account = Account.builder()
                .id(accountId)
                .availableBalance(new BigDecimal("100.00"))
                .holdAmount(BigDecimal.ZERO)
                .build();

        DebitTransaction declinedTx = DebitTransaction.builder()
                .id(UUID.randomUUID())
                .debitCardId(cardId)
                .accountId(accountId)
                .authorizationCode("AUTH888")
                .status(DebitTransactionStatus.DECLINED)
                .declineReason(DeclineReason.INSUFFICIENT_FUNDS)
                .amount(new BigDecimal("5000.00"))
                .merchantName("Amazon")
                .authorizedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        AuthorizationResponse expectedResponse = new AuthorizationResponse(
                declinedTx.getId(), "AUTH888", DebitTransactionStatus.DECLINED,
                new BigDecimal("5000.00"), "Amazon", DeclineReason.INSUFFICIENT_FUNDS, Instant.now()
        );

        when(debitCardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(debitTransactionRepository.save(any(DebitTransaction.class))).thenReturn(declinedTx);
        when(mapper.toAuthorizationResponse(declinedTx)).thenReturn(expectedResponse);

        AuthorizationResponse response = debitCardService.authorize(request);

        assertNotNull(response);
        assertEquals(DebitTransactionStatus.DECLINED, response.status());
        assertEquals(DeclineReason.INSUFFICIENT_FUNDS, response.declineReason());
    }

    @Test
    void testAuthorize_DailyLimitExceeded() {
        AuthorizationRequest request = new AuthorizationRequest(
                cardId, "Amazon", "5411",
                new BigDecimal("1000.00"), "USD", DebitTransactionType.PURCHASE
        );

        DebitCard card = DebitCard.builder()
                .id(cardId)
                .accountId(accountId)
                .status(CardStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusYears(2))
                .dailyLimit(new BigDecimal("500.00"))
                .monthlyLimit(new BigDecimal("25000.00"))
                .dailyUsed(BigDecimal.ZERO)
                .monthlyUsed(BigDecimal.ZERO)
                .dailyUsedResetDate(LocalDate.now())
                .monthlyUsedResetDate(LocalDate.now().withDayOfMonth(1))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        DebitTransaction declinedTx = DebitTransaction.builder()
                .id(UUID.randomUUID())
                .debitCardId(cardId)
                .accountId(accountId)
                .authorizationCode("AUTH777")
                .status(DebitTransactionStatus.DECLINED)
                .declineReason(DeclineReason.DAILY_LIMIT_EXCEEDED)
                .amount(new BigDecimal("1000.00"))
                .merchantName("Amazon")
                .authorizedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        AuthorizationResponse expectedResponse = new AuthorizationResponse(
                declinedTx.getId(), "AUTH777", DebitTransactionStatus.DECLINED,
                new BigDecimal("1000.00"), "Amazon", DeclineReason.DAILY_LIMIT_EXCEEDED, Instant.now()
        );

        when(debitCardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(debitTransactionRepository.save(any(DebitTransaction.class))).thenReturn(declinedTx);
        when(mapper.toAuthorizationResponse(declinedTx)).thenReturn(expectedResponse);

        AuthorizationResponse response = debitCardService.authorize(request);

        assertNotNull(response);
        assertEquals(DeclineReason.DAILY_LIMIT_EXCEEDED, response.declineReason());
    }
}

