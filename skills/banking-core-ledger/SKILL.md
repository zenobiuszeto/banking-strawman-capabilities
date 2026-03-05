---
name: banking-core-ledger
description: |
  **Core Ledger & Balance Management**: Master ledger entry journaling, balance calculations with holds and pending items, Regulation CC funds availability rules, account lifecycle management, and atomic balance updates with optimistic locking.

  MANDATORY TRIGGERS: ledger, balance, available balance, ledger balance, holds, hold, pending transactions, funds availability, Reg CC, RegCC, Regulation CC, cutoff time, memo posting, final posting, posting adapter, journal entry, account balance, Balance Service, BalanceService, HoldsService, FundsAvailabilityService, PostingAdapter, LedgerEntry, AccountBalance, available funds, pending holds, debit hold, credit hold, balance inquiry, overdraft, NSF, insufficient funds, account status, account lifecycle
---

# Banking Core Ledger & Balance Management

The core ledger system provides authoritative balance calculations, state machine-driven account lifecycle management, and Regulation CC-compliant funds availability. This skill covers designing atomic balance updates, managing holds and pending transactions, implementing cutoff times for wire/ACH, and building the posting adapter pattern for integration with downstream ledger systems.

---

## Balance Service Architecture

The `BalanceService` provides calculated balances with three components: ledger balance (sum of all posted transactions), available balance (ledger - holds - pending debits), and memo-posted transactions pending final posting.

```java
package com.banking.platform.ledger.service;

import com.banking.platform.ledger.dto.*;
import com.banking.platform.ledger.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BalanceService {
    private final LedgerEntryRepository ledgerRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final HoldsService holdsService;

    @Transactional(readOnly = true)
    public BalanceSnapshot getBalance(String accountId) {
        AccountBalance accountBalance = accountBalanceRepository.findByAccountId(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));

        BigDecimal postedBalance = calculatePostedBalance(accountId);
        List<Hold> activeHolds = holdsService.getActiveHolds(accountId);
        BigDecimal totalHolds = activeHolds.stream()
            .map(Hold::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingDebits = calculatePendingDebits(accountId);
        BigDecimal pendingCredits = calculatePendingCredits(accountId);

        BigDecimal availableBalance = postedBalance
            .subtract(totalHolds)
            .subtract(pendingDebits)
            .add(pendingCredits);

        return BalanceSnapshot.builder()
            .accountId(accountId)
            .ledgerBalance(postedBalance)
            .availableBalance(availableBalance)
            .holds(totalHolds)
            .pendingDebits(pendingDebits)
            .pendingCredits(pendingCredits)
            .asOfDateTime(LocalDateTime.now())
            .build();
    }

    private BigDecimal calculatePostedBalance(String accountId) {
        return ledgerRepository.sumByAccountIdAndPosted(accountId, true);
    }

    private BigDecimal calculatePendingDebits(String accountId) {
        return ledgerRepository.sumByAccountIdAndTransactionTypeAndPosted(
            accountId, TransactionType.DEBIT, false
        );
    }

    private BigDecimal calculatePendingCredits(String accountId) {
        return ledgerRepository.sumByAccountIdAndTransactionTypeAndPosted(
            accountId, TransactionType.CREDIT, false
        );
    }
}

public record BalanceSnapshot(
    String accountId,
    BigDecimal ledgerBalance,
    BigDecimal availableBalance,
    BigDecimal holds,
    BigDecimal pendingDebits,
    BigDecimal pendingCredits,
    LocalDateTime asOfDateTime
) {}
```

## LedgerEntry Entity with Optimistic Locking

