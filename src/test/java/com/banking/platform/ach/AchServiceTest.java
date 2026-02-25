package com.banking.platform.ach;

import com.banking.platform.ach.mapper.AchMapper;
import com.banking.platform.ach.model.dto.AchReturnRequest;
import com.banking.platform.ach.model.dto.AchTransferResponse;
import com.banking.platform.ach.model.dto.InitiateAchRequest;
import com.banking.platform.ach.model.entity.AchDirection;
import com.banking.platform.ach.model.entity.AchStatus;
import com.banking.platform.ach.model.entity.AchTransfer;
import com.banking.platform.ach.model.entity.AchType;
import com.banking.platform.ach.model.entity.SecCode;
import com.banking.platform.ach.model.event.AchEvent;
import com.banking.platform.ach.repository.AchTransferRepository;
import com.banking.platform.ach.service.AchService;
import com.banking.platform.shared.exception.InvalidOperationException;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AchService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ACH Service Tests")
class AchServiceTest {

    @Mock
    private AchTransferRepository achTransferRepository;

    @Mock
    private AchMapper achMapper;

    @Mock
    private KafkaTemplate<String, AchEvent> kafkaTemplate;

    private AchService achService;

    private UUID testAccountId;
    private UUID testLinkedBankId;
    private UUID testTransferId;

    @BeforeEach
    void setUp() {
        achService = new AchService(achTransferRepository, achMapper, kafkaTemplate);
        testAccountId = UUID.randomUUID();
        testLinkedBankId = UUID.randomUUID();
        testTransferId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Successfully initiate ACH transfer with valid amount")
    void testInitiateTransferSuccess() {
        // Arrange
        InitiateAchRequest request = new InitiateAchRequest(
            testAccountId,
            testLinkedBankId,
            AchDirection.OUTBOUND,
            AchType.STANDARD,
            new BigDecimal("5000.00"),
            "Test payment",
            SecCode.PPD,
            LocalDate.now().plusDays(1)
        );

        AchTransfer transfer = createTestTransfer();
        AchTransferResponse expectedResponse = createTestTransferResponse();

        when(achTransferRepository.save(any(AchTransfer.class))).thenReturn(transfer);
        when(achMapper.toEntity(any(), anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(transfer);
        when(achMapper.toResponse(transfer)).thenReturn(expectedResponse);

        // Act
        AchTransferResponse response = achService.initiateTransfer(request);

        // Assert
        assertNotNull(response);
        assertEquals(testTransferId, response.id());
        assertEquals(AchStatus.INITIATED, response.status());
        verify(achTransferRepository, times(1)).save(any(AchTransfer.class));
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any(AchEvent.class));
    }

    @Test
    @DisplayName("Fail to initiate transfer with amount exceeding standard limit")
    void testInitiateTransferAmountLimitExceeded() {
        // Arrange
        InitiateAchRequest request = new InitiateAchRequest(
            testAccountId,
            testLinkedBankId,
            AchDirection.OUTBOUND,
            AchType.STANDARD,
            new BigDecimal("30000.00"), // Exceeds $25,000 standard limit
            "Test payment",
            SecCode.PPD,
            LocalDate.now().plusDays(1)
        );

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> achService.initiateTransfer(request));
        verify(achTransferRepository, times(0)).save(any(AchTransfer.class));
    }

    @Test
    @DisplayName("Fail to initiate transfer with amount exceeding same-day limit")
    void testInitiateTransferSameDayAmountLimitExceeded() {
        // Arrange
        InitiateAchRequest request = new InitiateAchRequest(
            testAccountId,
            testLinkedBankId,
            AchDirection.OUTBOUND,
            AchType.SAME_DAY,
            new BigDecimal("150000.00"), // Exceeds $100,000 same-day limit
            "Test payment",
            SecCode.PPD,
            LocalDate.now().plusDays(1)
        );

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> achService.initiateTransfer(request));
        verify(achTransferRepository, times(0)).save(any(AchTransfer.class));
    }

