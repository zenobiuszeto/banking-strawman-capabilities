package com.banking.platform.account.service;

import com.banking.platform.account.mapper.AccountMapper;
import com.banking.platform.account.model.dto.AccountSummaryResponse;
import com.banking.platform.account.model.dto.DashboardResponse;
import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.repository.AccountRepository;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Cacheable(value = "account-summary", key = "#customerId")
    public DashboardResponse getDashboard(UUID customerId) {
        log.info("Fetching dashboard for customer: {}", customerId);

        List<Account> accounts = accountRepository.findByCustomerId(customerId);

        if (accounts.isEmpty()) {
            throw new ResourceNotFoundException("No accounts found for customer: " + customerId);
        }

        List<AccountSummaryResponse> accountSummaries = accounts.stream()
            .map(accountMapper::toSummary)
            .collect(Collectors.toList());

        BigDecimal totalNetWorth = calculateTotalNetWorth(accounts);

        DashboardResponse dashboard = new DashboardResponse(
            customerId,
            "Customer", // This would typically come from customer service
            totalNetWorth,
            accountSummaries,
            accounts.size(),
            Instant.now()
        );

        log.info("Dashboard retrieved successfully for customer: {}", customerId);

        return dashboard;
    }

    private BigDecimal calculateTotalNetWorth(List<Account> accounts) {
        return accounts.stream()
            .map(Account::getCurrentBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