```java
package com.banking.platform.ledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_account_posted", columnList = "account_id, posted"),
    @Index(name = "idx_posting_date", columnList = "posting_date"),
    @Index(name = "idx_value_date", columnList = "value_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String journalId;

    private String accountId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransactionType direction;  // DEBIT, CREDIT

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    private String transactionId;  // Reference to payment/transfer

    private String memo;
    
    private Boolean posted;  // false = memo posting, true = final

    private LocalDate valueDate;  // When funds become available
    private LocalDate postingDate;  // When entry was posted

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Version
    private Long version;  // Optimistic locking
}

public enum TransactionType {
    DEBIT, CREDIT, TRANSFER, WIRE, ACH, CHECK, INTEREST, FEE
}
```

## Holds Service

The `HoldsService` manages authorization holds, exception holds, and administrative holds with configurable TTLs.

```java
package com.banking.platform.ledger.service;

import com.banking.platform.ledger.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HoldsService {
    private final HoldRepository holdRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Hold placeHold(HoldRequest request) {
        Hold hold = Hold.builder()
            .accountId(request.getAccountId())
            .amount(request.getAmount())
            .holdType(request.getHoldType())
            .reason(request.getReason())
            .externalTransactionId(request.getExternalTransactionId())
            .placeDateTime(LocalDateTime.now())
            .expiresAt(calculateExpiryTime(request.getHoldType()))
            .status(HoldStatus.ACTIVE)
            .build();

        Hold saved = holdRepository.save(hold);
        eventPublisher.publishEvent(new HoldPlacedEvent(saved));
        return saved;
    }

    @Transactional
    public void releaseHold(String holdId) {
        Hold hold = holdRepository.findById(holdId)
            .orElseThrow(() -> new HoldNotFoundException(holdId));

        hold.setStatus(HoldStatus.RELEASED);
        hold.setReleasedAt(LocalDateTime.now());
        holdRepository.save(hold);

        eventPublisher.publishEvent(new HoldReleasedEvent(hold));
    }

    @Transactional
    public void expireOldHolds() {
        LocalDateTime cutoff = LocalDateTime.now();
        List<Hold> expiredHolds = holdRepository.findByStatusAndExpiresAtBefore(
            HoldStatus.ACTIVE, cutoff
        );

        expiredHolds.forEach(hold -> {
            hold.setStatus(HoldStatus.EXPIRED);
            hold.setReleasedAt(LocalDateTime.now());
            holdRepository.save(hold);
            eventPublisher.publishEvent(new HoldExpiredEvent(hold));
        });
    }

    public List<Hold> getActiveHolds(String accountId) {
        return holdRepository.findByAccountIdAndStatus(accountId, HoldStatus.ACTIVE);
    }

    private LocalDateTime calculateExpiryTime(HoldType holdType) {
        return switch (holdType) {
            case AUTH -> LocalDateTime.now().plus(7, ChronoUnit.DAYS);
            case EXCEPTION -> LocalDateTime.now().plus(30, ChronoUnit.DAYS);
            case ADMIN -> LocalDateTime.now().plus(90, ChronoUnit.DAYS);
        };
    }
}

@Entity
@Table(name = "holds")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hold {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String holdId;

    private String accountId;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private HoldType holdType;

    private String reason;
    private String externalTransactionId;

    private LocalDateTime placeDateTime;
    private LocalDateTime releasedAt;
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    private HoldStatus status;
}

public enum HoldType {
    AUTH, EXCEPTION, ADMIN
}

public enum HoldStatus {
    ACTIVE, RELEASED, EXPIRED
}
```

## Account Balance Aggregate with Atomic Updates

```java
package com.banking.platform.ledger.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_balances")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountBalance {
    @Id
    private String accountId;

    private BigDecimal ledgerBalance;  // Sum of all posted entries
    private BigDecimal availableBalance;
    private BigDecimal pendingDebits;
    private BigDecimal pendingCredits;
    private BigDecimal holds;

    private LocalDateTime lastUpdated;

    @Version
    private Long version;  // Optimistic locking for atomic updates
}
```

## Funds Availability Service (Regulation CC)

