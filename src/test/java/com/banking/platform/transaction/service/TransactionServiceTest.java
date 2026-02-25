package com.banking.platform.transaction.service;

import com.banking.platform.shared.exception.EntityNotFoundException;
import com.banking.platform.shared.exception.ValidationException;
import com.banking.platform.shared.util.PagedResponse;
import com.banking.platform.transaction.mapper.TransactionMapper;
import com.banking.platform.transaction.model.dto.CreateTransactionRequest;
import com.banking.platform.transaction.model.dto.TransactionResponse;
import com.banking.platform.transaction.model.dto.TransactionSearchRequest;
import com.banking.platform.transaction.model.dto.TransactionSummaryResponse;
import com.banking.platform.transaction.model.entity.Transaction;
import com.banking.platform.transaction.model.entity.TransactionCategory;
import com.banking.platform.transaction.model.entity.TransactionStatus;
import com.banking.platform.transaction.model.entity.TransactionType;
import com.banking.platform.transaction.model.event.TransactionEvent;
import com.banking.platform.transaction.repository.TransactionRepository;
import org.springframework.data.jpa.domain.Specification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionService.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @InjectMocks
    private TransactionService transactionService;

    private UUID accountId;
    private UUID transactionId;
    private CreateTransactionRequest createRequest;
    private Transaction transaction;
    private TransactionResponse transactionResponse;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        createRequest = new CreateTransactionRequest(
            accountId,
            null,
            TransactionType.DEBIT,
            TransactionCategory.POS_PURCHASE,
            new BigDecimal("100.00"),
            "Coffee purchase",
            "Coffee Shop",
            "5411",
            "ONLINE"
        );

        transaction = Transaction.builder()
            .id(transactionId)
            .accountId(accountId)
            .referenceNumber("TXN-1234567890-ABCD1234")
            .type(TransactionType.DEBIT)
            .category(TransactionCategory.POS_PURCHASE)
            .status(TransactionStatus.POSTED)
            .amount(new BigDecimal("100.00"))
            .runningBalance(new BigDecimal("5900.00"))
            .description("Coffee purchase")
            .merchantName("Coffee Shop")
            .postDate(LocalDate.now())
            .effectiveDate(LocalDate.now())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        transactionResponse = new TransactionResponse(
            transactionId,
            "TXN-1234567890-ABCD1234",
            TransactionType.DEBIT,
            TransactionCategory.POS_PURCHASE,
            TransactionStatus.POSTED,
            new BigDecimal("100.00"),
            new BigDecimal("5900.00"),
            "Coffee purchase",
            "Coffee Shop",
            LocalDate.now(),
            LocalDate.now(),
            Instant.now()
        );
    }

    @Test
    void testCreateTransaction_Success() {
        // Arrange
        when(transactionMapper.toEntity(createRequest)).thenReturn(transaction);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);
        when(transactionMapper.toResponse(transaction)).thenReturn(transactionResponse);

        // Act
        TransactionResponse result = transactionService.createTransaction(createRequest);

        // Assert
        assertNotNull(result);
        assertEquals(transactionResponse.id(), result.id());
        assertEquals(transactionResponse.amount(), result.amount());
        verify(transactionRepository).save(any(Transaction.class));
        verify(kafkaTemplate).send(anyString(), anyString(), any(TransactionEvent.class));
    }

    @Test
    void testCreateTransaction_InvalidAmount() {
        // Arrange
        CreateTransactionRequest invalidRequest = new CreateTransactionRequest(
            accountId,
            null,
            TransactionType.DEBIT,
            TransactionCategory.POS_PURCHASE,
            BigDecimal.ZERO,
            "Invalid",
            "Merchant",
            null,
            "ONLINE"
        );
        Transaction invalidTransaction = Transaction.builder()
            .accountId(accountId)
            .type(TransactionType.DEBIT)
            .category(TransactionCategory.POS_PURCHASE)
            .amount(BigDecimal.ZERO)
            .description("Invalid")
            .merchantName("Merchant")
            .channel("ONLINE")
            .build();
        when(transactionMapper.toEntity(invalidRequest)).thenReturn(invalidTransaction);

        // Act & Assert
        assertThrows(ValidationException.class, () -> transactionService.createTransaction(invalidRequest));
    }

    @Test
    void testGetTransaction_Success() {
        // Arrange
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(transactionMapper.toResponse(transaction)).thenReturn(transactionResponse);

        // Act
        TransactionResponse result = transactionService.getTransaction(transactionId);

        // Assert
        assertNotNull(result);
        assertEquals(transactionResponse.id(), result.id());
        verify(transactionRepository).findById(transactionId);
    }

    @Test
    void testGetTransaction_NotFound() {
        // Arrange
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> transactionService.getTransaction(transactionId));
    }

    @Test
    void testGetByReference_Success() {
        // Arrange
        String referenceNumber = "TXN-1234567890-ABCD1234";
        when(transactionRepository.findByReferenceNumber(referenceNumber)).thenReturn(Optional.of(transaction));
        when(transactionMapper.toResponse(transaction)).thenReturn(transactionResponse);

        // Act
        TransactionResponse result = transactionService.getByReference(referenceNumber);

        // Assert
        assertNotNull(result);
        assertEquals(transactionResponse.referenceNumber(), result.referenceNumber());
    }

    @Test
    void testGetByReference_NotFound() {
        // Arrange
        String referenceNumber = "INVALID-REF";
        when(transactionRepository.findByReferenceNumber(referenceNumber)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> transactionService.getByReference(referenceNumber));
    }

    @Test
    void testListTransactions_Success() {
        // Arrange
        List<Transaction> transactions = List.of(transaction);
        Page<Transaction> page = new PageImpl<>(transactions);
        when(transactionRepository.findByAccountIdOrderByCreatedAtDesc(eq(accountId), any(Pageable.class)))
            .thenReturn(page);
        when(transactionMapper.toResponse(transaction)).thenReturn(transactionResponse);

        // Act
        PagedResponse<TransactionResponse> result = transactionService.listTransactions(accountId, 0, 20);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1, result.getTotalElements());
        verify(transactionRepository).findByAccountIdOrderByCreatedAtDesc(eq(accountId), any(Pageable.class));
    }

    @Test
    void testSearchTransactions_Success() {
        // Arrange
        LocalDate fromDate = LocalDate.now().minusDays(30);
        LocalDate toDate = LocalDate.now();
        TransactionSearchRequest searchRequest = new TransactionSearchRequest(
            accountId,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            fromDate,
            toDate,
            Optional.empty()
        );

        List<Transaction> transactions = List.of(transaction);
        Page<Transaction> page = new PageImpl<>(transactions);
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(transactionMapper.toResponse(transaction)).thenReturn(transactionResponse);

        // Act
        PagedResponse<TransactionResponse> result = transactionService.searchTransactions(searchRequest, 0, 20);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(transactionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void testGetTransactionSummary_Success() {
        // Arrange
        LocalDate fromDate = LocalDate.now().minusDays(30);
        LocalDate toDate = LocalDate.now();
        BigDecimal totalCredits = new BigDecimal("5000.00");
        BigDecimal totalDebits = new BigDecimal("2000.00");
        int count = 15;

        when(transactionRepository.sumByAccountIdAndTypeAndDateRange(
            accountId,
            TransactionType.CREDIT,
            fromDate,
            toDate
        )).thenReturn(totalCredits);

        when(transactionRepository.sumByAccountIdAndTypeAndDateRange(
            accountId,
            TransactionType.DEBIT,
            fromDate,
            toDate
        )).thenReturn(totalDebits);

        when(transactionRepository.countByAccountIdAndPostDateBetween(accountId, fromDate, toDate))
            .thenReturn(count);

        // Act
        TransactionSummaryResponse result = transactionService.getTransactionSummary(accountId, fromDate, toDate);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.accountId());
        assertEquals(totalCredits, result.totalCredits());
        assertEquals(totalDebits, result.totalDebits());
        assertEquals(new BigDecimal("3000.00"), result.netChange());
        assertEquals(count, result.transactionCount());
    }

    @Test
    void testReverseTransaction_Success() {
        // Arrange
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);
        when(transactionMapper.toResponse(transaction)).thenReturn(transactionResponse);

        // Act
        TransactionResponse result = transactionService.reverseTransaction(transactionId);

        // Assert
        assertNotNull(result);
        verify(transactionRepository).save(any(Transaction.class));
        verify(kafkaTemplate).send(anyString(), anyString(), any(TransactionEvent.class));
    }

    @Test
    void testReverseTransaction_AlreadyReversed() {
        // Arrange
        transaction.setStatus(TransactionStatus.REVERSED);
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        // Act & Assert
        assertThrows(ValidationException.class, () -> transactionService.reverseTransaction(transactionId));
    }

    @Test
    void testReverseTransaction_FailedTransaction() {
        // Arrange
        transaction.setStatus(TransactionStatus.FAILED);
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        // Act & Assert
        assertThrows(ValidationException.class, () -> transactionService.reverseTransaction(transactionId));
    }

    @Test
    void testReverseTransaction_NotFound() {
        // Arrange
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> transactionService.reverseTransaction(transactionId));
    }
}
