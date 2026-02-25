package com.banking.platform.debitnetwork.mapper;

import com.banking.platform.debitnetwork.model.dto.*;
import com.banking.platform.debitnetwork.model.entity.*;
import org.springframework.stereotype.Component;

@Component
public class DebitNetworkMapper {

    public DebitCardResponse toDebitCardResponse(DebitCard card) {
        if (card == null) return null;
        return new DebitCardResponse(
                card.getId(), card.getAccountId(), card.getCustomerId(),
                card.getCardNumberMasked(), card.getCardHolderName(),
                card.getExpiryDate(), card.getStatus(),
                card.getDailyLimit(), card.getMonthlyLimit(),
                card.getDailyUsed(), card.getMonthlyUsed(), card.getCreatedAt()
        );
    }

    public DebitTransactionResponse toDebitTransactionResponse(DebitTransaction tx) {
        if (tx == null) return null;
        return new DebitTransactionResponse(
                tx.getId(), tx.getDebitCardId(), tx.getAccountId(),
                tx.getAuthorizationCode(), tx.getNetworkReferenceId(),
                tx.getMerchantName(), tx.getMerchantCategoryCode(),
                tx.getAmount(), tx.getCurrency(), tx.getTransactionType(),
                tx.getStatus(), tx.getDeclineReason(),
                tx.getAuthorizedAt(), tx.getSettledAt()
        );
    }

    public AuthorizationResponse toAuthorizationResponse(DebitTransaction tx) {
        if (tx == null) return null;
        return new AuthorizationResponse(
                tx.getId(), tx.getAuthorizationCode(), tx.getStatus(),
                tx.getAmount(), tx.getMerchantName(),
                tx.getDeclineReason(), tx.getAuthorizedAt()
        );
    }

    public SettlementResponse toSettlementResponse(NetworkSettlement settlement) {
        if (settlement == null) return null;
        return new SettlementResponse(
                settlement.getId(), settlement.getSettlementDate(),
                settlement.getTotalAmount(), settlement.getTransactionCount(),
                settlement.getStatus(), settlement.getBatchId(), settlement.getCreatedAt()
        );
    }
}