```java
package com.banking.platform.ledger.service;

import com.banking.platform.ledger.dto.*;
import com.banking.platform.ledger.entity.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DayOfWeek;

@Service
@RequiredArgsConstructor
public class FundsAvailabilityService {
    private final DepositTypeRepository depositTypeRepository;
    private final AccountRepository accountRepository;

    public FundsAvailabilityWindow calculateAvailability(
        DepositType depositType,
        BigDecimal amount,
        LocalDate depositDate,
        String accountId
    ) {
        boolean isNewAccount = isNewAccount(accountId);

        return switch (depositType) {
            case NEXT_DAY -> calculateNextDayAvailability(depositDate);
            case TWO_DAY -> calculateTwoDayAvailability(depositDate);
            case CASE_BY_CASE -> calculateCaseByCase(amount, isNewAccount);
            case LARGE_DEPOSIT_EXCEPTION -> {
                if (amount.compareTo(BigDecimal.valueOf(5500)) > 0) {
                    yield calculateCaseByCase(amount, isNewAccount);
                } else {
                    yield calculateNextDayAvailability(depositDate);
                }
            }
            case NEW_ACCOUNT_RULE -> {
                if (isNewAccount) {
                    yield calculateNewAccountAvailability(depositDate);
                } else {
                    yield calculateNextDayAvailability(depositDate);
                }
            }
        };
    }

    private FundsAvailabilityWindow calculateNextDayAvailability(LocalDate depositDate) {
        LocalDate availableDate = depositDate.plusDays(1);
        if (availableDate.getDayOfWeek() == DayOfWeek.SATURDAY) {
            availableDate = availableDate.plusDays(2);
        } else if (availableDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            availableDate = availableDate.plusDays(1);
        }

        return FundsAvailabilityWindow.builder()
            .depositDate(depositDate)
            .availableDate(availableDate)
            .regulationType("REG_CC_NEXT_DAY")
            .build();
    }

    private FundsAvailabilityWindow calculateTwoDayAvailability(LocalDate depositDate) {
        LocalDate availableDate = depositDate.plusDays(2);
        while (availableDate.getDayOfWeek() == DayOfWeek.SATURDAY || 
               availableDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            availableDate = availableDate.plusDays(1);
        }

        return FundsAvailabilityWindow.builder()
            .depositDate(depositDate)
            .availableDate(availableDate)
            .regulationType("REG_CC_TWO_DAY")
            .build();
    }

    private FundsAvailabilityWindow calculateCaseByCase(
        BigDecimal amount,
        boolean isNewAccount
    ) {
        int delayDays = isNewAccount ? 10 : 7;
        LocalDate availableDate = LocalDate.now().plusDays(delayDays);

        return FundsAvailabilityWindow.builder()
            .depositDate(LocalDate.now())
            .availableDate(availableDate)
            .regulationType("REG_CC_CASE_BY_CASE")
            .build();
    }

    private FundsAvailabilityWindow calculateNewAccountAvailability(LocalDate depositDate) {
        LocalDate availableDate = depositDate.plusDays(9);

        return FundsAvailabilityWindow.builder()
            .depositDate(depositDate)
            .availableDate(availableDate)
            .regulationType("REG_CC_NEW_ACCOUNT")
            .build();
    }

    private boolean isNewAccount(String accountId) {
        BankAccount account = accountRepository.findById(accountId)
            .orElseThrow();
        return account.getCreatedAt().toLocalDate()
            .isAfter(LocalDate.now().minusDays(30));
    }
}

public record FundsAvailabilityWindow(
    LocalDate depositDate,
    LocalDate availableDate,
    String regulationType
) {}

public enum DepositType {
    NEXT_DAY, TWO_DAY, CASE_BY_CASE, LARGE_DEPOSIT_EXCEPTION, NEW_ACCOUNT_RULE
}
```

## Account Lifecycle State Machine

