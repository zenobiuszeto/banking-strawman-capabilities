package com.banking.platform.bank.service;

import com.banking.platform.bank.mapper.BankMapper;
import com.banking.platform.bank.model.dto.BankDirectoryResponse;
import com.banking.platform.bank.model.dto.LinkBankRequest;
import com.banking.platform.bank.model.dto.LinkedBankResponse;
import com.banking.platform.bank.model.dto.VerifyMicroDepositsRequest;
import com.banking.platform.bank.model.entity.BankDirectory;
import com.banking.platform.bank.model.entity.LinkedBank;
import com.banking.platform.bank.model.entity.LinkStatus;
import com.banking.platform.bank.model.entity.VerificationMethod;
import com.banking.platform.bank.repository.BankDirectoryRepository;
import com.banking.platform.bank.repository.LinkedBankRepository;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import com.banking.platform.shared.exception.ValidationException;
import com.banking.platform.shared.util.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
@Transactional
public class BankService {

    private final LinkedBankRepository linkedBankRepository;
    private final BankDirectoryRepository bankDirectoryRepository;
    private final BankMapper bankMapper;

    private static final int MAX_VERIFICATION_ATTEMPTS = 3;

    @CacheEvict(value = "bank-details", key = "#request.customerId")
    public LinkedBankResponse linkBank(LinkBankRequest request) {
        log.info("Linking bank for customer: {}", request.customerId());

        // Validate routing number exists in directory
        BankDirectory bankDirectory = bankDirectoryRepository.findByRoutingNumber(request.routingNumber())
                .orElseThrow(() -> {
                    log.warn("Invalid routing number: {}", request.routingNumber());
                    return new ValidationException("Invalid routing number. Bank not found in directory.");
                });

        if (!bankDirectory.isActive()) {
            log.warn("Bank is inactive for routing number: {}", request.routingNumber());
            throw new ValidationException("Selected bank is no longer active.");
        }

        // Check for duplicate
        boolean exists = linkedBankRepository.existsByCustomerIdAndRoutingNumberAndAccountNumber(
                request.customerId(),
                request.routingNumber(),
                request.accountNumber()
        );
        if (exists) {
            log.warn("Duplicate bank link attempt for customer: {} with routing: {}",
                    request.customerId(), request.routingNumber());
            throw new ValidationException("This bank account is already linked to your profile.");
        }

        // Create linked bank entity
        LinkedBank linkedBank = LinkedBank.builder()
                .customerId(request.customerId())
                .bankName(bankDirectory.getBankName())
                .routingNumber(request.routingNumber())
                .accountNumber(request.accountNumber())
                .accountHolderName(request.accountHolderName())
                .nickname(request.nickname())
                .accountType(request.accountType())
                .verificationMethod(request.verificationMethod())
                .isPrimary(false)
                .verificationAttempts(0)
                .build();

        // Handle verification method
        if (VerificationMethod.MICRO_DEPOSIT.equals(request.verificationMethod())) {
            linkedBank.setLinkStatus(LinkStatus.PENDING_VERIFICATION);
            // Generate random micro-deposits
            linkedBank.setMicroDeposit1(generateRandomDeposit());
            linkedBank.setMicroDeposit2(generateRandomDeposit());
            log.debug("Generated micro-deposits for bank link: {}", linkedBank.getId());
        } else if (VerificationMethod.INSTANT.equals(request.verificationMethod())) {
            linkedBank.setLinkStatus(LinkStatus.VERIFIED);
            linkedBank.setVerifiedAt(Instant.now());
            log.info("Bank linked with instant verification for customer: {}", request.customerId());
        } else {
            linkedBank.setLinkStatus(LinkStatus.PENDING_VERIFICATION);
            log.info("Bank linked with manual verification for customer: {}", request.customerId());
        }

        linkedBank = linkedBankRepository.save(linkedBank);
        log.info("Bank successfully linked for customer: {} with ID: {}", request.customerId(), linkedBank.getId());
        return bankMapper.toLinkedBankResponse(linkedBank);
    }

