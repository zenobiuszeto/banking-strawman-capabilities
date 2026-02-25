package com.banking.platform.ach.service;

import com.banking.platform.ach.mapper.AchMapper;
import com.banking.platform.ach.model.dto.AchReturnRequest;
import com.banking.platform.ach.model.dto.AchSearchRequest;
import com.banking.platform.ach.model.dto.AchTransferResponse;
import com.banking.platform.ach.model.dto.InitiateAchRequest;
import com.banking.platform.ach.model.entity.AchStatus;
import com.banking.platform.ach.model.entity.AchTransfer;
import com.banking.platform.ach.model.entity.AchType;
import com.banking.platform.ach.model.event.AchEvent;
import com.banking.platform.ach.repository.AchTransferRepository;
import com.banking.platform.shared.exception.InvalidOperationException;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import com.banking.platform.shared.util.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing ACH transfer operations.
 * Handles initiation, processing, cancellation, and return processing of ACH transfers.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AchService {

    private static final BigDecimal STANDARD_LIMIT = new BigDecimal("25000");
    private static final BigDecimal SAME_DAY_LIMIT = new BigDecimal("100000");
    private static final String KAFKA_TOPIC = "ach-events";

    private final AchTransferRepository achTransferRepository;
    private final AchMapper achMapper;
    private final KafkaTemplate<String, AchEvent> kafkaTemplate;

    /**
     * Initiate a new ACH transfer.
     * Validates amount limits based on ACH type, generates trace number, and publishes event.
     *
     * @param request the initiate request containing transfer details
     * @return the response DTO with created transfer details
     * @throws InvalidOperationException if amount exceeds limits or validation fails
     */
    @Transactional
    public AchTransferResponse initiateTransfer(InitiateAchRequest request) {
        log.info("Initiating ACH transfer for account: {} with amount: {}", request.accountId(), request.amount());

        // Validate amount limits based on ACH type
        validateAmountLimit(request.amount(), request.achType());

        // Generate unique trace number
        String traceNumber = generateTraceNumber();

        // Create entity with placeholder bank details (would be fetched from LinkedBank service in production)
        AchTransfer transfer = achMapper.toEntity(
            request,
            traceNumber,
            "Sender Name",           // Would fetch from linked bank
            "000000000",             // Would fetch from linked bank
            "00000000000000",        // Would fetch from linked bank
            "Receiver Name",         // Would fetch from receiver bank
            "000000001",             // Would fetch from receiver bank
            "00000000000001",        // Would fetch from receiver bank
            "Banking Platform Inc",  // Company name
            "1234567890",            // Company ID
            "ACH"                    // Entry description
        );

        // Save transfer
        AchTransfer savedTransfer = achTransferRepository.save(transfer);
        log.info("ACH transfer initiated with trace number: {}", traceNumber);

        // Publish event
        publishEvent(savedTransfer, "ach.initiated");

        return achMapper.toResponse(savedTransfer);
    }

    /**
     * Get an ACH transfer by its ID.
     *
     * @param id the transfer ID
     * @return the transfer response
     * @throws ResourceNotFoundException if transfer not found
     */
    @Transactional(readOnly = true)
    public AchTransferResponse getTransfer(UUID id) {
        log.debug("Fetching ACH transfer with ID: {}", id);
        AchTransfer transfer = achTransferRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ACH transfer not found with ID: " + id));
        return achMapper.toResponse(transfer);
    }

    /**
     * Get an ACH transfer by its trace number.
     *
     * @param traceNumber the trace number
     * @return the transfer response
     * @throws ResourceNotFoundException if transfer not found
     */
    @Transactional(readOnly = true)
    public AchTransferResponse getByTraceNumber(String traceNumber) {
        log.debug("Fetching ACH transfer with trace number: {}", traceNumber);
        AchTransfer transfer = achTransferRepository.findByTraceNumber(traceNumber)
            .orElseThrow(() -> new ResourceNotFoundException("ACH transfer not found with trace number: " + traceNumber));
        return achMapper.toResponse(transfer);
    }

    /**
     * List ACH transfers for an account with pagination.
     *
     * @param accountId the account ID
     * @param page zero-indexed page number
     * @param size page size
     * @return paged response with transfers
     */
    @Transactional(readOnly = true)
    public PagedResponse<AchTransferResponse> listTransfers(UUID accountId, int page, int size) {
        log.debug("Listing ACH transfers for account: {} with page: {}, size: {}", accountId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<AchTransfer> transfers = achTransferRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);

        List<AchTransferResponse> responses = transfers.getContent()
            .stream()
            .map(achMapper::toResponse)
            .collect(Collectors.toList());

        return new PagedResponse<>(
            responses,
            transfers.getNumber(),
            transfers.getSize(),
            transfers.getTotalElements(),
            transfers.getTotalPages()
        );
    }

    /**
     * Search ACH transfers based on various filter criteria.
     *
     * @param searchRequest the search criteria
     * @param page zero-indexed page number
     * @param size page size
     * @return paged response with matching transfers
     */
    @Transactional(readOnly = true)
    public PagedResponse<AchTransferResponse> searchTransfers(AchSearchRequest searchRequest, int page, int size) {
        log.debug("Searching ACH transfers with criteria: {}", searchRequest);

        List<AchTransfer> allResults = new ArrayList<>();

        if (searchRequest.accountId() != null) {
            // Basic search by account ID - in production, would use more sophisticated search with all filters
            List<AchTransfer> results = new ArrayList<>();

            if (searchRequest.status() != null) {
                results = achTransferRepository.findByAccountIdAndStatus(searchRequest.accountId(), searchRequest.status());
            } else {
                Pageable tempPageable = PageRequest.of(0, Integer.MAX_VALUE);
                Page<AchTransfer> pageResults = achTransferRepository.findByAccountIdOrderByCreatedAtDesc(
                    searchRequest.accountId(),
                    tempPageable
                );
                results = pageResults.getContent();
            }

            // Apply date filters if provided
            if (searchRequest.fromDate() != null || searchRequest.toDate() != null) {
                results = results.stream()
                    .filter(t -> {
                        if (searchRequest.fromDate() != null) {
                            Instant fromInstant = searchRequest.fromDate()
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant();
                            if (t.getCreatedAt().isBefore(fromInstant)) {
                                return false;
                            }
                        }
                        if (searchRequest.toDate() != null) {
                            Instant toInstant = searchRequest.toDate()
                                .plusDays(1)
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant();
                            if (t.getCreatedAt().isAfter(toInstant)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
            }

            // Apply direction filter if provided
            if (searchRequest.direction() != null) {
                results = results.stream()
                    .filter(t -> t.getDirection() == searchRequest.direction())
                    .collect(Collectors.toList());
            }

            allResults = results;
        }

        // Paginate results
        Pageable pageable = PageRequest.of(page, size);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + size, allResults.size());

        List<AchTransferResponse> pageContent = allResults.subList(start, end)
            .stream()
            .map(achMapper::toResponse)
            .collect(Collectors.toList());

        return new PagedResponse<>(
            pageContent,
            page,
            size,
            allResults.size(),
            (allResults.size() + size - 1) / size
        );
    }

    /**
     * Cancel an ACH transfer.
     * Only transfers in INITIATED or PENDING_APPROVAL status can be cancelled.
     *
     * @param id the transfer ID
     * @return the updated transfer response
     * @throws ResourceNotFoundException if transfer not found
     * @throws InvalidOperationException if transfer cannot be cancelled
     */
    @Transactional
    public AchTransferResponse cancelTransfer(UUID id) {
        log.info("Cancelling ACH transfer with ID: {}", id);

        AchTransfer transfer = achTransferRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ACH transfer not found with ID: " + id));

        if (transfer.getStatus() != AchStatus.INITIATED && transfer.getStatus() != AchStatus.PENDING_APPROVAL) {
            throw new InvalidOperationException(
                "Cannot cancel transfer with status: " + transfer.getStatus() +
                ". Only INITIATED or PENDING_APPROVAL transfers can be cancelled."
            );
        }

        transfer.setStatus(AchStatus.CANCELLED);
        transfer.setUpdatedAt(Instant.now());
        AchTransfer updated = achTransferRepository.save(transfer);

        log.info("ACH transfer cancelled with ID: {}", id);
        publishEvent(updated, "ach.cancelled");

        return achMapper.toResponse(updated);
    }

    /**
     * Process a return for an ACH transfer.
     * Updates the transfer status to RETURNED and records the return reason.
     *
     * @param request the return request with reason code
     * @return the updated transfer response
     * @throws ResourceNotFoundException if transfer not found
     */
    @Transactional
    public AchTransferResponse processReturn(AchReturnRequest request) {
        log.info("Processing return for ACH transfer with trace number: {}", request.traceNumber());

        AchTransfer transfer = achTransferRepository.findByTraceNumber(request.traceNumber())
            .orElseThrow(() -> new ResourceNotFoundException(
                "ACH transfer not found with trace number: " + request.traceNumber()
            ));

        transfer.setStatus(AchStatus.RETURNED);
        transfer.setReturnReasonCode(request.returnReasonCode());
        transfer.setReturnDescription(request.returnDescription());
        transfer.setUpdatedAt(Instant.now());

        AchTransfer updated = achTransferRepository.save(transfer);
        log.info("ACH transfer return processed for trace number: {}", request.traceNumber());

        publishEvent(updated, "ach.returned");

        return achMapper.toResponse(updated);
    }

    /**
     * Approve an ACH transfer.
     * Transitions transfer from PENDING_APPROVAL to APPROVED status.
     *
     * @param id the transfer ID
     * @return the updated transfer response
     * @throws ResourceNotFoundException if transfer not found
     * @throws InvalidOperationException if transfer is not in PENDING_APPROVAL status
     */
    @Transactional
    public AchTransferResponse approveTransfer(UUID id) {
        log.info("Approving ACH transfer with ID: {}", id);

        AchTransfer transfer = achTransferRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ACH transfer not found with ID: " + id));

        if (transfer.getStatus() != AchStatus.INITIATED && transfer.getStatus() != AchStatus.PENDING_APPROVAL) {
            throw new InvalidOperationException(
                "Cannot approve transfer with status: " + transfer.getStatus() +
                ". Only INITIATED or PENDING_APPROVAL transfers can be approved."
            );
        }

        transfer.setStatus(AchStatus.APPROVED);
        transfer.setUpdatedAt(Instant.now());
        AchTransfer updated = achTransferRepository.save(transfer);

        log.info("ACH transfer approved with ID: {}", id);
        publishEvent(updated, "ach.approved");

        return achMapper.toResponse(updated);
    }

    /**
     * Submit an approved ACH transfer to the Federal Reserve.
     * Transitions status from APPROVED to SUBMITTED_TO_FED.
     *
     * @param id the transfer ID
     * @return the updated transfer response
     * @throws ResourceNotFoundException if transfer not found
     * @throws InvalidOperationException if transfer is not in APPROVED status
     */
    @Transactional
    public AchTransferResponse submitToFed(UUID id) {
        log.info("Submitting ACH transfer to Federal Reserve with ID: {}", id);

        AchTransfer transfer = achTransferRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ACH transfer not found with ID: " + id));

        if (transfer.getStatus() != AchStatus.APPROVED) {
            throw new InvalidOperationException(
                "Cannot submit transfer with status: " + transfer.getStatus() +
                ". Only APPROVED transfers can be submitted."
            );
        }

        transfer.setStatus(AchStatus.SUBMITTED_TO_FED);
        transfer.setSubmittedAt(Instant.now());
        transfer.setUpdatedAt(Instant.now());
        AchTransfer updated = achTransferRepository.save(transfer);

        log.info("ACH transfer submitted to Federal Reserve with ID: {}", id);
        publishEvent(updated, "ach.submitted");

        return achMapper.toResponse(updated);
    }

    /**
     * Mark an ACH transfer as settled.
     * Transitions status from PROCESSING to SETTLED.
     *
     * @param id the transfer ID
     * @return the updated transfer response
     * @throws ResourceNotFoundException if transfer not found
     */
    @Transactional
    public AchTransferResponse settleTransfer(UUID id) {
        log.info("Settling ACH transfer with ID: {}", id);

        AchTransfer transfer = achTransferRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ACH transfer not found with ID: " + id));

        transfer.setStatus(AchStatus.SETTLED);
        transfer.setSettledAt(Instant.now());
        transfer.setUpdatedAt(Instant.now());
        AchTransfer updated = achTransferRepository.save(transfer);

        log.info("ACH transfer settled with ID: {}", id);
        publishEvent(updated, "ach.settled");

        return achMapper.toResponse(updated);
    }

    /**
     * Validate that the transfer amount does not exceed the limits for the specified ACH type.
     *
     * @param amount the transfer amount
     * @param achType the ACH type
     * @throws InvalidOperationException if amount exceeds the limit
     */
    private void validateAmountLimit(BigDecimal amount, AchType achType) {
        BigDecimal limit = achType == AchType.SAME_DAY ? SAME_DAY_LIMIT : STANDARD_LIMIT;

        if (amount.compareTo(limit) > 0) {
            throw new InvalidOperationException(
                "Transfer amount exceeds limit for " + achType + ". Maximum: $" + limit + ", Requested: $" + amount
            );
        }
    }

    /**
     * Generate a unique ACH trace number.
     * Format: 9 digits ODFI + 6 digits sequence (in production, this would follow NACHA standards)
     *
     * @return a unique trace number
     */
    private String generateTraceNumber() {
        String odfiId = "000000001"; // In production, would use real ODFI ID
        String sequence = String.format("%06d", System.nanoTime() % 1000000);
        return odfiId + sequence;
    }

    /**
     * Publish an ACH event to Kafka for event-driven processing.
     *
     * @param transfer the ACH transfer
     * @param eventType the type of event
     */
    private void publishEvent(AchTransfer transfer, String eventType) {
        try {
            AchEvent event = AchEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .timestamp(Instant.now())
                .achTransferId(transfer.getId())
                .accountId(transfer.getAccountId())
                .direction(transfer.getDirection())
                .amount(transfer.getAmount())
                .status(transfer.getStatus())
                .build();

            kafkaTemplate.send(KAFKA_TOPIC, transfer.getId().toString(), event);
            log.debug("Published ACH event: {} for transfer: {}", eventType, transfer.getId());
        } catch (Exception e) {
            log.error("Failed to publish ACH event: {} for transfer: {}", eventType, transfer.getId(), e);
            // Don't rethrow - event publishing failure shouldn't block the operation
        }
    }
}