```java
package com.banking.platform.ledger.service;

import com.banking.platform.ledger.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountLifecycleService {
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void transitionAccountStatus(String accountId, AccountStatus newStatus) {
        BankAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));

        AccountStatus currentStatus = account.getAccountStatus();

        if (!isValidTransition(currentStatus, newStatus)) {
            throw new InvalidStateTransitionException(
                currentStatus + " -> " + newStatus
            );
        }

        account.setAccountStatus(newStatus);
        accountRepository.save(account);

        eventPublisher.publishEvent(new AccountStatusChangedEvent(
            accountId, currentStatus, newStatus
        ));
    }

    private boolean isValidTransition(AccountStatus from, AccountStatus to) {
        return switch (from) {
            case PENDING_OPEN -> to == AccountStatus.OPEN || to == AccountStatus.REJECTED;
            case OPEN -> to == AccountStatus.RESTRICTED || to == AccountStatus.CLOSED;
            case RESTRICTED -> to == AccountStatus.DORMANT || to == AccountStatus.OPEN;
            case DORMANT -> to == AccountStatus.CLOSED || to == AccountStatus.OPEN;
            case CLOSED -> false;  // No transitions from CLOSED
            case REJECTED -> false;
        };
    }
}

@Entity
@Table(name = "bank_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String accountId;

    private String accountNumber;
    private String customerId;
    private String productId;

    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus;

    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    @Version
    private Long version;
}

public enum AccountStatus {
    PENDING_OPEN, OPEN, RESTRICTED, DORMANT, CLOSED, REJECTED
}
```

## Posting Adapter Pattern

The `PostingAdapter` integrates with downstream ledger systems, enabling pluggable implementations.

```java
package com.banking.platform.ledger.adapter;

import com.banking.platform.ledger.dto.*;
import java.util.List;

public interface PostingAdapter {
    PostingResult postEntry(LedgerEntryRequest entry);
    
    PostingResult postBatch(List<LedgerEntryRequest> entries);
    
    BalanceResponse queryBalance(String accountId);
}

@Component
@RequiredArgsConstructor
public class InternalLedgerPostingAdapter implements PostingAdapter {
    private final LedgerEntryRepository ledgerRepository;
    private final BalanceService balanceService;

    @Override
    @Transactional
    public PostingResult postEntry(LedgerEntryRequest entry) {
        LedgerEntry ledger = LedgerEntry.builder()
            .accountId(entry.getAccountId())
            .amount(entry.getAmount())
            .direction(entry.getDirection())
            .transactionType(entry.getTransactionType())
            .transactionId(entry.getTransactionId())
            .memo(entry.getMemo())
            .posted(entry.isPosted())
            .valueDate(entry.getValueDate())
            .postingDate(entry.getPostingDate())
            .build();

        LedgerEntry saved = ledgerRepository.save(ledger);

        return PostingResult.builder()
            .success(true)
            .journalId(saved.getJournalId())
            .build();
    }

    @Override
    @Transactional
    public PostingResult postBatch(List<LedgerEntryRequest> entries) {
        entries.forEach(this::postEntry);
        return PostingResult.builder()
            .success(true)
            .entriesPosted(entries.size())
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceResponse queryBalance(String accountId) {
        BalanceSnapshot snapshot = balanceService.getBalance(accountId);
        return BalanceResponse.builder()
            .accountId(snapshot.accountId())
            .ledgerBalance(snapshot.ledgerBalance())
            .availableBalance(snapshot.availableBalance())
            .build();
    }
}

@Component
@RequiredArgsConstructor
public class ExternalLedgerPostingAdapter implements PostingAdapter {
    private final RestTemplate restTemplate;

    @Override
    public PostingResult postEntry(LedgerEntryRequest entry) {
        ResponseEntity<ExternalPostingResponse> response = restTemplate.postForEntity(
            "https://external-ledger-api/post",
            entry,
            ExternalPostingResponse.class
        );

        return PostingResult.builder()
            .success(response.getStatusCode().is2xxSuccessful())
            .journalId(response.getBody().getJournalId())
            .build();
    }

    @Override
    public PostingResult postBatch(List<LedgerEntryRequest> entries) {
        return entries.stream()
            .map(this::postEntry)
            .reduce(
                PostingResult.builder().success(true).entriesPosted(0).build(),
                (acc, result) -> {
                    acc.setEntriesPosted(acc.getEntriesPosted() + 1);
                    return acc;
                }
            );
    }

    @Override
    public BalanceResponse queryBalance(String accountId) {
        ResponseEntity<BalanceResponse> response = restTemplate.getForEntity(
            "https://external-ledger-api/balance/" + accountId,
            BalanceResponse.class
        );

        return response.getBody();
    }
}
```

