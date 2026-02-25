package com.banking.platform.wire;

import com.banking.platform.shared.exception.ResourceNotFoundException;
import com.banking.platform.wire.mapper.WireMapper;
import com.banking.platform.wire.model.dto.InitiateWireRequest;
import com.banking.platform.wire.model.dto.WireTransferResponse;
import com.banking.platform.wire.model.entity.WireStatus;
import com.banking.platform.wire.model.entity.WireTransfer;
import com.banking.platform.wire.model.entity.WireType;
import com.banking.platform.wire.repository.WireTransferRepository;
import com.banking.platform.wire.model.event.WireEvent;
import com.banking.platform.wire.service.WireService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WireServiceTest {

    @Mock
    private WireTransferRepository wireTransferRepository;

    @Mock
    private WireMapper wireMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WireService wireService;

    private UUID testAccountId;
    private UUID testWireId;

    @BeforeEach
    void setUp() {
        testAccountId = UUID.randomUUID();
        testWireId = UUID.randomUUID();
    }

    @Test
    void testInitiateWire_Success() {
        // Arrange
        InitiateWireRequest request = new InitiateWireRequest(
            testAccountId,
            WireType.DOMESTIC,
            new BigDecimal("1000.00"),
            "USD",
            "John Doe",
            "123456789",
            "987654321",
            "Test Bank",
            null,
            null,
            null,
            null,
            "Payment for services",
            "Test memo"
        );

        WireTransfer savedWire = WireTransfer.builder()
            .id(testWireId)
            .accountId(testAccountId)
            .wireReferenceNumber("WIRE-ABC12345-1234")
            .wireType(WireType.DOMESTIC)
            .status(WireStatus.INITIATED)
            .amount(new BigDecimal("1000.00"))
            .fee(new BigDecimal("25.00"))
            .currency("USD")
            .senderName("Account Holder")
            .senderAccountNumber("123456789012")
            .senderRoutingNumber("021000021")
            .senderBankName("Digital Banking Platform")
            .beneficiaryName("John Doe")
            .beneficiaryAccountNumber("123456789")
            .beneficiaryRoutingNumber("987654321")
            .beneficiaryBankName("Test Bank")
            .purposeOfWire("Payment for services")
            .memo("Test memo")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        WireTransferResponse expectedResponse = new WireTransferResponse(
            testWireId, testAccountId, "WIRE-ABC12345-1234", null, WireType.DOMESTIC,
            WireStatus.INITIATED, new BigDecimal("1000.00"), new BigDecimal("25.00"), "USD",
            "Account Holder", "123456789012", "021000021", "Digital Banking Platform",
            "John Doe", "123456789", "987654321", "Test Bank", null,
            null, null, null, null, "Payment for services", "Test memo", null,
            Instant.now(), Instant.now(), null, null
        );

        when(wireTransferRepository.save(any(WireTransfer.class))).thenReturn(savedWire);
        when(wireMapper.toResponse(any(WireTransfer.class))).thenReturn(expectedResponse);

        // Act
        WireTransferResponse result = wireService.initiateWire(request);

        // Assert
        assertNotNull(result);
        assertEquals(testAccountId, result.accountId());
        assertEquals(WireType.DOMESTIC, result.wireType());
        assertEquals(WireStatus.INITIATED, result.status());
        assertEquals(new BigDecimal("1000.00"), result.amount());
        assertEquals(new BigDecimal("25.00"), result.fee());

        verify(wireTransferRepository, times(1)).save(any(WireTransfer.class));
        verify(eventPublisher, times(1)).publishEvent(any(WireEvent.class));
    }

    @Test
    void testInitiateWire_InternationalFee() {
        // Arrange
        InitiateWireRequest request = new InitiateWireRequest(
            testAccountId,
            WireType.INTERNATIONAL,
            new BigDecimal("5000.00"),
            "USD",
            "Jane Smith",
            "123456789",
            null,
            "International Bank",
            "SWIFTCODE",
            null,
            null,
            null,
            "International transfer",
            null
        );

        WireTransfer savedWire = WireTransfer.builder()
            .id(testWireId)
            .accountId(testAccountId)
            .wireReferenceNumber("WIRE-XYZ98765-5678")
            .wireType(WireType.INTERNATIONAL)
            .status(WireStatus.INITIATED)
            .amount(new BigDecimal("5000.00"))
            .fee(new BigDecimal("45.00"))
            .currency("USD")
            .senderName("Account Holder")
            .senderAccountNumber("123456789012")
            .senderRoutingNumber("021000021")
            .senderBankName("Digital Banking Platform")
            .beneficiaryName("Jane Smith")
            .beneficiaryAccountNumber("123456789")
            .beneficiaryBankName("International Bank")
            .beneficiarySwiftCode("SWIFTCODE")
            .purposeOfWire("International transfer")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        WireTransferResponse expectedResponse = new WireTransferResponse(
            testWireId, testAccountId, "WIRE-XYZ98765-5678", null, WireType.INTERNATIONAL,
            WireStatus.INITIATED, new BigDecimal("5000.00"), new BigDecimal("45.00"), "USD",
            "Account Holder", "123456789012", "021000021", "Digital Banking Platform",
            "Jane Smith", "123456789", null, "International Bank", null,
            null, null, "SWIFTCODE", null, "International transfer", null, null,
            Instant.now(), Instant.now(), null, null
        );

        when(wireTransferRepository.save(any(WireTransfer.class))).thenReturn(savedWire);
        when(wireMapper.toResponse(any(WireTransfer.class))).thenReturn(expectedResponse);

        // Act
        WireTransferResponse result = wireService.initiateWire(request);

        // Assert
        assertNotNull(result);
        assertEquals(WireType.INTERNATIONAL, result.wireType());
        assertEquals(new BigDecimal("45.00"), result.fee());
    }

    @Test
    void testGetWire_Success() {
        // Arrange
        WireTransfer wire = WireTransfer.builder()
            .id(testWireId)
            .accountId(testAccountId)
            .wireReferenceNumber("WIRE-TEST123")
            .wireType(WireType.DOMESTIC)
            .status(WireStatus.COMPLETED)
            .amount(new BigDecimal("1000.00"))
            .fee(new BigDecimal("25.00"))
            .currency("USD")
            .createdAt(Instant.now())
            .build();

        WireTransferResponse expectedResponse = new WireTransferResponse(
            testWireId, testAccountId, "WIRE-TEST123", null, WireType.DOMESTIC,
            WireStatus.COMPLETED, new BigDecimal("1000.00"), new BigDecimal("25.00"), "USD",
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, Instant.now(), Instant.now(), null, null
        );

        when(wireTransferRepository.findById(testWireId)).thenReturn(Optional.of(wire));
        when(wireMapper.toResponse(wire)).thenReturn(expectedResponse);

        // Act
        WireTransferResponse result = wireService.getWire(testWireId);

        // Assert
        assertNotNull(result);
        assertEquals(testWireId, result.id());
        assertEquals(WireStatus.COMPLETED, result.status());
    }

    @Test
    void testGetWire_NotFound() {
        // Arrange
        when(wireTransferRepository.findById(testWireId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> wireService.getWire(testWireId));
    }

    @Test
    void testApproveWire_Success() {
        // Arrange
        WireTransfer wire = WireTransfer.builder()
            .id(testWireId)
            .accountId(testAccountId)
            .wireReferenceNumber("WIRE-TEST123")
            .wireType(WireType.DOMESTIC)
            .status(WireStatus.INITIATED)
            .amount(new BigDecimal("1000.00"))
            .fee(new BigDecimal("25.00"))
            .currency("USD")
            .createdAt(Instant.now())
            .build();

        WireTransfer approvedWire = WireTransfer.builder()
            .id(testWireId)
            .accountId(testAccountId)
            .wireReferenceNumber("WIRE-TEST123")
            .wireType(WireType.DOMESTIC)
            .status(WireStatus.APPROVED)
            .amount(new BigDecimal("1000.00"))
            .fee(new BigDecimal("25.00"))
            .currency("USD")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        WireTransferResponse expectedResponse = new WireTransferResponse(
            testWireId, testAccountId, "WIRE-TEST123", null, WireType.DOMESTIC,
            WireStatus.APPROVED, new BigDecimal("1000.00"), new BigDecimal("25.00"), "USD",
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, Instant.now(), Instant.now(), null, null
        );

        when(wireTransferRepository.findById(testWireId)).thenReturn(Optional.of(wire));
        when(wireTransferRepository.save(any(WireTransfer.class))).thenReturn(approvedWire);
        when(wireMapper.toResponse(approvedWire)).thenReturn(expectedResponse);

        // Act
        WireTransferResponse result = wireService.approveWire(testWireId);

        // Assert
        assertNotNull(result);
        assertEquals(WireStatus.APPROVED, result.status());
    }

    @Test
    void testApproveWire_InvalidStatus() {
        // Arrange
        WireTransfer wire = WireTransfer.builder()
            .id(testWireId)
            .accountId(testAccountId)
            .status(WireStatus.COMPLETED)
            .build();

        when(wireTransferRepository.findById(testWireId)).thenReturn(Optional.of(wire));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> wireService.approveWire(testWireId));
    }

    @Test
    void testCancelWire_Success() {
        // Arrange
        WireTransfer wire = WireTransfer.builder()
            .id(testWireId)
            .accountId(testAccountId)
            .wireReferenceNumber("WIRE-TEST123")
            .status(WireStatus.INITIATED)
            .build();

        WireTransfer cancelledWire = WireTransfer.builder()
            .id(testWireId)
            .accountId(testAccountId)
            .wireReferenceNumber("WIRE-TEST123")
            .status(WireStatus.CANCELLED)
            .build();

        WireTransferResponse expectedResponse = new WireTransferResponse(
            testWireId, testAccountId, "WIRE-TEST123", null, WireType.DOMESTIC,
            WireStatus.CANCELLED, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null
        );

        when(wireTransferRepository.findById(testWireId)).thenReturn(Optional.of(wire));
        when(wireTransferRepository.save(any(WireTransfer.class))).thenReturn(cancelledWire);
        when(wireMapper.toResponse(cancelledWire)).thenReturn(expectedResponse);

        // Act
        WireTransferResponse result = wireService.cancelWire(testWireId);

        // Assert
        assertNotNull(result);
        assertEquals(WireStatus.CANCELLED, result.status());
    }

    @Test
    void testCompleteWire_Success() {
        // Arrange
        WireTransfer wire = WireTransfer.builder()
            .id(testWireId)
            .accountId(testAccountId)
            .status(WireStatus.SUBMITTED)
            .build();

        WireTransfer completedWire = WireTransfer.builder()
            .id(testWireId)
            .accountId(testAccountId)
            .status(WireStatus.COMPLETED)
            .fedReferenceNumber("FED-123456")
            .completedAt(Instant.now())
            .build();

        WireTransferResponse expectedResponse = new WireTransferResponse(
            testWireId, testAccountId, null, "FED-123456", WireType.DOMESTIC,
            WireStatus.COMPLETED, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, Instant.now()
        );

        when(wireTransferRepository.findById(testWireId)).thenReturn(Optional.of(wire));
        when(wireTransferRepository.save(any(WireTransfer.class))).thenReturn(completedWire);
        when(wireMapper.toResponse(completedWire)).thenReturn(expectedResponse);

        // Act
        WireTransferResponse result = wireService.completeWire(testWireId, "FED-123456");

        // Assert
        assertNotNull(result);
        assertEquals(WireStatus.COMPLETED, result.status());
        assertEquals("FED-123456", result.fedReferenceNumber());
    }

    @Test
    void testFailWire_Success() {
        // Arrange
        WireTransfer wire = WireTransfer.builder()
            .id(testWireId)
            .accountId(testAccountId)
            .status(WireStatus.SUBMITTED)
            .build();

        WireTransfer failedWire = WireTransfer.builder()
            .id(testWireId)
            .accountId(testAccountId)
            .status(WireStatus.FAILED)
            .failureReason("Insufficient funds")
            .build();

        WireTransferResponse expectedResponse = new WireTransferResponse(
            testWireId, testAccountId, null, null, WireType.DOMESTIC,
            WireStatus.FAILED, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, "Insufficient funds", null, null, null, null
        );

        when(wireTransferRepository.findById(testWireId)).thenReturn(Optional.of(wire));
        when(wireTransferRepository.save(any(WireTransfer.class))).thenReturn(failedWire);
        when(wireMapper.toResponse(failedWire)).thenReturn(expectedResponse);

        // Act
        WireTransferResponse result = wireService.failWire(testWireId, "Insufficient funds");

        // Assert
        assertNotNull(result);
        assertEquals(WireStatus.FAILED, result.status());
        assertEquals("Insufficient funds", result.failureReason());
    }
}
