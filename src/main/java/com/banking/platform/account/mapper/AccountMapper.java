package com.banking.platform.account.mapper;

import com.banking.platform.account.model.dto.*;
import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.model.entity.InterestCharge;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        if (account == null) {
            return null;
        }

        return new AccountResponse(
            account.getId(),
            account.getAccountNumber(),
            account.getAccountType(),
            account.getStatus(),
            account.getCurrentBalance(),
            account.getAvailableBalance(),
            account.getPendingBalance(),
            account.getHoldAmount(),
            account.getCurrency(),
            account.getOpenedDate()
        );
    }

    public AccountSummaryResponse toSummary(Account account) {
        if (account == null) {
            return null;
        }

        return new AccountSummaryResponse(
            account.getId(),
            account.getAccountNumber(),
            account.getAccountType(),
            account.getStatus(),
            account.getCurrentBalance(),
            account.getAvailableBalance(),
            account.getPendingBalance(),
            BigDecimal.ZERO
        );
    }

    public BalanceDetailResponse toBalanceDetail(Account account) {
        if (account == null) {
            return null;
        }

        BigDecimal overdraftUsed = BigDecimal.ZERO;
        if (account.getOverdraftLimit() != null && account.getCurrentBalance().compareTo(BigDecimal.ZERO) < 0) {
            overdraftUsed = account.getCurrentBalance().negate();
        }

        return new BalanceDetailResponse(
            account.getId(),
            account.getCurrentBalance(),
            account.getAvailableBalance(),
            account.getPendingBalance(),
            account.getHoldAmount(),
            account.getCurrentBalance(),
            account.getAccruedInterest() != null ? account.getAccruedInterest() : BigDecimal.ZERO,
            account.getOverdraftLimit() != null ? account.getOverdraftLimit() : BigDecimal.ZERO,
            overdraftUsed,
            account.getCurrency()
        );
    }

    public InterestChargeResponse toInterestChargeResponse(InterestCharge charge) {
        if (charge == null) {
            return null;
        }

        return new InterestChargeResponse(
            charge.getId(),
            charge.getChargeType(),
            charge.getAmount(),
            charge.getRunningBalance(),
            charge.getDescription(),
            charge.getPostDate(),
            charge.getEffectiveDate()
        );
    }
}