## Overdraft & NSF Detection

```java
package com.banking.platform.ledger.service;

import com.banking.platform.ledger.entity.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OverdraftDetectionService {
    private final BalanceService balanceService;
    private final OverdraftPolicyRepository overdraftPolicyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OverdraftDecision evaluateTransaction(
        String accountId,
        BigDecimal debitAmount
    ) {
        BalanceSnapshot balance = balanceService.getBalance(accountId);
        OverdraftPolicy policy = overdraftPolicyRepository.findByAccountId(accountId)
            .orElse(OverdraftPolicy.builder().overdraftEnabled(false).build());

        BigDecimal projectedBalance = balance.availableBalance().subtract(debitAmount);

        if (projectedBalance.compareTo(BigDecimal.ZERO) < 0) {
            if (policy.isOverdraftEnabled() && 
                projectedBalance.negate().compareTo(policy.getOverdraftLimit()) <= 0) {
                eventPublisher.publishEvent(
                    new OverdraftOccurredEvent(accountId, debitAmount, projectedBalance)
                );
                return OverdraftDecision.builder()
                    .allowed(true)
                    .reason("Covered by overdraft protection")
                    .overdraftFeeApplied(true)
                    .build();
            } else {
                eventPublisher.publishEvent(
                    new NSFEvent(accountId, debitAmount, balance.availableBalance())
                );
                return OverdraftDecision.builder()
                    .allowed(false)
                    .reason("NSF: Insufficient funds")
                    .nsfFeeApplied(true)
                    .build();
            }
        }

        return OverdraftDecision.builder()
            .allowed(true)
            .reason("Sufficient available balance")
            .build();
    }
}
```

## Cutoff Times

```java
package com.banking.platform.ledger.config;

import org.springframework.stereotype.Component;
import lombok.Data;
import java.time.LocalTime;

@Component
@Data
public class CutoffTimeConfig {
    // Wire cutoff: 9 PM ET for next-day settlement
    private static final LocalTime WIRE_CUTOFF = LocalTime.of(21, 0);

    // ACH same-day cutoff: 2:30 PM ET
    private static final LocalTime ACH_SAME_DAY_CUTOFF = LocalTime.of(14, 30);

    // ACH second window: 5:00 PM ET
    private static final LocalTime ACH_SECOND_WINDOW_CUTOFF = LocalTime.of(17, 0);

    public boolean isBeforeWireCutoff() {
        return LocalTime.now().isBefore(WIRE_CUTOFF);
    }

    public boolean isBeforeACHSameDayCutoff() {
        return LocalTime.now().isBefore(ACH_SAME_DAY_CUTOFF);
    }

    public boolean isBeforeACHSecondWindowCutoff() {
        return LocalTime.now().isBefore(ACH_SECOND_WINDOW_CUTOFF);
    }
}
```

## Flyway Migration

