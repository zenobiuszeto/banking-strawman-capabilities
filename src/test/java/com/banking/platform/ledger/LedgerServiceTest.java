package com.banking.platform.ledger;

import com.banking.platform.ledger.mapper.LedgerMapper;
import com.banking.platform.ledger.model.dto.*;
import com.banking.platform.ledger.model.entity.*;
import com.banking.platform.ledger.repository.*;
import com.banking.platform.ledger.service.LedgerService;
import com.banking.platform.shared.exception.BusinessException;
import com.banking.platform.shared.exception.DuplicateResourceException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private GlAccountRepository glAccountRepository;

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Mock
    private JournalEntryLineRepository journalEntryLineRepository;

    @Mock
    private PostingRuleRepository postingRuleRepository;

    @Mock
    private LedgerMapper ledgerMapper;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private LedgerService ledgerService;

    private UUID glAccountId;

    @BeforeEach
    void setUp() {
        glAccountId = UUID.randomUUID();
    }

    @Test
    void testCreateGlAccount_Success() {
        CreateGlAccountRequest request = new CreateGlAccountRequest(
                "1000", "Cash", "Cash and equivalents",
                GlAccountType.ASSET, NormalBalance.DEBIT, null
        );

        GlAccount saved = GlAccount.builder()
                .id(glAccountId)
                .accountCode("1000")
                .name("Cash")
                .description("Cash and equivalents")
                .accountType(GlAccountType.ASSET)
                .normalBalance(NormalBalance.DEBIT)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        GlAccountResponse expectedResponse = new GlAccountResponse(
                glAccountId, "1000", "Cash", "Cash and equivalents",
                GlAccountType.ASSET, NormalBalance.DEBIT, null, true, Instant.now()
        );

        when(glAccountRepository.existsByAccountCode("1000")).thenReturn(false);
        when(glAccountRepository.save(any(GlAccount.class))).thenReturn(saved);
        when(ledgerMapper.toGlAccountResponse(saved)).thenReturn(expectedResponse);

        GlAccountResponse response = ledgerService.createGlAccount(request);

        assertNotNull(response);
        assertEquals("1000", response.accountCode());
        assertEquals(GlAccountType.ASSET, response.accountType());
        verify(glAccountRepository, times(1)).save(any(GlAccount.class));
    }

    @Test
    void testCreateGlAccount_DuplicateCode() {
        CreateGlAccountRequest request = new CreateGlAccountRequest(
                "1000", "Cash", null, GlAccountType.ASSET, NormalBalance.DEBIT, null
        );

        when(glAccountRepository.existsByAccountCode("1000")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> ledgerService.createGlAccount(request));
        verify(glAccountRepository, never()).save(any());
    }

    @Test
    void testCreateJournalEntry_Balanced() {
        CreateJournalEntryRequest request = new CreateJournalEntryRequest(
                "Test entry", LocalDate.now(), null, null, "admin",
                List.of(
                        new CreateJournalEntryRequest.JournalEntryLineRequest("1000", new BigDecimal("100.00"), null, "Debit cash"),
                        new CreateJournalEntryRequest.JournalEntryLineRequest("2000", null, new BigDecimal("100.00"), "Credit liability")
                )
        );

        GlAccount cashAccount = GlAccount.builder().id(UUID.randomUUID()).accountCode("1000").build();
        GlAccount liabilityAccount = GlAccount.builder().id(UUID.randomUUID()).accountCode("2000").build();

        JournalEntry savedEntry = JournalEntry.builder()
                .id(UUID.randomUUID())
                .entryNumber("JE-20260224-000001")
                .description("Test entry")
                .postingDate(LocalDate.now())
                .status(JournalEntryStatus.DRAFT)
                .lines(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        JournalEntryResponse expectedResponse = new JournalEntryResponse(
                savedEntry.getId(), "JE-20260224-000001", "Test entry", LocalDate.now(),
                JournalEntryStatus.DRAFT, null, null, "admin", null, List.of(), Instant.now()
        );

        when(glAccountRepository.findByAccountCode("1000")).thenReturn(Optional.of(cashAccount));
        when(glAccountRepository.findByAccountCode("2000")).thenReturn(Optional.of(liabilityAccount));
        when(journalEntryRepository.findMaxEntryNumberByPrefix(anyString())).thenReturn(null);
        when(journalEntryRepository.save(any(JournalEntry.class))).thenReturn(savedEntry);
        when(ledgerMapper.toJournalEntryResponse(savedEntry)).thenReturn(expectedResponse);

        JournalEntryResponse response = ledgerService.createJournalEntry(request);

        assertNotNull(response);
        assertEquals(JournalEntryStatus.DRAFT, response.status());
        verify(journalEntryRepository, times(1)).save(any(JournalEntry.class));
    }

    @Test
    void testCreateJournalEntry_Unbalanced() {
        CreateJournalEntryRequest request = new CreateJournalEntryRequest(
                "Unbalanced", LocalDate.now(), null, null, "admin",
                List.of(
                        new CreateJournalEntryRequest.JournalEntryLineRequest("1000", new BigDecimal("100.00"), null, null),
                        new CreateJournalEntryRequest.JournalEntryLineRequest("2000", null, new BigDecimal("50.00"), null)
                )
        );

        assertThrows(BusinessException.class, () -> ledgerService.createJournalEntry(request));
        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    void testPostJournalEntry_Success() {
        UUID entryId = UUID.randomUUID();
        JournalEntry entry = JournalEntry.builder()
                .id(entryId)
                .entryNumber("JE-001")
                .status(JournalEntryStatus.DRAFT)
                .lines(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        JournalEntry postedEntry = JournalEntry.builder()
                .id(entryId)
                .entryNumber("JE-001")
                .status(JournalEntryStatus.POSTED)
                .lines(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        JournalEntryResponse expectedResponse = new JournalEntryResponse(
                entryId, "JE-001", null, null, JournalEntryStatus.POSTED,
                null, null, null, null, List.of(), Instant.now()
        );

        when(journalEntryRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(journalEntryRepository.save(any(JournalEntry.class))).thenReturn(postedEntry);
        when(ledgerMapper.toJournalEntryResponse(postedEntry)).thenReturn(expectedResponse);

        JournalEntryResponse response = ledgerService.postJournalEntry(entryId);

        assertNotNull(response);
        assertEquals(JournalEntryStatus.POSTED, response.status());
    }

    @Test
    void testPostJournalEntry_AlreadyPosted() {
        UUID entryId = UUID.randomUUID();
        JournalEntry entry = JournalEntry.builder()
                .id(entryId)
                .status(JournalEntryStatus.POSTED)
                .lines(new ArrayList<>())
                .build();

        when(journalEntryRepository.findById(entryId)).thenReturn(Optional.of(entry));

        assertThrows(BusinessException.class, () -> ledgerService.postJournalEntry(entryId));
    }

    @Test
    void testReverseJournalEntry_NotPosted() {
        UUID entryId = UUID.randomUUID();
        JournalEntry entry = JournalEntry.builder()
                .id(entryId)
                .status(JournalEntryStatus.DRAFT)
                .lines(new ArrayList<>())
                .build();

        when(journalEntryRepository.findById(entryId)).thenReturn(Optional.of(entry));

        assertThrows(BusinessException.class, () -> ledgerService.reverseJournalEntry(entryId, "reason"));
    }

    @Test
    void testGetTrialBalance() {
        List<Object[]> rawData = List.of(
                new Object[]{"1000", new BigDecimal("1000.00"), new BigDecimal("200.00")},
                new Object[]{"2000", new BigDecimal("200.00"), new BigDecimal("1000.00")}
        );

        GlAccount cash = GlAccount.builder().accountCode("1000").name("Cash").accountType(GlAccountType.ASSET).build();
        GlAccount liability = GlAccount.builder().accountCode("2000").name("Deposits").accountType(GlAccountType.LIABILITY).build();

        when(journalEntryLineRepository.findTrialBalanceData()).thenReturn(rawData);
        when(glAccountRepository.findByAccountCode("1000")).thenReturn(Optional.of(cash));
        when(glAccountRepository.findByAccountCode("2000")).thenReturn(Optional.of(liability));

        TrialBalanceResponse response = ledgerService.getTrialBalance();

        assertNotNull(response);
        assertEquals(2, response.accounts().size());
        assertEquals(new BigDecimal("1200.00"), response.totalDebits());
        assertEquals(new BigDecimal("1200.00"), response.totalCredits());
        assertTrue(response.balanced());
    }

    @Test
    void testCreatePostingRule_Success() {
        PostingRuleRequest request = new PostingRuleRequest(
                "ACH_OUT", "ACH Outbound", "desc",
                "ACH_INITIATED", "2100", "1000"
        );

        GlAccount debitAcct = GlAccount.builder().accountCode("2100").build();
        GlAccount creditAcct = GlAccount.builder().accountCode("1000").build();

        PostingRule saved = PostingRule.builder()
                .id(UUID.randomUUID())
                .ruleCode("ACH_OUT")
                .name("ACH Outbound")
                .triggerEvent("ACH_INITIATED")
                .debitAccountCode("2100")
                .creditAccountCode("1000")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        PostingRuleResponse expectedResponse = new PostingRuleResponse(
                saved.getId(), "ACH_OUT", "ACH Outbound", "desc",
                "ACH_INITIATED", "2100", "1000", true, Instant.now()
        );

        when(postingRuleRepository.existsByRuleCode("ACH_OUT")).thenReturn(false);
        when(glAccountRepository.findByAccountCode("2100")).thenReturn(Optional.of(debitAcct));
        when(glAccountRepository.findByAccountCode("1000")).thenReturn(Optional.of(creditAcct));
        when(postingRuleRepository.save(any(PostingRule.class))).thenReturn(saved);
        when(ledgerMapper.toPostingRuleResponse(saved)).thenReturn(expectedResponse);

        PostingRuleResponse response = ledgerService.createPostingRule(request);

        assertNotNull(response);
        assertEquals("ACH_OUT", response.ruleCode());
        assertTrue(response.active());
    }

    @Test
    void testCreatePostingRule_DuplicateCode() {
        PostingRuleRequest request = new PostingRuleRequest(
                "ACH_OUT", "ACH Outbound", null,
                "ACH_INITIATED", "2100", "1000"
        );

        when(postingRuleRepository.existsByRuleCode("ACH_OUT")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> ledgerService.createPostingRule(request));
    }
}

