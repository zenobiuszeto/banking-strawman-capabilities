package com.banking.platform.wire.mapper;

import com.banking.platform.wire.model.dto.WireTransferResponse;
import com.banking.platform.wire.model.entity.WireTransfer;
import org.springframework.stereotype.Component;

@Component
public class WireMapper {

    public WireTransferResponse toResponse(WireTransfer wireTransfer) {
        if (wireTransfer == null) {
            return null;
        }

        return new WireTransferResponse(
            wireTransfer.getId(),
            wireTransfer.getAccountId(),
            wireTransfer.getWireReferenceNumber(),
            wireTransfer.getFedReferenceNumber(),
            wireTransfer.getWireType(),
            wireTransfer.getStatus(),
            wireTransfer.getAmount(),
            wireTransfer.getFee(),
            wireTransfer.getCurrency(),
            wireTransfer.getSenderName(),
            wireTransfer.getSenderAccountNumber(),
            wireTransfer.getSenderRoutingNumber(),
            wireTransfer.getSenderBankName(),
            wireTransfer.getBeneficiaryName(),
            wireTransfer.getBeneficiaryAccountNumber(),
            wireTransfer.getBeneficiaryRoutingNumber(),
            wireTransfer.getBeneficiaryBankName(),
            wireTransfer.getBeneficiaryBankAddress(),
            wireTransfer.getIntermediaryBankName(),
            wireTransfer.getIntermediarySwiftCode(),
            wireTransfer.getBeneficiarySwiftCode(),
            wireTransfer.getBeneficiaryIban(),
            wireTransfer.getPurposeOfWire(),
            wireTransfer.getMemo(),
            wireTransfer.getFailureReason(),
            wireTransfer.getCreatedAt(),
            wireTransfer.getUpdatedAt(),
            wireTransfer.getSubmittedAt(),
            wireTransfer.getCompletedAt()
        );
    }
}
