package com.banking.platform.wire.service;

import com.banking.platform.shared.exception.ResourceNotFoundException;
import com.banking.platform.shared.util.PagedResponse;
import com.banking.platform.wire.mapper.WireMapper;
import com.banking.platform.wire.model.dto.InitiateWireRequest;
import com.banking.platform.wire.model.dto.WireTransferResponse;
import com.banking.platform.wire.model.entity.WireStatus;
import com.banking.platform.wire.model.entity.WireTransfer;
import com.banking.platform.wire.model.entity.WireType;
import com.banking.platform.wire.model.event.WireEvent;
import com.banking.platform.wire.repository.WireTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WireService {

    private final WireTransferRepository wireTransferRepository;
    private final WireMapper wireMapper;
    private final ApplicationEventPublisher eventPublisher;

    private static final BigDecimal DOMESTIC_FEE = new BigDecimal("25.00");
    private static final BigDecimal INTERNATIONAL_FEE = new BigDecimal("45.00");

    public WireTransferResponse initiateWire(InitiateWireRequest request) {
        log.info("Initiating wire transfer for account: {}", request.accountId());

        String wireReferenceNumber = generateWireReferenceNumber();
        BigDecimal fee = calculateFee(request.wireType());

        WireTransfer wireTransfer = WireTransfer.builder()
            .accountId(request.accountId())
            .wireReferenceNumber(wireReferenceNumber)
            .wireType(request.wireType())
            .status(WireStatus.INITIATED)
            .amount(request.amount())
            .fee(fee)
            .currency(request.currency())
            .senderName(resolveSenderName(request.accountId()))
            .senderAccountNumber(resolveSenderAccountNumber(request.accountId()))
            .senderRoutingNumber(resolveSenderRoutingNumber(request.accountId()))
            .senderBankName(resolveSenderBankName())
            .beneficiaryName(request.beneficiaryName())
            .beneficiaryAccountNumber(request.beneficiaryAccountNumber())
            .beneficiaryRoutingNumber(request.beneficiaryRoutingNumber())
            .beneficiaryBankName(request.beneficiaryBankName())
            .beneficiaryBankAddress(request.beneficiaryIban())
            .intermediaryBankName(request.intermediaryBankName())
            .intermediarySwiftCode(request.intermediarySwiftCode())
            .beneficiarySwiftCode(request.beneficiarySwiftCode())
            .beneficiaryIban(request.beneficiaryIban())
            .purposeOfWire(request.purposeOfWire())
            .memo(request.memo())
            .build();

        WireTransfer saved = wireTransferRepository.save(wireTransfer);
        log.info("Wire transfer initiated: {} for account: {}", wireReferenceNumber, request.accountId());

        publishWireEvent(saved, "wire.initiated");

        return wireMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public WireTransferResponse getWire(UUID wireId) {
        log.debug("Retrieving wire transfer: {}", wireId);
        WireTransfer wireTransfer = wireTransferRepository.findById(wireId)
            .orElseThrow(() -> new ResourceNotFoundException("Wire transfer not found: " + wireId));
        return wireMapper.toResponse(wireTransfer);
    }

    @Transactional(readOnly = true)
    public WireTransferResponse getByReference(String wireReferenceNumber) {
        log.debug("Retrieving wire transfer by reference: {}", wireReferenceNumber);
        WireTransfer wireTransfer = wireTransferRepository.findByWireReferenceNumber(wireReferenceNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Wire transfer not found: " + wireReferenceNumber));
        return wireMapper.toResponse(wireTransfer);
    }

    @Transactional(readOnly = true)
    public PagedResponse<WireTransferResponse> listWires(UUID accountId, int page, int size) {
        log.debug("Listing wire transfers for account: {}, page: {}, size: {}", accountId, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WireTransfer> wireTransfers = wireTransferRepository.findByAccountId(accountId, pageable);

        return new PagedResponse<>(
            wireTransfers.getContent().stream()
                .map(wireMapper::toResponse)
                .toList(),
            wireTransfers.getNumber(),
            wireTransfers.getSize(),
            wireTransfers.getTotalElements(),
            wireTransfers.getTotalPages()
        );
    }

    public WireTransferResponse approveWire(UUID wireId) {
        log.info("Approving wire transfer: {}", wireId);

        WireTransfer wireTransfer = wireTransferRepository.findById(wireId)
            .orElseThrow(() -> new ResourceNotFoundException("Wire transfer not found: " + wireId));

        if (!wireTransfer.getStatus().equals(WireStatus.INITIATED)) {
            throw new IllegalStateException("Cannot approve wire transfer in status: " + wireTransfer.getStatus());
        }

        wireTransfer.setStatus(WireStatus.APPROVED);
        wireTransfer.setUpdatedAt(Instant.now());
        WireTransfer saved = wireTransferRepository.save(wireTransfer);

        log.info("Wire transfer approved: {}", wireId);
        publishWireEvent(saved, "wire.approved");

        return wireMapper.toResponse(saved);
    }

    public WireTransferResponse cancelWire(UUID wireId) {
        log.info("Cancelling wire transfer: {}", wireId);

        WireTransfer wireTransfer = wireTransferRepository.findById(wireId)
            .orElseThrow(() -> new ResourceNotFoundException("Wire transfer not found: " + wireId));

        WireStatus currentStatus = wireTransfer.getStatus();
        if (currentStatus.equals(WireStatus.COMPLETED) || currentStatus.equals(WireStatus.FAILED) ||
            currentStatus.equals(WireStatus.CANCELLED) || currentStatus.equals(WireStatus.IN_TRANSIT)) {
            throw new IllegalStateException("Cannot cancel wire transfer in status: " + currentStatus);
        }

        wireTransfer.setStatus(WireStatus.CANCELLED);
        wireTransfer.setUpdatedAt(Instant.now());
        WireTransfer saved = wireTransferRepository.save(wireTransfer);

        log.info("Wire transfer cancelled: {}", wireId);
        publishWireEvent(saved, "wire.cancelled");

        return wireMapper.toResponse(saved);
    }

    public WireTransferResponse completeWire(UUID wireId, String fedReferenceNumber) {
        log.info("Completing wire transfer: {} with fed reference: {}", wireId, fedReferenceNumber);

        WireTransfer wireTransfer = wireTransferRepository.findById(wireId)
            .orElseThrow(() -> new ResourceNotFoundException("Wire transfer not found: " + wireId));

        if (!wireTransfer.getStatus().equals(WireStatus.SUBMITTED) &&
            !wireTransfer.getStatus().equals(WireStatus.IN_TRANSIT)) {
            throw new IllegalStateException("Cannot complete wire transfer in status: " + wireTransfer.getStatus());
        }

        wireTransfer.setStatus(WireStatus.COMPLETED);
        wireTransfer.setFedReferenceNumber(fedReferenceNumber);
        wireTransfer.setCompletedAt(Instant.now());
        wireTransfer.setUpdatedAt(Instant.now());
        WireTransfer saved = wireTransferRepository.save(wireTransfer);

        log.info("Wire transfer completed: {}", wireId);
        publishWireEvent(saved, "wire.completed");

        return wireMapper.toResponse(saved);
    }

    public WireTransferResponse failWire(UUID wireId, String failureReason) {
        log.info("Failing wire transfer: {} with reason: {}", wireId, failureReason);

        WireTransfer wireTransfer = wireTransferRepository.findById(wireId)
            .orElseThrow(() -> new ResourceNotFoundException("Wire transfer not found: " + wireId));

        wireTransfer.setStatus(WireStatus.FAILED);
        wireTransfer.setFailureReason(failureReason);
        wireTransfer.setUpdatedAt(Instant.now());
        WireTransfer saved = wireTransferRepository.save(wireTransfer);

        log.info("Wire transfer failed: {}", wireId);
        publishWireEvent(saved, "wire.failed");

        return wireMapper.toResponse(saved);
    }

    private String generateWireReferenceNumber() {
        return "WIRE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() +
               "-" + System.currentTimeMillis() % 10000;
    }

    private BigDecimal calculateFee(WireType wireType) {
        return wireType == WireType.DOMESTIC ? DOMESTIC_FEE : INTERNATIONAL_FEE;
    }

    private String resolveSenderName(UUID accountId) {
        // In a real system, this would fetch from the account service
        return "Account Holder";
    }

    private String resolveSenderAccountNumber(UUID accountId) {
        // In a real system, this would fetch from the account service
        return accountId.toString().substring(0, 12);
    }

    private String resolveSenderRoutingNumber(UUID accountId) {
        // In a real system, this would fetch from the account service
        return "021000021";
    }

    private String resolveSenderBankName() {
        // In a real system, this would fetch from the bank configuration
        return "Digital Banking Platform";
    }

    private void publishWireEvent(WireTransfer wireTransfer, String eventType) {
        WireEvent event = WireEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .timestamp(Instant.now())
            .wireTransferId(wireTransfer.getId())
            .accountId(wireTransfer.getAccountId())
            .wireType(wireTransfer.getWireType())
            .amount(wireTransfer.getAmount())
            .currency(wireTransfer.getCurrency())
            .status(wireTransfer.getStatus())
            .build();

        log.debug("Publishing wire event: {} for wire: {}", eventType, wireTransfer.getId());
        eventPublisher.publishEvent(event);
    }
}
