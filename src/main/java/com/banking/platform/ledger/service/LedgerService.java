package com.banking.platform.ledger.service;

import com.banking.platform.ledger.mapper.LedgerMapper;
import com.banking.platform.ledger.model.dto.*;
import com.banking.platform.ledger.model.entity.*;
import com.banking.platform.ledger.model.event.LedgerEvent;
import com.banking.platform.ledger.repository.*;
import com.banking.platform.shared.exception.BusinessException;
import com.banking.platform.shared.exception.DuplicateResourceException;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LedgerService {

    private final GlAccountRepository glAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;
    private final PostingRuleRepository postingRuleRepository;
    private final LedgerMapper ledgerMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String LEDGER_TOPIC = "ledger-events";

    // ==================== GL Account Operations ====================

    @Transactional
    @CacheEvict(value = "gl-accounts", allEntries = true)
    public GlAccountResponse createGlAccount(CreateGlAccountRequest request) {
        log.info("Creating GL account: {} - {}", request.accountCode(), request.name());

        if (glAccountRepository.existsByAccountCode(request.accountCode())) {
            throw new DuplicateResourceException("GL account with code " + request.accountCode() + " already exists");
        }

        if (request.parentId() != null) {
            glAccountRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent GL account", request.parentId().toString()));
        }

        GlAccount account = GlAccount.builder()
                .accountCode(request.accountCode())
                .name(request.name())
                .description(request.description())
                .accountType(request.accountType())
                .normalBalance(request.normalBalance())
                .parentId(request.parentId())
                .active(true)
                .build();

        GlAccount saved = glAccountRepository.save(account);
        log.info("GL account created: {}", saved.getId());
        return ledgerMapper.toGlAccountResponse(saved);
    }

    @Cacheable(value = "gl-accounts", key = "'all'")
    public List<GlAccountResponse> getAllGlAccounts() {
        return glAccountRepository.findByActiveTrue()
                .stream()
                .map(ledgerMapper::toGlAccountResponse)
                .collect(Collectors.toList());
    }

    public GlAccountResponse getGlAccount(UUID id) {
        GlAccount account = glAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GL Account", id.toString()));
        return ledgerMapper.toGlAccountResponse(account);
    }

    public GlAccountResponse getGlAccountByCode(String accountCode) {
        GlAccount account = glAccountRepository.findByAccountCode(accountCode)
                .orElseThrow(() -> new ResourceNotFoundException("GL Account with code: " + accountCode));
        return ledgerMapper.toGlAccountResponse(account);
    }

    // ==================== Journal Entry Operations ====================

    @Transactional
    public JournalEntryResponse createJournalEntry(CreateJournalEntryRequest request) {
        log.info("Creating journal entry: {}", request.description());

        // Validate that debits equal credits
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (CreateJournalEntryRequest.JournalEntryLineRequest lineReq : request.lines()) {
            BigDecimal debit = lineReq.debitAmount() != null ? lineReq.debitAmount() : BigDecimal.ZERO;
            BigDecimal credit = lineReq.creditAmount() != null ? lineReq.creditAmount() : BigDecimal.ZERO;

            if (debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException("INVALID_ENTRY", "A line cannot have both debit and credit amounts");
            }
            if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
                throw new BusinessException("INVALID_ENTRY", "A line must have either a debit or credit amount");
            }

            totalDebits = totalDebits.add(debit);
            totalCredits = totalCredits.add(credit);
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new BusinessException("UNBALANCED_ENTRY",
                    String.format("Journal entry is not balanced. Debits: %s, Credits: %s", totalDebits, totalCredits));
        }

        String entryNumber = generateEntryNumber();

        JournalEntry entry = JournalEntry.builder()
                .entryNumber(entryNumber)
                .description(request.description())
                .postingDate(request.postingDate())
                .status(JournalEntryStatus.DRAFT)
                .referenceId(request.referenceId())
                .referenceType(request.referenceType())
                .createdBy(request.createdBy())
                .lines(new ArrayList<>())
                .build();

        for (CreateJournalEntryRequest.JournalEntryLineRequest lineReq : request.lines()) {
            GlAccount glAccount = glAccountRepository.findByAccountCode(lineReq.accountCode())
                    .orElseThrow(() -> new ResourceNotFoundException("GL Account with code: " + lineReq.accountCode()));

            JournalEntryLine line = JournalEntryLine.builder()
                    .glAccountId(glAccount.getId())
                    .accountCode(lineReq.accountCode())
                    .debitAmount(lineReq.debitAmount() != null ? lineReq.debitAmount() : BigDecimal.ZERO)
                    .creditAmount(lineReq.creditAmount() != null ? lineReq.creditAmount() : BigDecimal.ZERO)
                    .description(lineReq.description())
                    .build();

            entry.addLine(line);
        }

        JournalEntry saved = journalEntryRepository.save(entry);

        publishLedgerEvent(saved, "journal_entry.created", totalDebits);

        log.info("Journal entry created: {} ({})", saved.getId(), entryNumber);
        return ledgerMapper.toJournalEntryResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "trial-balance", allEntries = true)
    public JournalEntryResponse postJournalEntry(UUID journalEntryId) {
        log.info("Posting journal entry: {}", journalEntryId);

        JournalEntry entry = journalEntryRepository.findById(journalEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry", journalEntryId.toString()));

        if (entry.getStatus() != JournalEntryStatus.DRAFT) {
            throw new BusinessException("INVALID_STATUS",
                    "Only DRAFT journal entries can be posted. Current status: " + entry.getStatus());
        }

        entry.setStatus(JournalEntryStatus.POSTED);
        JournalEntry saved = journalEntryRepository.save(entry);

        BigDecimal totalAmount = entry.getLines().stream()
                .map(JournalEntryLine::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        publishLedgerEvent(saved, "journal_entry.posted", totalAmount);

        log.info("Journal entry posted: {}", saved.getEntryNumber());
        return ledgerMapper.toJournalEntryResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "trial-balance", allEntries = true)
    public JournalEntryResponse reverseJournalEntry(UUID journalEntryId, String reason) {
        log.info("Reversing journal entry: {}", journalEntryId);

        JournalEntry original = journalEntryRepository.findById(journalEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry", journalEntryId.toString()));

        if (original.getStatus() != JournalEntryStatus.POSTED) {
            throw new BusinessException("INVALID_STATUS",
                    "Only POSTED journal entries can be reversed. Current status: " + original.getStatus());
        }

        // Mark original as reversed
        original.setStatus(JournalEntryStatus.REVERSED);
        journalEntryRepository.save(original);

        // Create reversal entry (swap debits and credits)
        String reversalNumber = generateEntryNumber();

        JournalEntry reversal = JournalEntry.builder()
                .entryNumber(reversalNumber)
                .description("REVERSAL: " + reason + " (Original: " + original.getEntryNumber() + ")")
                .postingDate(LocalDate.now())
                .status(JournalEntryStatus.POSTED)
                .referenceId(original.getReferenceId())
                .referenceType(original.getReferenceType())
                .createdBy("SYSTEM")
                .reversalOfId(original.getId())
                .lines(new ArrayList<>())
                .build();

        for (JournalEntryLine originalLine : original.getLines()) {
            JournalEntryLine reversalLine = JournalEntryLine.builder()
                    .glAccountId(originalLine.getGlAccountId())
                    .accountCode(originalLine.getAccountCode())
                    .debitAmount(originalLine.getCreditAmount())
                    .creditAmount(originalLine.getDebitAmount())
                    .description("Reversal of: " + (originalLine.getDescription() != null ? originalLine.getDescription() : ""))
                    .build();

            reversal.addLine(reversalLine);
        }

        JournalEntry savedReversal = journalEntryRepository.save(reversal);

        BigDecimal totalAmount = savedReversal.getLines().stream()
                .map(JournalEntryLine::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        publishLedgerEvent(savedReversal, "journal_entry.reversed", totalAmount);

        log.info("Journal entry reversed: {} -> {}", original.getEntryNumber(), reversalNumber);
        return ledgerMapper.toJournalEntryResponse(savedReversal);
    }

    public JournalEntryResponse getJournalEntry(UUID id) {
        JournalEntry entry = journalEntryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry", id.toString()));
        return ledgerMapper.toJournalEntryResponse(entry);
    }

    public PagedResponse<JournalEntryResponse> getJournalEntries(
            JournalEntryStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<JournalEntry> entries;

        if (status != null) {
            entries = journalEntryRepository.findByStatusOrderByPostingDateDesc(status, pageable);
        } else {
            entries = journalEntryRepository.findAll(pageable);
        }

        List<JournalEntryResponse> content = entries.getContent()
                .stream()
                .map(ledgerMapper::toJournalEntryResponse)
                .collect(Collectors.toList());

        return PagedResponse.<JournalEntryResponse>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(entries.getTotalElements())
                .totalPages(entries.getTotalPages())
                .last(entries.isLast())
                .build();
    }

    // ==================== Trial Balance ====================

    @Cacheable(value = "trial-balance", key = "'current'")
    public TrialBalanceResponse getTrialBalance() {
        log.info("Generating trial balance");

        List<Object[]> rawData = journalEntryLineRepository.findTrialBalanceData();
        List<GlAccountBalanceResponse> accounts = new ArrayList<>();
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (Object[] row : rawData) {
            String accountCode = (String) row[0];
            BigDecimal debits = (BigDecimal) row[1];
            BigDecimal credits = (BigDecimal) row[2];
            BigDecimal balance = debits.subtract(credits);

            GlAccount glAccount = glAccountRepository.findByAccountCode(accountCode).orElse(null);
            String accountName = glAccount != null ? glAccount.getName() : accountCode;
            GlAccountType accountType = glAccount != null ? glAccount.getAccountType() : null;

            accounts.add(new GlAccountBalanceResponse(
                    accountCode, accountName, accountType, debits, credits, balance
            ));

            totalDebits = totalDebits.add(debits);
            totalCredits = totalCredits.add(credits);
        }

        boolean balanced = totalDebits.compareTo(totalCredits) == 0;

        return new TrialBalanceResponse(accounts, totalDebits, totalCredits, balanced);
    }

    // ==================== Posting Rules ====================

    @Transactional
    public PostingRuleResponse createPostingRule(PostingRuleRequest request) {
        log.info("Creating posting rule: {} - {}", request.ruleCode(), request.triggerEvent());

        if (postingRuleRepository.existsByRuleCode(request.ruleCode())) {
            throw new DuplicateResourceException("Posting rule with code " + request.ruleCode() + " already exists");
        }

        // Validate that the GL accounts exist
        glAccountRepository.findByAccountCode(request.debitAccountCode())
                .orElseThrow(() -> new ResourceNotFoundException("Debit GL Account with code: " + request.debitAccountCode()));
        glAccountRepository.findByAccountCode(request.creditAccountCode())
                .orElseThrow(() -> new ResourceNotFoundException("Credit GL Account with code: " + request.creditAccountCode()));

        PostingRule rule = PostingRule.builder()
                .ruleCode(request.ruleCode())
                .name(request.name())
                .description(request.description())
                .triggerEvent(request.triggerEvent())
                .debitAccountCode(request.debitAccountCode())
                .creditAccountCode(request.creditAccountCode())
                .active(true)
                .build();

        PostingRule saved = postingRuleRepository.save(rule);
        log.info("Posting rule created: {}", saved.getId());
        return ledgerMapper.toPostingRuleResponse(saved);
    }

    public List<PostingRuleResponse> getAllPostingRules() {
        return postingRuleRepository.findByActiveTrue()
                .stream()
                .map(ledgerMapper::toPostingRuleResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void togglePostingRule(UUID ruleId, boolean active) {
        PostingRule rule = postingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Posting Rule", ruleId.toString()));
        rule.setActive(active);
        postingRuleRepository.save(rule);
    }

    // ==================== Helpers ====================

    private String generateEntryNumber() {
        String prefix = "JE-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        Long maxNum = journalEntryRepository.findMaxEntryNumberByPrefix(prefix);
        long next = (maxNum != null ? maxNum : 0) + 1;
        return prefix + String.format("%06d", next);
    }

    private void publishLedgerEvent(JournalEntry entry, String eventType, BigDecimal totalAmount) {
        try {
            LedgerEvent event = LedgerEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .timestamp(Instant.now())
                    .journalEntryId(entry.getId())
                    .entryNumber(entry.getEntryNumber())
                    .status(entry.getStatus())
                    .totalAmount(totalAmount)
                    .referenceType(entry.getReferenceType())
                    .referenceId(entry.getReferenceId())
                    .build();

            kafkaTemplate.send(LEDGER_TOPIC, entry.getId().toString(), event);
            log.debug("Ledger event published: {}", eventType);
        } catch (Exception e) {
            log.error("Failed to publish ledger event: {}", eventType, e);
        }
    }
}

