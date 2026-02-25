package com.banking.platform.rewards.mapper;

import com.banking.platform.rewards.model.dto.RewardsAccountResponse;
import com.banking.platform.rewards.model.dto.RewardsOfferResponse;
import com.banking.platform.rewards.model.dto.RewardsTransactionResponse;
import com.banking.platform.rewards.model.entity.RewardsAccount;
import com.banking.platform.rewards.model.entity.RewardsOffer;
import com.banking.platform.rewards.model.entity.RewardsTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class RewardsMapper {

    public RewardsAccountResponse toAccountResponse(RewardsAccount account) {
        if (account == null) {
            return null;
        }

        BigDecimal estimatedCashValue = BigDecimal.valueOf(account.getCurrentBalance())
                .multiply(BigDecimal.valueOf(0.01));

        return new RewardsAccountResponse(
                account.getId(),
                account.getCustomerId(),
                account.getTier(),
                account.getTier().getDisplayName(),
                account.getCurrentBalance(),
                account.getTotalPointsEarned(),
                account.getTotalPointsRedeemed(),
                estimatedCashValue,
                account.getTierExpiryDate()
        );
    }

    public RewardsTransactionResponse toTransactionResponse(RewardsTransaction transaction) {
        if (transaction == null) {
            return null;
        }

        return new RewardsTransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getPoints(),
                transaction.getRunningBalance(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }

    public RewardsOfferResponse toOfferResponse(RewardsOffer offer) {
        if (offer == null) {
            return null;
        }

        return new RewardsOfferResponse(
                offer.getId(),
                offer.getOfferCode(),
                offer.getTitle(),
                offer.getDescription(),
                offer.getType(),
                offer.getBonusPoints(),
                offer.getMultiplier(),
                offer.getStartDate(),
                offer.getEndDate(),
                offer.getMinimumTier()
        );
    }
}
