package com.banking.platform.debitnetwork.service;

import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.model.entity.AccountStatus;
import com.banking.platform.account.repository.AccountRepository;
import com.banking.platform.debitnetwork.mapper.DebitNetworkMapper;
import com.banking.platform.debitnetwork.model.dto.*;
import com.banking.platform.debitnetwork.model.entity.*;
import com.banking.platform.debitnetwork.model.event.DebitNetworkEvent;
import com.banking.platform.debitnetwork.repository.DebitCardRepository;
import com.banking.platform.debitnetwork.repository.DebitTransactionRepository;
import com.banking.platform.shared.exception.BusinessException;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DebitCardService {

    private final DebitCardRepository debitCardRepository;
    private final DebitTransactionRepository debitTransactionRepository;
    private final AccountRepository accountRepository;
    private final DebitNetworkMapper mapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String DEBIT_TOPIC = "debit-network-events";

    @Transactional
    @CacheEvict(value = "debit-cards", allEntries = true)
    public DebitCardResponse issueCard(IssueCardRequest request) {
        log.info("Issuing debit card for account: {}, customer: {}", request.accountId(), request.customerId());

        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.accountId().toString()));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("ACCOUNT_NOT_ACTIVE", "Account must be active to issue a debit card");
        }

        String cardNumber = generateCardNumber();
        String maskedNumber = "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);

        DebitCard card = DebitCard.builder()
                .accountId(request.accountId())
                .customerId(request.customerId())
                .cardNumberMasked(maskedNumber)
                .cardHolderName(request.cardHolderName())
                .expiryDate(LocalDate.now().plusYears(3))
                .status(CardStatus.ACTIVE)
                .dailyLimit(request.dailyLimit())
                .monthlyLimit(request.monthlyLimit())
                .dailyUsed(BigDecimal.ZERO)
                .monthlyUsed(BigDecimal.ZERO)
                .dailyUsedResetDate(LocalDate.now())
                .monthlyUsedResetDate(LocalDate.now().withDayOfMonth(1))
                .build();

        DebitCard saved = debitCardRepository.save(card);
        publishEvent(saved.getId(), request.accountId(), null, null,
                BigDecimal.ZERO, null, null, null, "card.issued");

        log.info("Debit card issued: {}", saved.getId());
        return mapper.toDebitCardResponse(saved);
    }

    @Cacheable(value = "debit-cards", key = "#customerId")
    public List<DebitCardResponse> getCustomerCards(UUID customerId) {
        return debitCardRepository.findByCustomerId(customerId).stream()
                .map(mapper::toDebitCardResponse).collect(Collectors.toList());
    }

    public DebitCardResponse getCard(UUID cardId) {
        DebitCard card = debitCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Debit Card", cardId.toString()));
        return mapper.toDebitCardResponse(card);
    }

    @Transactional
    @CacheEvict(value = "debit-cards", allEntries = true)
    public DebitCardResponse blockCard(UUID cardId) {
        log.info("Blocking debit card: {}", cardId);
        DebitCard card = debitCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Debit Card", cardId.toString()));
        card.setStatus(CardStatus.BLOCKED);
        DebitCard saved = debitCardRepository.save(card);
        publishEvent(cardId, card.getAccountId(), null, null,
                BigDecimal.ZERO, null, null, null, "card.blocked");
        return mapper.toDebitCardResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "debit-cards", allEntries = true)
    public DebitCardResponse unblockCard(UUID cardId) {
        log.info("Unblocking debit card: {}", cardId);
        DebitCard card = debitCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Debit Card", cardId.toString()));
        if (card.getStatus() != CardStatus.BLOCKED) {
            throw new BusinessException("INVALID_STATUS", "Only blocked cards can be unblocked");
        }
        card.setStatus(CardStatus.ACTIVE);
        DebitCard saved = debitCardRepository.save(card);
        publishEvent(cardId, card.getAccountId(), null, null,
                BigDecimal.ZERO, null, null, null, "card.unblocked");
        return mapper.toDebitCardResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "debit-cards", allEntries = true)
    public DebitCardResponse updateLimits(UUID cardId, BigDecimal dailyLimit, BigDecimal monthlyLimit) {
        log.info("Updating limits for card: {}", cardId);
        DebitCard card = debitCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Debit Card", cardId.toString()));
        if (dailyLimit != null) card.setDailyLimit(dailyLimit);
        if (monthlyLimit != null) card.setMonthlyLimit(monthlyLimit);
        DebitCard saved = debitCardRepository.save(card);
        return mapper.toDebitCardResponse(saved);
    }

    // ==================== Authorization ====================

    @Transactional
    public AuthorizationResponse authorize(AuthorizationRequest request) {
        log.info("Authorizing debit transaction: card={}, merchant={}, amount={}",
                request.debitCardId(), request.merchantName(), request.amount());

        DebitCard card = debitCardRepository.findById(request.debitCardId())
                .orElseThrow(() -> new ResourceNotFoundException("Debit Card", request.debitCardId().toString()));

        if (card.getStatus() == CardStatus.BLOCKED) {
            return createDeclinedTransaction(card, request, DeclineReason.CARD_BLOCKED);
        }
        if (card.getStatus() == CardStatus.EXPIRED || card.getExpiryDate().isBefore(LocalDate.now())) {
            return createDeclinedTransaction(card, request, DeclineReason.CARD_EXPIRED);
        }
        if (card.getStatus() != CardStatus.ACTIVE) {
            return createDeclinedTransaction(card, request, DeclineReason.INVALID_CARD);
        }

        resetUsageCounters(card);

        BigDecimal newDailyUsed = card.getDailyUsed().add(request.amount());
        if (newDailyUsed.compareTo(card.getDailyLimit()) > 0) {
            return createDeclinedTransaction(card, request, DeclineReason.DAILY_LIMIT_EXCEEDED);
        }

        BigDecimal newMonthlyUsed = card.getMonthlyUsed().add(request.amount());
        if (newMonthlyUsed.compareTo(card.getMonthlyLimit()) > 0) {
            return createDeclinedTransaction(card, request, DeclineReason.MONTHLY_LIMIT_EXCEEDED);
        }

        Account account = accountRepository.findById(card.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", card.getAccountId().toString()));

        if (account.getAvailableBalance().compareTo(request.amount()) < 0) {
            return createDeclinedTransaction(card, request, DeclineReason.INSUFFICIENT_FUNDS);
        }

        // Place hold
        account.setHoldAmount(account.getHoldAmount().add(request.amount()));
        account.setAvailableBalance(account.getAvailableBalance().subtract(request.amount()));
        accountRepository.save(account);

        card.setDailyUsed(newDailyUsed);
        card.setMonthlyUsed(newMonthlyUsed);
        debitCardRepository.save(card);

        String authCode = generateAuthorizationCode();
        DebitTransaction tx = DebitTransaction.builder()
                .debitCardId(card.getId())
                .accountId(card.getAccountId())
                .authorizationCode(authCode)
                .networkReferenceId(UUID.randomUUID().toString().substring(0, 16))
                .merchantName(request.merchantName())
                .merchantCategoryCode(request.merchantCategoryCode())
                .amount(request.amount())
                .currency(request.currency())
                .transactionType(request.transactionType())
                .status(DebitTransactionStatus.AUTHORIZED)
                .build();

        DebitTransaction saved = debitTransactionRepository.save(tx);
        publishEvent(card.getId(), card.getAccountId(), saved.getId(), authCode,
                request.amount(), request.merchantName(), request.transactionType(),
                DebitTransactionStatus.AUTHORIZED, "transaction.authorized");

        log.info("Transaction authorized: {}, authCode: {}", saved.getId(), authCode);
        return mapper.toAuthorizationResponse(saved);
    }

    public PagedResponse<DebitTransactionResponse> getCardTransactions(UUID cardId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DebitTransaction> txPage = debitTransactionRepository.findByDebitCardIdOrderByAuthorizedAtDesc(cardId, pageable);
        List<DebitTransactionResponse> content = txPage.getContent().stream()
                .map(mapper::toDebitTransactionResponse).collect(Collectors.toList());
        return PagedResponse.<DebitTransactionResponse>builder()
                .content(content).page(page).size(size)
                .totalElements(txPage.getTotalElements()).totalPages(txPage.getTotalPages())
                .last(txPage.isLast()).build();
    }

    public PagedResponse<DebitTransactionResponse> getAccountTransactions(UUID accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DebitTransaction> txPage = debitTransactionRepository.findByAccountIdOrderByAuthorizedAtDesc(accountId, pageable);
        List<DebitTransactionResponse> content = txPage.getContent().stream()
                .map(mapper::toDebitTransactionResponse).collect(Collectors.toList());
        return PagedResponse.<DebitTransactionResponse>builder()
                .content(content).page(page).size(size)
                .totalElements(txPage.getTotalElements()).totalPages(txPage.getTotalPages())
                .last(txPage.isLast()).build();
    }

    // ==================== Helpers ====================

    private AuthorizationResponse createDeclinedTransaction(DebitCard card, AuthorizationRequest request, DeclineReason reason) {
        log.warn("Transaction declined: card={}, reason={}", card.getId(), reason);
        String authCode = generateAuthorizationCode();
        DebitTransaction tx = DebitTransaction.builder()
                .debitCardId(card.getId()).accountId(card.getAccountId())
                .authorizationCode(authCode).merchantName(request.merchantName())
                .merchantCategoryCode(request.merchantCategoryCode())
                .amount(request.amount()).currency(request.currency())
                .transactionType(request.transactionType())
                .status(DebitTransactionStatus.DECLINED).declineReason(reason).build();
        DebitTransaction saved = debitTransactionRepository.save(tx);
        publishEvent(card.getId(), card.getAccountId(), saved.getId(), authCode,
                request.amount(), request.merchantName(), request.transactionType(),
                DebitTransactionStatus.DECLINED, "transaction.declined");
        return mapper.toAuthorizationResponse(saved);
    }

    private void resetUsageCounters(DebitCard card) {
        LocalDate today = LocalDate.now();
        if (card.getDailyUsedResetDate() == null || card.getDailyUsedResetDate().isBefore(today)) {
            card.setDailyUsed(BigDecimal.ZERO);
            card.setDailyUsedResetDate(today);
        }
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        if (card.getMonthlyUsedResetDate() == null || card.getMonthlyUsedResetDate().isBefore(firstOfMonth)) {
            card.setMonthlyUsed(BigDecimal.ZERO);
            card.setMonthlyUsedResetDate(firstOfMonth);
        }
    }

    private String generateCardNumber() {
        return "4" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 15);
    }

    private String generateAuthorizationCode() {
        return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 12).toUpperCase();
    }

    private void publishEvent(UUID cardId, UUID accountId, UUID txId, String authCode,
                              BigDecimal amount, String merchant, DebitTransactionType txType,
                              DebitTransactionStatus txStatus, String eventType) {
        try {
            DebitNetworkEvent event = DebitNetworkEvent.builder()
                    .eventId(UUID.randomUUID().toString()).eventType(eventType)
                    .timestamp(Instant.now()).debitCardId(cardId).accountId(accountId)
                    .transactionId(txId).authorizationCode(authCode).amount(amount)
                    .merchantName(merchant).transactionType(txType).transactionStatus(txStatus).build();
            kafkaTemplate.send(DEBIT_TOPIC, cardId.toString(), event);
            log.debug("Debit network event published: {}", eventType);
        } catch (Exception e) {
            log.error("Failed to publish debit network event: {}", eventType, e);
        }
    }
}

