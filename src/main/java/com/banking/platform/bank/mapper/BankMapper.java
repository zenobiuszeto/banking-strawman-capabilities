package com.banking.platform.bank.mapper;

import com.banking.platform.bank.model.dto.BankDirectoryResponse;
import com.banking.platform.bank.model.dto.LinkedBankResponse;
import com.banking.platform.bank.model.entity.BankDirectory;
import com.banking.platform.bank.model.entity.LinkedBank;
import org.springframework.stereotype.Component;

@Component
public class BankMapper {

    public LinkedBankResponse toLinkedBankResponse(LinkedBank linkedBank) {
        return new LinkedBankResponse(
                linkedBank.getId(),
                linkedBank.getBankName(),
                maskAccountNumber(linkedBank.getAccountNumber()),
                linkedBank.getAccountType(),
                linkedBank.getLinkStatus(),
                linkedBank.getNickname(),
                linkedBank.isPrimary(),
                linkedBank.getVerifiedAt()
        );
    }

    public BankDirectoryResponse toBankDirectoryResponse(BankDirectory bankDirectory) {
        return new BankDirectoryResponse(
                bankDirectory.getRoutingNumber(),
                bankDirectory.getBankName(),
                bankDirectory.getCity(),
                bankDirectory.getState(),
                bankDirectory.isActive()
        );
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
