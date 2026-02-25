package com.banking.platform.bank;

import com.banking.platform.bank.mapper.BankMapper;
import com.banking.platform.bank.model.dto.BankDirectoryResponse;
import com.banking.platform.bank.model.dto.LinkBankRequest;
import com.banking.platform.bank.model.dto.LinkedBankResponse;
import com.banking.platform.bank.model.dto.VerifyMicroDepositsRequest;
import com.banking.platform.bank.model.entity.BankAccountType;
import com.banking.platform.bank.model.entity.BankDirectory;
import com.banking.platform.bank.model.entity.LinkedBank;
import com.banking.platform.bank.model.entity.LinkStatus;
import com.banking.platform.bank.model.entity.VerificationMethod;
import com.banking.platform.bank.repository.BankDirectoryRepository;
import com.banking.platform.bank.repository.LinkedBankRepository;
import com.banking.platform.bank.service.BankService;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import com.banking.platform.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankServiceTest {

    @Mock
    private LinkedBankRepository linkedBankRepository;

    @Mock
    private BankDirectoryRepository bankDirectoryRepository;

    @Mock
    private BankMapper bankMapper;

    @InjectMocks
    private BankService bankService;

    private UUID customerId;
    private UUID bankId;
    private String routingNumber;
    private String accountNumber;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        bankId = UUID.randomUUID();
        routingNumber = "021000021";
        accountNumber = "1234567890";
    }

    @Test
    void testLinkBank_Success_WithMicroDeposit() {
        // Arrange
        LinkBankRequest request = new LinkBankRequest(
                customerId,
                routingNumber,
                accountNumber,
                BankAccountType.CHECKING,
                "John Doe",
                "My Checking",
                VerificationMethod.MICRO_DEPOSIT
        );

        BankDirectory bankDirectory = BankDirectory.builder()
                .id(UUID.randomUUID())
                .routingNumber(routingNumber)
                .bankName("Test Bank")
                .city("New York")
                .state("NY")
                .zipCode("10001")
                .isActive(true)
                .build();

        LinkedBank savedBank = LinkedBank.builder()
                .id(bankId)
                .customerId(customerId)
                .bankName("Test Bank")
                .routingNumber(routingNumber)
                .accountNumber(accountNumber)
                .accountHolderName("John Doe")
                .nickname("My Checking")
                .accountType(BankAccountType.CHECKING)
                .linkStatus(LinkStatus.PENDING_VERIFICATION)
                .verificationMethod(VerificationMethod.MICRO_DEPOSIT)
                .isPrimary(false)
                .microDeposit1(new BigDecimal("0.25"))
                .microDeposit2(new BigDecimal("0.50"))
                .verificationAttempts(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LinkedBankResponse response = new LinkedBankResponse(
                bankId,
                "Test Bank",
                "****7890",
                BankAccountType.CHECKING,
                LinkStatus.PENDING_VERIFICATION,
                "My Checking",
                false,
                null
        );

        when(bankDirectoryRepository.findByRoutingNumber(routingNumber))
                .thenReturn(Optional.of(bankDirectory));
        when(linkedBankRepository.existsByCustomerIdAndRoutingNumberAndAccountNumber(
                customerId, routingNumber, accountNumber))
                .thenReturn(false);
        when(linkedBankRepository.save(any(LinkedBank.class)))
                .thenReturn(savedBank);
        when(bankMapper.toLinkedBankResponse(savedBank))
                .thenReturn(response);

        // Act
        LinkedBankResponse result = bankService.linkBank(request);

        // Assert
        assertNotNull(result);
        assertEquals(bankId, result.id());
        assertEquals(LinkStatus.PENDING_VERIFICATION, result.status());
        verify(linkedBankRepository, times(1)).save(any(LinkedBank.class));
        verify(bankDirectoryRepository, times(1)).findByRoutingNumber(routingNumber);
    }

    @Test
    void testLinkBank_Success_WithInstantVerification() {
        // Arrange
        LinkBankRequest request = new LinkBankRequest(
                customerId,
                routingNumber,
                accountNumber,
                BankAccountType.SAVINGS,
                "Jane Doe",
                "My Savings",
                VerificationMethod.INSTANT
        );

        BankDirectory bankDirectory = BankDirectory.builder()
                .id(UUID.randomUUID())
                .routingNumber(routingNumber)
                .bankName("Test Bank")
                .city("New York")
                .state("NY")
                .isActive(true)
                .build();

        LinkedBank savedBank = LinkedBank.builder()
                .id(bankId)
                .customerId(customerId)
                .bankName("Test Bank")
                .routingNumber(routingNumber)
                .accountNumber(accountNumber)
                .accountHolderName("Jane Doe")
                .nickname("My Savings")
                .accountType(BankAccountType.SAVINGS)
                .linkStatus(LinkStatus.VERIFIED)
                .verificationMethod(VerificationMethod.INSTANT)
                .isPrimary(false)
                .verificationAttempts(0)
                .verifiedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LinkedBankResponse response = new LinkedBankResponse(
                bankId,
                "Test Bank",
                "****7890",
                BankAccountType.SAVINGS,
                LinkStatus.VERIFIED,
                "My Savings",
                false,
                Instant.now()
        );

        when(bankDirectoryRepository.findByRoutingNumber(routingNumber))
                .thenReturn(Optional.of(bankDirectory));
        when(linkedBankRepository.existsByCustomerIdAndRoutingNumberAndAccountNumber(
                customerId, routingNumber, accountNumber))
                .thenReturn(false);
        when(linkedBankRepository.save(any(LinkedBank.class)))
                .thenReturn(savedBank);
        when(bankMapper.toLinkedBankResponse(savedBank))
                .thenReturn(response);

        // Act
        LinkedBankResponse result = bankService.linkBank(request);

        // Assert
        assertNotNull(result);
        assertEquals(LinkStatus.VERIFIED, result.status());
        assertNotNull(result.verifiedAt());
        verify(linkedBankRepository, times(1)).save(any(LinkedBank.class));
    }

    @Test
    void testLinkBank_InvalidRoutingNumber() {
        // Arrange
        LinkBankRequest request = new LinkBankRequest(
                customerId,
                "999999999",
                accountNumber,
                BankAccountType.CHECKING,
                "John Doe",
                "My Checking",
                VerificationMethod.MICRO_DEPOSIT
        );

        when(bankDirectoryRepository.findByRoutingNumber("999999999"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ValidationException.class, () -> bankService.linkBank(request));
        verify(linkedBankRepository, never()).save(any(LinkedBank.class));
    }

    @Test
    void testLinkBank_DuplicateBank() {
        // Arrange
        LinkBankRequest request = new LinkBankRequest(
                customerId,
                routingNumber,
                accountNumber,
                BankAccountType.CHECKING,
                "John Doe",
                "My Checking",
                VerificationMethod.MICRO_DEPOSIT
        );

        BankDirectory bankDirectory = BankDirectory.builder()
                .id(UUID.randomUUID())
                .routingNumber(routingNumber)
                .bankName("Test Bank")
                .city("New York")
                .state("NY")
                .isActive(true)
                .build();

        when(bankDirectoryRepository.findByRoutingNumber(routingNumber))
                .thenReturn(Optional.of(bankDirectory));
        when(linkedBankRepository.existsByCustomerIdAndRoutingNumberAndAccountNumber(
                customerId, routingNumber, accountNumber))
                .thenReturn(true);

        // Act & Assert
        assertThrows(ValidationException.class, () -> bankService.linkBank(request));
        verify(linkedBankRepository, never()).save(any(LinkedBank.class));
    }

    @Test
    void testVerifyMicroDeposits_Success() {
        // Arrange
        BigDecimal amount1 = new BigDecimal("0.25");
        BigDecimal amount2 = new BigDecimal("0.50");

        VerifyMicroDepositsRequest request = new VerifyMicroDepositsRequest(amount1, amount2);

        LinkedBank bank = LinkedBank.builder()
                .id(bankId)
                .customerId(customerId)
                .bankName("Test Bank")
                .routingNumber(routingNumber)
                .accountNumber(accountNumber)
                .accountType(BankAccountType.CHECKING)
                .linkStatus(LinkStatus.PENDING_VERIFICATION)
                .verificationMethod(VerificationMethod.MICRO_DEPOSIT)
                .microDeposit1(amount1)
                .microDeposit2(amount2)
                .verificationAttempts(0)
                .isPrimary(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LinkedBank verifiedBank = LinkedBank.builder()
                .id(bankId)
                .customerId(customerId)
                .bankName("Test Bank")
                .routingNumber(routingNumber)
                .accountNumber(accountNumber)
                .accountType(BankAccountType.CHECKING)
                .linkStatus(LinkStatus.VERIFIED)
                .verificationMethod(VerificationMethod.MICRO_DEPOSIT)
                .verificationAttempts(0)
                .verifiedAt(Instant.now())
                .isPrimary(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LinkedBankResponse response = new LinkedBankResponse(
                bankId,
                "Test Bank",
                "****7890",
                BankAccountType.CHECKING,
                LinkStatus.VERIFIED,
                null,
                false,
                Instant.now()
        );

        when(linkedBankRepository.findById(bankId))
                .thenReturn(Optional.of(bank));
        when(linkedBankRepository.save(any(LinkedBank.class)))
                .thenReturn(verifiedBank);
        when(bankMapper.toLinkedBankResponse(verifiedBank))
                .thenReturn(response);

        // Act
        LinkedBankResponse result = bankService.verifyMicroDeposits(bankId, request);

        // Assert
        assertNotNull(result);
        assertEquals(LinkStatus.VERIFIED, result.status());
        verify(linkedBankRepository, times(1)).findById(bankId);
        verify(linkedBankRepository, times(1)).save(any(LinkedBank.class));
    }

    @Test
    void testVerifyMicroDeposits_InvalidAmounts() {
        // Arrange
        BigDecimal amount1 = new BigDecimal("0.25");
        BigDecimal amount2 = new BigDecimal("0.75");

        VerifyMicroDepositsRequest request = new VerifyMicroDepositsRequest(amount1, amount2);

        LinkedBank bank = LinkedBank.builder()
                .id(bankId)
                .customerId(customerId)
                .bankName("Test Bank")
                .routingNumber(routingNumber)
                .accountNumber(accountNumber)
                .accountType(BankAccountType.CHECKING)
                .linkStatus(LinkStatus.PENDING_VERIFICATION)
                .verificationMethod(VerificationMethod.MICRO_DEPOSIT)
                .microDeposit1(new BigDecimal("0.25"))
                .microDeposit2(new BigDecimal("0.50"))
                .verificationAttempts(0)
                .isPrimary(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(linkedBankRepository.findById(bankId))
                .thenReturn(Optional.of(bank));
        when(linkedBankRepository.save(any(LinkedBank.class)))
                .thenReturn(bank);

        // Act & Assert
        assertThrows(ValidationException.class, () -> bankService.verifyMicroDeposits(bankId, request));
        verify(linkedBankRepository, times(1)).save(any(LinkedBank.class));
    }

    @Test
    void testVerifyMicroDeposits_BankNotFound() {
        // Arrange
        VerifyMicroDepositsRequest request = new VerifyMicroDepositsRequest(
                new BigDecimal("0.25"),
                new BigDecimal("0.50")
        );

        when(linkedBankRepository.findById(bankId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> bankService.verifyMicroDeposits(bankId, request));
    }

    @Test
    void testGetLinkedBanks() {
        // Arrange
        LinkedBank bank1 = LinkedBank.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .bankName("Bank 1")
                .routingNumber("111111111")
                .accountNumber("1111111111")
                .accountType(BankAccountType.CHECKING)
                .linkStatus(LinkStatus.VERIFIED)
                .verificationMethod(VerificationMethod.INSTANT)
                .isPrimary(true)
                .verifiedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LinkedBank bank2 = LinkedBank.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .bankName("Bank 2")
                .routingNumber("222222222")
                .accountNumber("2222222222")
                .accountType(BankAccountType.SAVINGS)
                .linkStatus(LinkStatus.VERIFIED)
                .verificationMethod(VerificationMethod.INSTANT)
                .isPrimary(false)
                .verifiedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LinkedBankResponse response1 = new LinkedBankResponse(
                bank1.getId(), "Bank 1", "****1111", BankAccountType.CHECKING,
                LinkStatus.VERIFIED, null, true, Instant.now()
        );
        LinkedBankResponse response2 = new LinkedBankResponse(
                bank2.getId(), "Bank 2", "****2222", BankAccountType.SAVINGS,
                LinkStatus.VERIFIED, null, false, Instant.now()
        );

        when(linkedBankRepository.findByCustomerId(customerId))
                .thenReturn(List.of(bank1, bank2));
        when(bankMapper.toLinkedBankResponse(bank1)).thenReturn(response1);
        when(bankMapper.toLinkedBankResponse(bank2)).thenReturn(response2);

        // Act
        List<LinkedBankResponse> result = bankService.getLinkedBanks(customerId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(linkedBankRepository, times(1)).findByCustomerId(customerId);
    }

    @Test
    void testSetPrimary_Success() {
        // Arrange
        LinkedBank currentPrimary = LinkedBank.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .bankName("Bank 1")
                .routingNumber("111111111")
                .accountNumber("1111111111")
                .accountType(BankAccountType.CHECKING)
                .linkStatus(LinkStatus.VERIFIED)
                .verificationMethod(VerificationMethod.INSTANT)
                .isPrimary(true)
                .verifiedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LinkedBank newPrimary = LinkedBank.builder()
                .id(bankId)
                .customerId(customerId)
                .bankName("Bank 2")
                .routingNumber("222222222")
                .accountNumber("2222222222")
                .accountType(BankAccountType.SAVINGS)
                .linkStatus(LinkStatus.VERIFIED)
                .verificationMethod(VerificationMethod.INSTANT)
                .isPrimary(false)
                .verifiedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(linkedBankRepository.findById(bankId))
                .thenReturn(Optional.of(newPrimary));
        when(linkedBankRepository.findByCustomerId(customerId))
                .thenReturn(List.of(currentPrimary, newPrimary));
        when(linkedBankRepository.saveAll(anyList()))
                .thenReturn(List.of());
        when(linkedBankRepository.save(any(LinkedBank.class)))
                .thenReturn(newPrimary);

        // Act
        assertDoesNotThrow(() -> bankService.setPrimary(customerId, bankId));

        // Assert
        verify(linkedBankRepository, times(1)).findById(bankId);
        verify(linkedBankRepository, times(1)).findByCustomerId(customerId);
        verify(linkedBankRepository, times(1)).saveAll(anyList());
    }

    @Test
    void testSetPrimary_BankNotVerified() {
        // Arrange
        LinkedBank unverifiedBank = LinkedBank.builder()
                .id(bankId)
                .customerId(customerId)
                .bankName("Bank")
                .routingNumber("111111111")
                .accountNumber("1111111111")
                .accountType(BankAccountType.CHECKING)
                .linkStatus(LinkStatus.PENDING_VERIFICATION)
                .verificationMethod(VerificationMethod.MICRO_DEPOSIT)
                .isPrimary(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(linkedBankRepository.findById(bankId))
                .thenReturn(Optional.of(unverifiedBank));

        // Act & Assert
        assertThrows(ValidationException.class, () -> bankService.setPrimary(customerId, bankId));
    }

    @Test
    void testRemoveBank() {
        // Arrange
        LinkedBank bank = LinkedBank.builder()
                .id(bankId)
                .customerId(customerId)
                .bankName("Test Bank")
                .routingNumber(routingNumber)
                .accountNumber(accountNumber)
                .accountType(BankAccountType.CHECKING)
                .linkStatus(LinkStatus.VERIFIED)
                .verificationMethod(VerificationMethod.INSTANT)
                .isPrimary(true)
                .verifiedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(linkedBankRepository.findById(bankId))
                .thenReturn(Optional.of(bank));
        when(linkedBankRepository.save(any(LinkedBank.class)))
                .thenReturn(bank);

        // Act
        assertDoesNotThrow(() -> bankService.removeBank(bankId));

        // Assert
        verify(linkedBankRepository, times(1)).findById(bankId);
        verify(linkedBankRepository, times(1)).save(any(LinkedBank.class));
    }

    @Test
    void testLookupRouting_Success() {
        // Arrange
        BankDirectory bankDirectory = BankDirectory.builder()
                .id(UUID.randomUUID())
                .routingNumber(routingNumber)
                .bankName("Test Bank")
                .city("New York")
                .state("NY")
                .isActive(true)
                .build();

        BankDirectoryResponse response = new BankDirectoryResponse(
                routingNumber,
                "Test Bank",
                "New York",
                "NY",
                true
        );

        when(bankDirectoryRepository.findByRoutingNumber(routingNumber))
                .thenReturn(Optional.of(bankDirectory));
        when(bankMapper.toBankDirectoryResponse(bankDirectory))
                .thenReturn(response);

        // Act
        BankDirectoryResponse result = bankService.lookupRouting(routingNumber);

        // Assert
        assertNotNull(result);
        assertEquals("Test Bank", result.bankName());
        assertEquals(routingNumber, result.routingNumber());
    }

    @Test
    void testLookupRouting_NotFound() {
        // Arrange
        when(bankDirectoryRepository.findByRoutingNumber("999999999"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> bankService.lookupRouting("999999999"));
    }
}