```sql
-- V2__init_ledger.sql
CREATE TABLE ledger_entries (
    journal_id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    direction VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    transaction_id VARCHAR(36),
    memo VARCHAR(255),
    posted BOOLEAN NOT NULL DEFAULT FALSE,
    value_date DATE,
    posting_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    FOREIGN KEY (account_id) REFERENCES bank_accounts(account_id)
);

CREATE TABLE account_balances (
    account_id VARCHAR(36) PRIMARY KEY,
    ledger_balance DECIMAL(20, 2),
    available_balance DECIMAL(20, 2),
    pending_debits DECIMAL(20, 2),
    pending_credits DECIMAL(20, 2),
    holds DECIMAL(20, 2),
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    FOREIGN KEY (account_id) REFERENCES bank_accounts(account_id)
);

CREATE TABLE holds (
    hold_id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    hold_type VARCHAR(50) NOT NULL,
    reason VARCHAR(500),
    external_transaction_id VARCHAR(36),
    place_date_time TIMESTAMP NOT NULL,
    released_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    FOREIGN KEY (account_id) REFERENCES bank_accounts(account_id)
);

CREATE TABLE bank_accounts (
    account_id VARCHAR(36) PRIMARY KEY,
    account_number VARCHAR(16) UNIQUE NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36),
    account_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    FOREIGN KEY (customer_id) REFERENCES customer_profiles(customer_id),
    FOREIGN KEY (product_id) REFERENCES product_catalog(product_id)
);

CREATE TABLE overdraft_policies (
    account_id VARCHAR(36) PRIMARY KEY,
    overdraft_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    overdraft_limit DECIMAL(20, 2),
    FOREIGN KEY (account_id) REFERENCES bank_accounts(account_id)
);

CREATE INDEX idx_account_posted ON ledger_entries(account_id, posted);
CREATE INDEX idx_posting_date ON ledger_entries(posting_date);
CREATE INDEX idx_value_date ON ledger_entries(value_date);
CREATE INDEX idx_hold_account ON holds(account_id);
CREATE INDEX idx_hold_status ON holds(status);
```

---

## 10 Critical Rules

1. **Always Use Optimistic Locking**: Add @Version field to AccountBalance and LedgerEntry entities; use ObjectOptimisticLockingFailureException handling in @Transactional methods to detect and retry concurrent balance updates.

2. **Calculate Available Balance Consistently**: Available = Ledger Balance - Total Holds - Pending Debits + Pending Credits; recalculate on every balance query from source tables, not cached derived values.

3. **Enforce Memo vs Final Posting Distinction**: Mark entries posted=false for memo (authorization) posting; post=true for final ledger entries; never calculate available balance from memo-posted entries.

4. **Implement Atomic Hold Release with TTL**: Use ACTIVE/RELEASED/EXPIRED statuses; run scheduled job hourly to expire TTL-based holds; emit HoldExpiredEvent for every expiration to trigger notifications.

5. **Validate Regulation CC Rules by Account Lifecycle**: Check isNewAccount() flag (created <30 days ago); apply 9-day availability window for new accounts vs 1-2 days for established accounts; reject deposits on CLOSED accounts.

6. **Cutoff Times are Time-Zone Aware**: Store all cutoffs in Eastern Time (ET); use ZonedDateTime for comparison against incoming transaction timestamps; handle DST transitions with ZoneId.of("America/New_York").

7. **Prevent Negative Balance Transitions**: In account status machine, validate no debit postings when status=DORMANT or CLOSED; enforce OPEN→RESTRICTED before allowing NSF; never silently allow overdraft without OverdraftPolicy record.

8. **Posting Adapter is Pluggable Not Polymorphic**: Use adapter pattern for PostingAdapter interface; select implementation by configuration (internal vs external ledger); do not couple business logic to specific adapter.

9. **Audit All Balance-Affecting Operations**: Log account ID, amount, direction, transaction ID, and version number on every LedgerEntry creation; emit Spring ApplicationEvents for NSF/Overdraft/Hold changes for downstream audit trail.

10. **Flyway Migrations Enforce Schema Contracts**: Create V2 for ledger tables with explicit FK constraints and indexes on (account_id, posted, value_date); never modify V1 or V2 once deployed; add UNIQUE constraints on account_number to prevent duplicate accounts.