    @CacheEvict(value = "bank-details", key = "#bankId")
    public LinkedBankResponse verifyMicroDeposits(UUID bankId, VerifyMicroDepositsRequest request) {
        log.info("Verifying micro-deposits for bank: {}", bankId);

        LinkedBank linkedBank = linkedBankRepository.findById(bankId)
                .orElseThrow(() -> {
                    log.warn("Bank not found: {}", bankId);
                    return new ResourceNotFoundException("Bank account not found.");
                });

        if (!LinkStatus.PENDING_VERIFICATION.equals(linkedBank.getLinkStatus())) {
            log.warn("Bank is not in pending verification status: {}", bankId);
            throw new ValidationException("This bank account is not pending verification.");
        }

        if (linkedBank.getVerificationAttempts() >= MAX_VERIFICATION_ATTEMPTS) {
            log.warn("Max verification attempts exceeded for bank: {}", bankId);
            linkedBank.setLinkStatus(LinkStatus.FAILED);
            linkedBankRepository.save(linkedBank);
            throw new ValidationException("Maximum verification attempts exceeded. Please relink your account.");
        }

        // Validate amounts match
        if (!linkedBank.getMicroDeposit1().equals(request.amount1()) ||
                !linkedBank.getMicroDeposit2().equals(request.amount2())) {
            linkedBank.setVerificationAttempts(linkedBank.getVerificationAttempts() + 1);
            linkedBankRepository.save(linkedBank);
            log.warn("Micro-deposit verification failed for bank: {}. Attempts: {}",
                    bankId, linkedBank.getVerificationAttempts());
            throw new ValidationException("Deposit amounts do not match. Please try again. " +
                    "Attempts remaining: " + (MAX_VERIFICATION_ATTEMPTS - linkedBank.getVerificationAttempts()));
        }

        // Update status
        linkedBank.setLinkStatus(LinkStatus.VERIFIED);
        linkedBank.setVerifiedAt(Instant.now());
        linkedBank = linkedBankRepository.save(linkedBank);
        log.info("Bank account verified successfully: {}", bankId);
        return bankMapper.toLinkedBankResponse(linkedBank);
    }

    @Cacheable(value = "bank-details", key = "#customerId")
    public List<LinkedBankResponse> getLinkedBanks(UUID customerId) {
        log.debug("Fetching linked banks for customer: {}", customerId);
        List<LinkedBank> banks = linkedBankRepository.findByCustomerId(customerId);
        return banks.stream()
                .map(bankMapper::toLinkedBankResponse)
                .collect(Collectors.toList());
    }

    public LinkedBankResponse getLinkedBank(UUID bankId) {
        log.debug("Fetching linked bank: {}", bankId);
        LinkedBank linkedBank = linkedBankRepository.findById(bankId)
                .orElseThrow(() -> {
                    log.warn("Bank not found: {}", bankId);
                    return new ResourceNotFoundException("Bank account not found.");
                });
        return bankMapper.toLinkedBankResponse(linkedBank);
    }

    @CacheEvict(value = "bank-details", key = "#customerId")
    public void setPrimary(UUID customerId, UUID bankId) {
        log.info("Setting primary bank for customer: {} to bank: {}", customerId, bankId);

        LinkedBank newPrimary = linkedBankRepository.findById(bankId)
                .orElseThrow(() -> {
                    log.warn("Bank not found: {}", bankId);
                    return new ResourceNotFoundException("Bank account not found.");
                });

        if (!newPrimary.getCustomerId().equals(customerId)) {
            log.warn("Unauthorized primary bank change attempt for customer: {}", customerId);
            throw new ValidationException("This bank account does not belong to you.");
        }

        if (!LinkStatus.VERIFIED.equals(newPrimary.getLinkStatus())) {
            log.warn("Cannot set unverified bank as primary: {}", bankId);
            throw new ValidationException("Only verified bank accounts can be set as primary.");
        }

        // Unset all others
        List<LinkedBank> allBanks = linkedBankRepository.findByCustomerId(customerId);
        allBanks.forEach(bank -> bank.setPrimary(false));
        linkedBankRepository.saveAll(allBanks);

        // Set this one as primary
        newPrimary.setPrimary(true);
        linkedBankRepository.save(newPrimary);
        log.info("Primary bank set successfully for customer: {}", customerId);
    }

    @CacheEvict(value = "bank-details", key = "#bankId")
    public void removeBank(UUID bankId) {
        log.info("Removing bank account: {}", bankId);

        LinkedBank linkedBank = linkedBankRepository.findById(bankId)
                .orElseThrow(() -> {
                    log.warn("Bank not found: {}", bankId);
                    return new ResourceNotFoundException("Bank account not found.");
                });

        linkedBank.setLinkStatus(LinkStatus.REMOVED);
        linkedBank.setPrimary(false);
        linkedBankRepository.save(linkedBank);
        log.info("Bank account removed: {}", bankId);
    }

    public BankDirectoryResponse lookupRouting(String routingNumber) {
        log.debug("Looking up routing number: {}", routingNumber);
        BankDirectory bankDirectory = bankDirectoryRepository.findByRoutingNumber(routingNumber)
                .orElseThrow(() -> {
                    log.warn("Routing number not found: {}", routingNumber);
                    return new ResourceNotFoundException("Bank with this routing number not found.");
                });
        return bankMapper.toBankDirectoryResponse(bankDirectory);
    }

    public PagedResponse<BankDirectoryResponse> searchBanks(String query, int page, int size) {
        log.debug("Searching banks with query: {}", query);
        Pageable pageable = PageRequest.of(page, size);
        Page<BankDirectory> result = bankDirectoryRepository.searchByBankNameContainingIgnoreCase(query, pageable);

        List<BankDirectoryResponse> content = result.getContent().stream()
                .map(bankMapper::toBankDirectoryResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast()
        );
    }

    private BigDecimal generateRandomDeposit() {
        // Generate random micro-deposit between 0.01 and 0.99
        return new BigDecimal(String.format("0.%02d", (int)(Math.random() * 99) + 1));
    }
}
