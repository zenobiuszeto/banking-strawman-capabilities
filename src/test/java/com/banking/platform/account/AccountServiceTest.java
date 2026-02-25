package com.banking.platform.account;

import com.banking.platform.account.mapper.AccountMapper;
import com.banking.platform.account.model.dto.AccountResponse;
import com.banking.platform.account.model.dto.CreateAccountRequest;
import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.model.entity.AccountStatus;
import com.banking.platform.account.repository.AccountRepository;
import com.banking.platform.account.service.AccountService;
import com.banking.platform.onboarding.model.entity.AccountType;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private KafkaTemplate kafkaTemplate;

    @InjectMocks
    private AccountService accountService;

    private UUID customerId;
    private UUID accountId;
    private CreateAccountRequest createAccountRequest;
    private Account testAccount;
    private AccountResponse testAccountResponse;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        createAccountRequest = new CreateAccountRequest(
            customerId,
            AccountType.CHECKING,
            BigDecimal.valueOf(1000),
            "USD"
        );

        testAccount = Account.builder()
            .id(accountId)
            .customerId(customerId)
            .accountNumber("123456789012")
            .accountType(AccountType.CHECKING)
            .status(AccountStatus.ACTIVE)
            .currentBalance(BigDecimal.valueOf(1000))
            .availableBalance(BigDecimal.valueOf(1000))
            .pendingBalance(BigDecimal.ZERO)
            .holdAmount(BigDecimal.ZERO)
            .interestRate(BigDecimal.ZERO)
            .accruedInterest(BigDecimal.ZERO)
            .overdraftLimit(BigDecimal.ZERO)
            .currency("USD")
            .openedDate(LocalDate.now())
            .build();

        testAccountResponse = new AccountResponse(
            accountId,
            "123456789012",
            AccountType.CHECKING,
            AccountStatus.ACTIVE,
            BigDecimal.valueOf(1000),
            BigDecimal.valueOf(1000),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "USD",
            LocalDate.now()
        );
    }

    @Test
    void testCreateAccountSuccess() {
        // Arrange
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);
        when(accountMapper.toResponse(any(Account.class))).thenReturn(testAccountResponse);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

        // Act
        AccountResponse response = accountService.createAccount(createAccountRequest);

        // Assert
        assertNotNull(response);
        assertEquals(accountId, response.id());
        assertEquals(AccountType.CHECKING, response.type());
        assertEquals(BigDecimal.valueOf(1000), response.currentBalance());
        verify(accountRepository, times(1)).save(any(Account.class));
        verify(accountMapper, times(1)).toResponse(any(Account.class));
    }

    @Test
    void testGetAccountCached() {
        // Arrange
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(accountMapper.toResponse(any(Account.class))).thenReturn(testAccountResponse);

        // Act
        AccountResponse response = accountService.getAccount(accountId);

        // Assert
        assertNotNull(response);
        assertEquals(accountId, response.id());
        verify(accountRepository, times(1)).findById(accountId);
    }

    @Test
    void testGetAccountNotFound() {
        // Arrange
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> accountService.getAccount(accountId));
        verify(accountRepository, times(1)).findById(accountId);
    }

    @Test
    void testGetAccountByNumberSuccess() {
        // Arrange
        String accountNumber = "123456789012";
        when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.of(testAccount));
        when(accountMapper.toResponse(any(Account.class))).thenReturn(testAccountResponse);

        // Act
        AccountResponse response = accountService.getAccountByNumber(accountNumber);

        // Assert
        assertNotNull(response);
        assertEquals(testAccountResponse, response);
        verify(accountRepository, times(1)).findByAccountNumber(accountNumber);
    }

    @Test
    void testGetAccountByNumberNotFound() {
        // Arrange
        String accountNumber = "999999999999";
        when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> accountService.getAccountByNumber(accountNumber));
        verify(accountRepository, times(1)).findByAccountNumber(accountNumber);
    }
}
