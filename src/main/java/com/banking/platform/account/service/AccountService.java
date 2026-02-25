package com.banking.platform.account.service;

import com.banking.platform.account.mapper.AccountMapper;
import com.banking.platform.account.model.dto.AccountResponse;
import com.banking.platform.account.model.dto.BalanceDetailResponse;
import com.banking.platform.account.model.dto.CreateAccountRequest;
import com.banking.platform.account.model.dto.UpdateAccountStatusRequest;
import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.model.entity.AccountStatus;
import com.banking.platform.account.model.event.AccountEvent;
import com.banking.platform.account.repository.AccountRepository;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final KafkaTemplate<String, AccountEvent> kafkaTemplate;

    private static final String ACCOUNT_TOPIC = "account-events";
    private static final int ACCOUNT_NUMBER_LENGTH = 12;

    @Transactional
    @CacheEvict(value = "accounts", allEntries = true)
    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account for customer: {}", request.customerId());

        String accountNumber = generateAccountNumber();

        Account account = Account.builder()
            .id(UUID.randomUUID())
            .customerId(request.customerId())
            .accountNumber(accountNumber)
            .accountType(request.accountType())
            .status(AccountStatus.ACTIVE)
            .currentBalance(request.initialDeposit())
            .availableBalance(request.initialDeposit())
            .pendingBalance(BigDecimal.ZERO)
            .holdAmount(BigDecimal.ZERO)
            .interestRate(BigDecimal.ZERO)
            .accruedInterest(BigDecimal.ZERO)
            .overdraftLimit(BigDecimal.ZERO)
            .currency(request.currency())
            .openedDate(LocalDate.now())
            .build();

        Account savedAccount = accountRepository.save(account);

        publishAccountEvent(savedAccount, "ACCOUNT_CREATED");

        log.info("Account created successfully: {}", savedAccount.getId());

        return accountMapper.toResponse(savedAccount);
    }

    @Cacheable(value = "accounts", key = "#id")
    public AccountResponse getAccount(UUID id) {
        log.info("Fetching account: {}", id);

        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));

        return accountMapper.toResponse(account);
    }

    public AccountResponse getAccountByNumber(String accountNumber) {
        log.info("Fetching account by number: {}", accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        return accountMapper.toResponse(account);
    }

    public List<AccountResponse> getCustomerAccounts(UUID customerId) {
        log.info("Fetching accounts for customer: {}", customerId);

        return accountRepository.findByCustomerId(customerId)
            .stream()
            .map(accountMapper::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "accounts", key = "#id")
    public AccountResponse updateAccountStatus(UUID id, UpdateAccountStatusRequest request) {
        log.info("Updating account status for: {} to {}", id, request.status());

        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));

        AccountStatus oldStatus = account.getStatus();
        account.setStatus(request.status());

        if (request.status() == AccountStatus.CLOSED) {
            account.setClosedDate(LocalDate.now());
        }

        Account updatedAccount = accountRepository.save(account);

        publishAccountEvent(updatedAccount, "ACCOUNT_STATUS_CHANGED");

        log.info("Account status updated from {} to {}", oldStatus, request.status());

        return accountMapper.toResponse(updatedAccount);
    }

    @Cacheable(value = "accounts-balance", key = "#accountId")
    public BalanceDetailResponse getBalanceDetails(UUID accountId) {
        log.info("Fetching balance details for account: {}", accountId);

        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        return accountMapper.toBalanceDetail(account);
    }

    private String generateAccountNumber() {
        String randomNumber = UUID.randomUUID().toString().replace("-", "").substring(0, ACCOUNT_NUMBER_LENGTH);
        log.debug("Generated account number: {}", randomNumber);
        return randomNumber;
    }

    private void publishAccountEvent(Account account, String eventType) {
        try {
            AccountEvent event = AccountEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .timestamp(Instant.now())
                .accountId(account.getId())
                .customerId(account.getCustomerId())
                .accountNumber(account.getAccountNumber())
                .status(account.getStatus())
                .balance(account.getCurrentBalance())
                .build();

            kafkaTemplate.send(ACCOUNT_TOPIC, account.getId().toString(), event);
            log.debug("Account event published: {}", eventType);
        } catch (Exception e) {
            log.warn("Failed to publish account event: {}", eventType, e);
        }
    }
}
