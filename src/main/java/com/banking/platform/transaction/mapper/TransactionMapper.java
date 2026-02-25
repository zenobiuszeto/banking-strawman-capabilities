package com.banking.platform.transaction.mapper;

import com.banking.platform.transaction.model.dto.CreateTransactionRequest;
import com.banking.platform.transaction.model.dto.TransactionResponse;
import com.banking.platform.transaction.model.entity.Transaction;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Mapper for converting between Transaction entities and DTOs.
 * Handles transformation of domain models to/from API contracts.
 */
@Component
public class TransactionMapper {

    /**
     * Convert a Transaction entity to a TransactionResponse DTO.
     *
     * @param transaction the transaction entity
     * @return a TransactionResponse DTO
     */
    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
            transaction.getId(),
            transaction.getReferenceNumber(),
            transaction.getType(),
            transaction.getCategory(),
            transaction.getStatus(),
            transaction.getAmount(),
            transaction.getRunningBalance(),
            transaction.getDescription(),
            transaction.getMerchantName(),
            transaction.getPostDate(),
            transaction.getEffectiveDate(),
            transaction.getCreatedAt()
        );
    }

    /**
     * Convert a CreateTransactionRequest DTO to a Transaction entity.
     * Sets initial values; callers must set additional fields like referenceNumber and status.
     *
     * @param request the create request DTO
     * @return a Transaction entity (not yet persisted)
     */
    public Transaction toEntity(CreateTransactionRequest request) {
        LocalDate today = LocalDate.now();

        return Transaction.builder()
            .id(UUID.randomUUID())
            .accountId(request.accountId())
            .relatedAccountId(request.relatedAccountId())
            .type(request.type())
            .category(request.category())
            .amount(request.amount())
            .description(request.description())
            .merchantName(request.merchantName())
            .merchantCategory(request.merchantCategory())
            .channel(request.channel())
            .postDate(today)
            .effectiveDate(today)
            .build();
    }
}
