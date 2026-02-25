package com.banking.platform.debitnetwork.service;

import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.repository.AccountRepository;
import com.banking.platform.debitnetwork.mapper.DebitNetworkMapper;
import com.banking.platform.debitnetwork.model.dto.SettlementResponse;
import com.banking.platform.debitnetwork.model.entity.*;
import com.banking.platform.debitnetwork.model.event.DebitNetworkEvent;
import com.banking.platform.debitnetwork.repository.DebitTransactionRepository;
import com.banking.platform.debitnetwork.repository.NetworkSettlementRepository;
import com.banking.platform.shared.exception.BusinessException;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import com.banking.platform.shared.util.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SettlementService {

    private final DebitTransactionRepository debitTransactionRepository;
    private final NetworkSettlementRepository networkSettlementRepository;
    private final AccountRepository accountRepository;
    private final DebitNetworkMapper mapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String DEBIT_TOPIC = "debit-network-events";

    @Transactional
    public SettlementResponse settleAuthorizedTransactions() {
        log.info("Starting batch settlement of authorized transactions");

        List<DebitTransaction> authorizedTxs = debitTransactionRepository.findByStatus(DebitTransactionStatus.AUTHORIZED);
        if (authorizedTxs.isEmpty()) {
            throw new BusinessException("NO_TRANSACTIONS", "No authorized transactions to settle");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        int count = 0;

        for (DebitTransaction tx : authorizedTxs) {
            try {
                Account account = accountRepository.findById(tx.getAccountId()).orElse(null);
                if (account != null) {
                    account.setHoldAmount(account.getHoldAmount().subtract(tx.getAmount()).max(BigDecimal.ZERO));
                    account.setCurrentBalance(account.getCurrentBalance().subtract(tx.getAmount()));
                    accountRepository.save(account);
                }
                tx.setStatus(DebitTransactionStatus.SETTLED);
                tx.setSettledAt(Instant.now());
                debitTransactionRepository.save(tx);
                totalAmount = totalAmount.add(tx.getAmount());
                count++;
            } catch (Exception e) {
                log.error("Failed to settle transaction: {}", tx.getId(), e);
            }
        }

        String batchId = "BATCH-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        NetworkSettlement settlement = NetworkSettlement.builder()
                .settlementDate(LocalDate.now()).totalAmount(totalAmount)
                .transactionCount(count).status(SettlementStatus.COMPLETED).batchId(batchId).build();

        NetworkSettlement saved = networkSettlementRepository.save(settlement);

        try {
            DebitNetworkEvent event = DebitNetworkEvent.builder()
                    .eventId(UUID.randomUUID().toString()).eventType("settlement.completed")
                    .timestamp(Instant.now()).amount(totalAmount).build();
            kafkaTemplate.send(DEBIT_TOPIC, batchId, event);
        } catch (Exception e) {
            log.error("Failed to publish settlement event", e);
        }

        log.info("Settlement completed: batchId={}, txCount={}, totalAmount={}", batchId, count, totalAmount);
        return mapper.toSettlementResponse(saved);
    }

    public SettlementResponse getSettlement(UUID settlementId) {
        NetworkSettlement settlement = networkSettlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", settlementId.toString()));
        return mapper.toSettlementResponse(settlement);
    }

    public PagedResponse<SettlementResponse> listSettlements(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NetworkSettlement> settlementPage = networkSettlementRepository.findAllByOrderBySettlementDateDesc(pageable);
        List<SettlementResponse> content = settlementPage.getContent().stream()
                .map(mapper::toSettlementResponse).collect(Collectors.toList());
        return PagedResponse.<SettlementResponse>builder()
                .content(content).page(page).size(size)
                .totalElements(settlementPage.getTotalElements()).totalPages(settlementPage.getTotalPages())
                .last(settlementPage.isLast()).build();
    }

    @Transactional
    public void reverseTransaction(UUID transactionId) {
        log.info("Reversing debit transaction: {}", transactionId);
        DebitTransaction tx = debitTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Debit Transaction", transactionId.toString()));

        if (tx.getStatus() == DebitTransactionStatus.AUTHORIZED) {
            Account account = accountRepository.findById(tx.getAccountId()).orElse(null);
            if (account != null) {
                account.setHoldAmount(account.getHoldAmount().subtract(tx.getAmount()).max(BigDecimal.ZERO));
                account.setAvailableBalance(account.getAvailableBalance().add(tx.getAmount()));
                accountRepository.save(account);
            }
        } else if (tx.getStatus() == DebitTransactionStatus.SETTLED) {
            Account account = accountRepository.findById(tx.getAccountId()).orElse(null);
            if (account != null) {
                account.setCurrentBalance(account.getCurrentBalance().add(tx.getAmount()));
                account.setAvailableBalance(account.getAvailableBalance().add(tx.getAmount()));
                accountRepository.save(account);
            }
        } else {
            throw new BusinessException("INVALID_STATUS", "Cannot reverse transaction with status: " + tx.getStatus());
        }
        tx.setStatus(DebitTransactionStatus.REVERSED);
        debitTransactionRepository.save(tx);
        log.info("Transaction reversed: {}", transactionId);
    }
}

