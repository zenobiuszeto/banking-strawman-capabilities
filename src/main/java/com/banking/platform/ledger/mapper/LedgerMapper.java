package com.banking.platform.ledger.mapper;

import com.banking.platform.ledger.model.dto.*;
import com.banking.platform.ledger.model.entity.*;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class LedgerMapper {

    public GlAccountResponse toGlAccountResponse(GlAccount account) {
        if (account == null) return null;

        return new GlAccountResponse(
                account.getId(),
                account.getAccountCode(),
                account.getName(),
                account.getDescription(),
                account.getAccountType(),
                account.getNormalBalance(),
                account.getParentId(),
                account.isActive(),
                account.getCreatedAt()
        );
    }

    public JournalEntryResponse toJournalEntryResponse(JournalEntry entry) {
        if (entry == null) return null;

        return new JournalEntryResponse(
                entry.getId(),
                entry.getEntryNumber(),
                entry.getDescription(),
                entry.getPostingDate(),
                entry.getStatus(),
                entry.getReferenceId(),
                entry.getReferenceType(),
                entry.getCreatedBy(),
                entry.getReversalOfId(),
                entry.getLines().stream()
                        .map(this::toJournalEntryLineResponse)
                        .collect(Collectors.toList()),
                entry.getCreatedAt()
        );
    }

    public JournalEntryLineResponse toJournalEntryLineResponse(JournalEntryLine line) {
        if (line == null) return null;

        return new JournalEntryLineResponse(
                line.getId(),
                line.getGlAccountId(),
                line.getAccountCode(),
                line.getDebitAmount(),
                line.getCreditAmount(),
                line.getDescription()
        );
    }

    public PostingRuleResponse toPostingRuleResponse(PostingRule rule) {
        if (rule == null) return null;

        return new PostingRuleResponse(
                rule.getId(),
                rule.getRuleCode(),
                rule.getName(),
                rule.getDescription(),
                rule.getTriggerEvent(),
                rule.getDebitAccountCode(),
                rule.getCreditAccountCode(),
                rule.isActive(),
                rule.getCreatedAt()
        );
    }
}