    @Test
    @DisplayName("Successfully cancel transfer with INITIATED status")
    void testCancelTransferSuccess() {
        // Arrange
        AchTransfer transfer = createTestTransfer();
        transfer.setStatus(AchStatus.INITIATED);

        AchTransferResponse expectedResponse = createTestTransferResponse();

        when(achTransferRepository.findById(testTransferId)).thenReturn(Optional.of(transfer));
        when(achTransferRepository.save(any(AchTransfer.class))).thenReturn(transfer);
        when(achMapper.toResponse(transfer)).thenReturn(expectedResponse);

        // Act
        AchTransferResponse response = achService.cancelTransfer(testTransferId);

        // Assert
        assertNotNull(response);
        assertEquals(AchStatus.INITIATED, response.status()); // Still shows initial status from mock
        verify(achTransferRepository, times(1)).save(any(AchTransfer.class));
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any(AchEvent.class));
    }

    @Test
    @DisplayName("Fail to cancel transfer with SETTLED status")
    void testCancelTransferInvalidStatus() {
        // Arrange
        AchTransfer transfer = createTestTransfer();
        transfer.setStatus(AchStatus.SETTLED);

        when(achTransferRepository.findById(testTransferId)).thenReturn(Optional.of(transfer));

        // Act & Assert
        assertThrows(InvalidOperationException.class, () -> achService.cancelTransfer(testTransferId));
        verify(achTransferRepository, times(0)).save(any(AchTransfer.class));
    }

    @Test
    @DisplayName("Fail to cancel transfer that does not exist")
    void testCancelTransferNotFound() {
        // Arrange
        when(achTransferRepository.findById(testTransferId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> achService.cancelTransfer(testTransferId));
    }

    @Test
    @DisplayName("Successfully process ACH return")
    void testProcessReturnSuccess() {
        // Arrange
        String traceNumber = "000000001000001";
        AchReturnRequest request = new AchReturnRequest(
            traceNumber,
            "R01",
            "Account closed"
        );

        AchTransfer transfer = createTestTransfer();
        AchTransferResponse expectedResponse = createTestTransferResponse();

        when(achTransferRepository.findByTraceNumber(traceNumber)).thenReturn(Optional.of(transfer));
        when(achTransferRepository.save(any(AchTransfer.class))).thenReturn(transfer);
        when(achMapper.toResponse(transfer)).thenReturn(expectedResponse);

        // Act
        AchTransferResponse response = achService.processReturn(request);

        // Assert
        assertNotNull(response);
        verify(achTransferRepository, times(1)).save(any(AchTransfer.class));
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any(AchEvent.class));
    }

    @Test
    @DisplayName("Fail to process return for non-existent transfer")
    void testProcessReturnNotFound() {
        // Arrange
        String traceNumber = "999999999999999";
        AchReturnRequest request = new AchReturnRequest(
            traceNumber,
            "R01",
            "Account closed"
        );

        when(achTransferRepository.findByTraceNumber(traceNumber)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> achService.processReturn(request));
    }

    @Test
    @DisplayName("Successfully get transfer by ID")
    void testGetTransferSuccess() {
        // Arrange
        AchTransfer transfer = createTestTransfer();
        AchTransferResponse expectedResponse = createTestTransferResponse();

        when(achTransferRepository.findById(testTransferId)).thenReturn(Optional.of(transfer));
        when(achMapper.toResponse(transfer)).thenReturn(expectedResponse);

        // Act
        AchTransferResponse response = achService.getTransfer(testTransferId);

        // Assert
        assertNotNull(response);
        assertEquals(testTransferId, response.id());
    }

    @Test
    @DisplayName("Fail to get transfer that does not exist")
    void testGetTransferNotFound() {
        // Arrange
        when(achTransferRepository.findById(testTransferId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> achService.getTransfer(testTransferId));
    }

    @Test
    @DisplayName("Successfully approve transfer")
    void testApproveTransferSuccess() {
        // Arrange
        AchTransfer transfer = createTestTransfer();
        transfer.setStatus(AchStatus.INITIATED);

        AchTransferResponse expectedResponse = createTestTransferResponse();

        when(achTransferRepository.findById(testTransferId)).thenReturn(Optional.of(transfer));
        when(achTransferRepository.save(any(AchTransfer.class))).thenReturn(transfer);
        when(achMapper.toResponse(transfer)).thenReturn(expectedResponse);

        // Act
        AchTransferResponse response = achService.approveTransfer(testTransferId);

        // Assert
        assertNotNull(response);
        verify(achTransferRepository, times(1)).save(any(AchTransfer.class));
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any(AchEvent.class));
    }

    // Helper methods

    private AchTransfer createTestTransfer() {
        return AchTransfer.builder()
            .id(testTransferId)
            .accountId(testAccountId)
            .linkedBankId(testLinkedBankId)
            .traceNumber("000000001000001")
            .direction(AchDirection.OUTBOUND)
            .achType(AchType.STANDARD)
            .status(AchStatus.INITIATED)
            .secCode(SecCode.PPD)
            .amount(new BigDecimal("5000.00"))
            .senderName("Sender Name")
            .senderRoutingNumber("000000000")
            .senderAccountNumber("00000000000000")
            .receiverName("Receiver Name")
            .receiverRoutingNumber("000000001")
            .receiverAccountNumber("00000000000001")
            .companyName("Banking Platform Inc")
            .companyId("1234567890")
            .entryDescription("ACH")
            .memo("Test payment")
            .effectiveDate(LocalDate.now().plusDays(1))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private AchTransferResponse createTestTransferResponse() {
        return new AchTransferResponse(
            testTransferId,
            "000000001000001",
            AchDirection.OUTBOUND,
            AchType.STANDARD,
            AchStatus.INITIATED,
            new BigDecimal("5000.00"),
            "Sender Name",
            "Receiver Name",
            LocalDate.now().plusDays(1),
            null,
            null,
            Instant.now()
        );
    }
}
