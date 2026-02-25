package com.banking.platform.ach.mapper;

import com.banking.platform.ach.model.dto.AchTransferResponse;
import com.banking.platform.ach.model.dto.InitiateAchRequest;
import com.banking.platform.ach.model.entity.AchStatus;
import com.banking.platform.ach.model.entity.AchTransfer;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Mapper for converting between ACH entities and DTOs.
 */
@Component
public class AchMapper {

    /**
     * Convert an AchTransfer entity to an AchTransferResponse DTO.
     *
     * @param transfer the ACH transfer entity
     * @return the response DTO
     */
    public AchTransferResponse toResponse(AchTransfer transfer) {
        if (transfer == null) {
            return null;
        }

        return new AchTransferResponse(
            transfer.getId(),
            transfer.getTraceNumber(),
            transfer.getDirection(),
            transfer.getAchType(),
            transfer.getStatus(),
            transfer.getAmount(),
            transfer.getSenderName(),
            transfer.getReceiverName(),
            transfer.getEffectiveDate(),
            transfer.getSettlementDate(),
            transfer.getReturnReasonCode(),
            transfer.getCreatedAt()
        );
    }

    /**
     * Convert an InitiateAchRequest DTO to an AchTransfer entity.
     * Note: This creates a partially initialized entity that requires bank details lookup.
     *
     * @param request the initiate request DTO
     * @param traceNumber the generated trace number
     * @param senderName the sender name
     * @param senderRoutingNumber the sender routing number
     * @param senderAccountNumber the sender account number
     * @param receiverName the receiver name
     * @param receiverRoutingNumber the receiver routing number
     * @param receiverAccountNumber the receiver account number
     * @param companyName the company name
     * @param companyId the company ID
     * @param entryDescription the entry description
     * @return the AchTransfer entity
     */
    public AchTransfer toEntity(
        InitiateAchRequest request,
        String traceNumber,
        String senderName,
        String senderRoutingNumber,
        String senderAccountNumber,
        String receiverName,
        String receiverRoutingNumber,
        String receiverAccountNumber,
        String companyName,
        String companyId,
        String entryDescription
    ) {
        if (request == null) {
            return null;
        }

        Instant now = Instant.now();

        return AchTransfer.builder()
            .accountId(request.accountId())
            .linkedBankId(request.linkedBankId())
            .traceNumber(traceNumber)
            .direction(request.direction())
            .achType(request.achType())
            .status(AchStatus.INITIATED)
            .secCode(request.secCode())
            .amount(request.amount())
            .senderName(senderName)
            .senderRoutingNumber(senderRoutingNumber)
            .senderAccountNumber(senderAccountNumber)
            .receiverName(receiverName)
            .receiverRoutingNumber(receiverRoutingNumber)
            .receiverAccountNumber(receiverAccountNumber)
            .companyName(companyName)
            .companyId(companyId)
            .entryDescription(entryDescription)
            .memo(request.memo())
            .effectiveDate(request.effectiveDate() != null ? request.effectiveDate() : java.time.LocalDate.now())
            .createdAt(now)
            .updatedAt(now)
            .retryCount(0)
            .build();
    }
}
