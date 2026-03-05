---
name: banking-payments-hub
description: |
  **Payments Hub & Payment Orchestration**: Master payment rail selection, initiation workflows, external payee management, velocity limits, payment state machines, and multi-channel payment routing across wires, ACH, RTP, internal transfers, bill pay, and P2P networks.

  MANDATORY TRIGGERS: Payments Hub, payments hub, PaymentsHub, payment orchestrator, PaymentOrchestrator, rail selection, PaymentRailSelector, payment initiation, PaymentInitiationRequest, transfer service, TransferService, external payee, ExternalPayee, bill pay, BillPayService, P2P transfer, peer to peer, Zelle, Venmo, payment routing, PaymentRouter, limits service, LimitsService, velocity controls, velocity, daily limit, monthly limit, payment status, PaymentStatus, payment state machine, payment workflow, wire cutoff, ACH cutoff, RTP, FedNow, Fedwire, payment rail, PaymentRail
---

# Banking Payments Hub & Orchestration

The payments hub serves as the central orchestrator for all outbound and inbound payment flows, providing intelligent rail selection, velocity-based limit controls, external payee management, and pluggable adapters for each payment rail (WIRE, ACH, RTP, internal, bill pay, P2P). This skill covers designing idempotent payment initiation, implementing comprehensive payment state machines, and integrating with downstream payment rail services.

---

## Payment Rail Types & Selection Strategy

```java
package com.banking.platform.payment.domain;

import lombok.*;
import java.math.BigDecimal;

public enum PaymentRail {
    WIRE,           // Immediate wire transfer via Fedwire
    ACH,            // Automated Clearing House (batch)
    RTP,            // Real-Time Payments (FedNow)
    INTERNAL,       // Same-bank transfer
    BILL_PAY,       // Bill payment via PayOne/ACI
    P2P,            // Peer-to-peer (Zelle)
    CHECK           // Physical check mailing
}

@Component
@RequiredArgsConstructor
public class PaymentRailSelector {
    private final LimitsService limitsService;
    private final AccountRepository accountRepository;
    private final CutoffTimeConfig cutoffTimeConfig;

    public PaymentRailSelectorResult selectRail(PaymentInitiationRequest request) {
        // Rule 1: Amount > $25,000 must be wire
        if (request.getAmount().compareTo(BigDecimal.valueOf(25000)) > 0) {
            if (cutoffTimeConfig.isBeforeWireCutoff()) {
                return PaymentRailSelectorResult.builder()
                    .selectedRail(PaymentRail.WIRE)
                    .reason("Amount exceeds $25k threshold")
                    .priority(1)
                    .build();
            } else {
                return PaymentRailSelectorResult.builder()
                    .selectedRail(PaymentRail.ACH)
                    .reason("Wire cutoff passed; routing to next-day ACH")
                    .priority(2)
                    .build();
            }
        }

        // Rule 2: Same-bank transfer routes to INTERNAL
        if (isSameBank(request.getCreditAccountId())) {
            return PaymentRailSelectorResult.builder()
                .selectedRail(PaymentRail.INTERNAL)
                .reason("Same-bank transfer")
                .priority(1)
                .build();
        }

        // Rule 3: Real-time preferred if not scheduled and amount < $1M
        if (!isScheduled(request) && request.getAmount().compareTo(BigDecimal.valueOf(1_000_000)) < 0) {
            return PaymentRailSelectorResult.builder()
                .selectedRail(PaymentRail.RTP)
                .reason("Eligible for real-time payment")
                .priority(1)
                .build();
        }

        // Rule 4: Fallback to ACH for bulk, scheduled, or large amounts
        return PaymentRailSelectorResult.builder()
            .selectedRail(PaymentRail.ACH)
            .reason("Default routing to ACH")
            .priority(3)
            .build();
    }

    private boolean isSameBank(String creditAccountId) {
        return accountRepository.existsById(creditAccountId);
    }

    private boolean isScheduled(PaymentInitiationRequest request) {
        return request.getScheduledDate() != null && 
            request.getScheduledDate().isAfter(LocalDate.now());
    }
}

@Data
@Builder
public class PaymentRailSelectorResult {
    private PaymentRail selectedRail;
    private String reason;
    private int priority;
}
```

## Payment Initiation Request Record

```java
package com.banking.platform.payment.dto;

import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentInitiationRequest(
    String debitAccountId,
    String creditAccountId,        // For internal transfers
    String payeeId,                // For external payments
    BigDecimal amount,
    String currency,               // USD, EUR, etc.
    String description,
    PaymentRail railHint,          // Client preference
    LocalDate scheduledDate,       // Null = immediate
    String idempotencyKey,         // UUID for idempotency
    String correlationId
) {}

public record PaymentInitiationResponse(
    String paymentId,
    String status,
    LocalDate executionDate,
    String idempotencyKey,
    String message
) {}
```

## Payment Orchestrator

The central orchestrator validates, routes, and executes payments.

```java
package com.banking.platform.payment.service;

import com.banking.platform.payment.dto.*;
import com.banking.platform.payment.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentOrchestrator {
    private final PaymentRepository paymentRepository;
    private final PaymentRailSelector railSelector;
    private final LimitsService limitsService;
    private final ExternalPayeeService externalPayeeService;
    private final PaymentRailAdapterFactory adapterFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final IdempotencyKeyService idempotencyKeyService;

    @Transactional
    @CircuitBreaker(name = "paymentOrchestration", fallbackMethod = "fallbackInitiatePayment")
    public PaymentInitiationResponse initiatePayment(PaymentInitiationRequest request) {
        // Step 1: Idempotency check
        if (idempotencyKeyService.exists(request.idempotencyKey())) {
            Payment existingPayment = idempotencyKeyService.getPayment(request.idempotencyKey());
            return mapToResponse(existingPayment);
        }

        // Step 2: Validate account balance
        BalanceSnapshot balance = balanceService.getBalance(request.debitAccountId());
        if (balance.availableBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(request.debitAccountId());
        }

        // Step 3: Check velocity limits
        LimitCheckResult limitCheck = limitsService.checkLimits(
            request.debitAccountId(),
            request.amount(),
            PaymentLimitType.DAILY
        );
        if (!limitCheck.allowed()) {
            throw new VelocityLimitExceededException(
                "Daily limit exceeded by $" + limitCheck.excessAmount()
            );
        }

        // Step 4: Select payment rail
        PaymentRailSelectorResult railSelection = railSelector.selectRail(request);
        PaymentRail selectedRail = request.railHint() != null ? 
            request.railHint() : railSelection.selectedRail();

        // Step 5: Create and persist payment
        Payment payment = Payment.builder()
            .paymentId(UUID.randomUUID().toString())
            .debitAccountId(request.debitAccountId())
            .creditAccountId(request.creditAccountId())
            .payeeId(request.payeeId())
            .amount(request.amount())
            .currency(request.currency())
            .description(request.description())
            .selectedRail(selectedRail)
            .status(PaymentStatus.INITIATED)
            .idempotencyKey(request.idempotencyKey())
            .correlationId(request.correlationId())
            .createdAt(LocalDateTime.now())
            .scheduledDate(request.scheduledDate())
            .build();

        Payment savedPayment = paymentRepository.save(payment);
        idempotencyKeyService.store(request.idempotencyKey(), savedPayment.getPaymentId());

        // Step 6: Route to rail adapter
        PaymentRailAdapter adapter = adapterFactory.getAdapter(selectedRail);
        PaymentRailResult railResult = adapter.submitPayment(savedPayment);

        // Step 7: Update payment status
        savedPayment.setStatus(railResult.success() ? 
            PaymentStatus.SUBMITTED : PaymentStatus.VALIDATION_FAILED);
        savedPayment.setExternalReference(railResult.externalReference());
        paymentRepository.save(savedPayment);

        // Step 8: Emit event
        eventPublisher.publishEvent(new PaymentInitiatedEvent(savedPayment));

        return mapToResponse(savedPayment);
    }

    public PaymentInitiationResponse fallbackInitiatePayment(
        PaymentInitiationRequest request,
        Exception ex
    ) {
        return PaymentInitiationResponse.builder()
            .status("FALLBACK_PROCESSING")
            .message("Payment queued for processing: " + ex.getMessage())
            .build();
    }

    private PaymentInitiationResponse mapToResponse(Payment payment) {
        return PaymentInitiationResponse.builder()
            .paymentId(payment.getPaymentId())
            .status(payment.getStatus().name())
            .executionDate(payment.getScheduledDate())
            .idempotencyKey(payment.getIdempotencyKey())
            .message("Payment initiated successfully")
            .build();
    }
}

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String paymentId;

    private String debitAccountId;
    private String creditAccountId;
    private String payeeId;

    private BigDecimal amount;
    private String currency;
    private String description;

    @Enumerated(EnumType.STRING)
    private PaymentRail selectedRail;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String idempotencyKey;
    private String correlationId;
    private String externalReference;  // Bank reference number

    private LocalDate scheduledDate;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime rejectedAt;

    private String rejectionReason;

    @Version
    private Long version;
}

public enum PaymentStatus {
    INITIATED, VALIDATED, SUBMITTED, CONFIRMED, REJECTED, RETURNED, REVERSED
}
```

## Limits Service with Velocity Controls

```java
package com.banking.platform.payment.service;

import com.banking.platform.payment.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class LimitsService {
    private final PaymentLimitRepository limitRepository;
    private final PaymentRepository paymentRepository;
    private final AccountRiskTierRepository riskTierRepository;

    @Transactional(readOnly = true)
    public LimitCheckResult checkLimits(
        String accountId,
        BigDecimal amount,
        PaymentLimitType limitType
    ) {
        PaymentLimit limit = limitRepository.findByAccountIdAndLimitType(accountId, limitType)
            .orElse(getDefaultLimit(accountId, limitType));

        BigDecimal usedAmount = calculateUsedAmount(accountId, limitType);
        BigDecimal remaining = limit.getLimitAmount().subtract(usedAmount);

        if (amount.compareTo(remaining) > 0) {
            return LimitCheckResult.builder()
                .allowed(false)
                .excessAmount(amount.subtract(remaining))
                .message("Velocity limit exceeded")
                .build();
        }

        return LimitCheckResult.builder()
            .allowed(true)
            .remainingLimit(remaining.subtract(amount))
            .build();
    }

    private BigDecimal calculateUsedAmount(String accountId, PaymentLimitType limitType) {
        return switch (limitType) {
            case DAILY -> paymentRepository.sumByDebitAccountIdAndCreatedDateAndStatus(
                accountId, LocalDate.now(), PaymentStatus.CONFIRMED
            );
            case MONTHLY -> paymentRepository.sumByDebitAccountIdAndYearMonthAndStatus(
                accountId, YearMonth.now(), PaymentStatus.CONFIRMED
            );
            case PER_TRANSACTION -> BigDecimal.ZERO;  // Per-transaction is checked inline
        };
    }

    private PaymentLimit getDefaultLimit(String accountId, PaymentLimitType limitType) {
        AccountRiskTier riskTier = riskTierRepository.findByAccountId(accountId)
            .orElse(AccountRiskTier.builder().riskLevel("STANDARD").build());

        return switch (riskTier.riskLevel()) {
            case "PREMIUM" -> switch (limitType) {
                case DAILY -> PaymentLimit.builder().limitAmount(BigDecimal.valueOf(100_000)).build();
                case MONTHLY -> PaymentLimit.builder().limitAmount(BigDecimal.valueOf(500_000)).build();
                case PER_TRANSACTION -> PaymentLimit.builder().limitAmount(BigDecimal.valueOf(50_000)).build();
            };
            case "STANDARD" -> switch (limitType) {
                case DAILY -> PaymentLimit.builder().limitAmount(BigDecimal.valueOf(50_000)).build();
                case MONTHLY -> PaymentLimit.builder().limitAmount(BigDecimal.valueOf(250_000)).build();
                case PER_TRANSACTION -> PaymentLimit.builder().limitAmount(BigDecimal.valueOf(25_000)).build();
            };
            default -> switch (limitType) {
                case DAILY -> PaymentLimit.builder().limitAmount(BigDecimal.valueOf(10_000)).build();
                case MONTHLY -> PaymentLimit.builder().limitAmount(BigDecimal.valueOf(50_000)).build();
                case PER_TRANSACTION -> PaymentLimit.builder().limitAmount(BigDecimal.valueOf(5_000)).build();
            };
        };
    }
}

@Entity
@Table(name = "payment_limits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentLimit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String limitId;

    private String accountId;

    @Enumerated(EnumType.STRING)
    private PaymentLimitType limitType;

    private BigDecimal limitAmount;
    private LocalDateTime effectiveDate;

    @Enumerated(EnumType.STRING)
    private LimitStatus status;
}

public enum PaymentLimitType {
    DAILY, MONTHLY, PER_TRANSACTION
}

public enum LimitStatus {
    ACTIVE, SUSPENDED, OVERRIDDEN
}

@Data
@Builder
public class LimitCheckResult {
    private boolean allowed;
    private BigDecimal excessAmount;
    private BigDecimal remainingLimit;
    private String message;
}
```

## External Payee Management

```java
package com.banking.platform.payment.service;

import com.banking.platform.payment.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExternalPayeeService {
    private final ExternalPayeeRepository payeeRepository;
    private final PayeeVerificationAdapter verificationAdapter;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ExternalPayeeResponse addPayee(ExternalPayeeRequest request) {
        ExternalPayee payee = ExternalPayee.builder()
            .payeeId(UUID.randomUUID().toString())
            .accountId(request.getAccountId())
            .payeeName(request.getPayeeName())
            .bankAccountNumber(request.getBankAccountNumber())
            .bankRoutingNumber(request.getBankRoutingNumber())
            .bankName(request.getBankName())
            .payeeCategory(request.getPayeeCategory())
            .verificationStatus(VerificationStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build();

        ExternalPayee saved = payeeRepository.save(payee);

        // Trigger micro-deposit verification
        verificationAdapter.initiateMicroDepositVerification(saved);

        eventPublisher.publishEvent(new PayeeAddedEvent(saved));

        return ExternalPayeeResponse.builder()
            .payeeId(saved.getPayeeId())
            .verificationStatus(VerificationStatus.PENDING.name())
            .message("Payee added; awaiting micro-deposit verification")
            .build();
    }

    @Transactional
    public void verifyPayee(String payeeId, BigDecimal amount1, BigDecimal amount2) {
        ExternalPayee payee = payeeRepository.findById(payeeId)
            .orElseThrow(() -> new PayeeNotFoundException(payeeId));

        boolean verified = verificationAdapter.verifyMicroDeposits(
            payee, amount1, amount2
        );

        if (verified) {
            payee.setVerificationStatus(VerificationStatus.VERIFIED);
            payee.setVerifiedAt(LocalDateTime.now());
        } else {
            payee.setVerificationStatus(VerificationStatus.FAILED);
            payee.setVerificationFailedAt(LocalDateTime.now());
        }

        payeeRepository.save(payee);
        eventPublisher.publishEvent(new PayeeVerifiedEvent(payee, verified));
    }
}

@Entity
@Table(name = "external_payees")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalPayee {
    @Id
    private String payeeId;

    private String accountId;
    private String payeeName;
    private String bankAccountNumber;
    private String bankRoutingNumber;
    private String bankName;

    @Enumerated(EnumType.STRING)
    private PayeeCategory payeeCategory;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    private LocalDateTime createdAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime verificationFailedAt;
}

public enum VerificationStatus {
    PENDING, VERIFIED, FAILED, BLOCKED
}

public enum PayeeCategory {
    PERSONAL, BUSINESS, BILL_PAYMENT, GOVERNMENT
}
```

## Bill Pay Service

```java
package com.banking.platform.payment.service;

import com.banking.platform.payment.dto.*;
import com.banking.platform.payment.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BillPayService {
    private final PayeeDirectoryClient payeeDirectoryClient;
    private final BillPayAdapter billPayAdapter;
    private final PaymentRepository paymentRepository;

    @Transactional
    public BillPaymentResponse scheduleBillPayment(BillPaymentRequest request) {
        // Lookup payee in directory
        Payee payeeInfo = payeeDirectoryClient.lookupPayee(request.getPayeeName());

        if (payeeInfo == null || payeeInfo.getBillerCode() == null) {
            throw new PayeeNotFoundException(request.getPayeeName());
        }

        // Create bill payment via adapter
        BillPaymentSubmission submission = BillPaymentSubmission.builder()
            .consumerAccountNumber(request.getDebitAccountId())
            .billerCode(payeeInfo.getBillerCode())
            .billerAccountNumber(request.getPayeeAccountNumber())
            .amount(request.getAmount())
            .dueDate(request.getDueDate())
            .scheduleDate(request.getScheduleDate())
            .memo(request.getMemo())
            .build();

        BillPaymentResult result = billPayAdapter.submit(submission);

        Payment payment = Payment.builder()
            .paymentId(result.getConfirmationNumber())
            .debitAccountId(request.getDebitAccountId())
            .payeeId(payeeInfo.getPayeeId())
            .amount(request.getAmount())
            .selectedRail(PaymentRail.BILL_PAY)
            .status(PaymentStatus.SUBMITTED)
            .externalReference(result.getConfirmationNumber())
            .createdAt(LocalDateTime.now())
            .scheduledDate(request.getScheduleDate())
            .build();

        paymentRepository.save(payment);

        return BillPaymentResponse.builder()
            .confirmationNumber(result.getConfirmationNumber())
            .payeeName(payeeInfo.getName())
            .scheduledDate(request.getScheduleDate())
            .status("SCHEDULED")
            .build();
    }
}

public record BillPaymentRequest(
    String debitAccountId,
    String payeeName,
    String payeeAccountNumber,
    BigDecimal amount,
    LocalDate dueDate,
    LocalDate scheduleDate,
    String memo
) {}
```

## P2P Transfer Service (Zelle-like)

```java
package com.banking.platform.payment.service;

import com.banking.platform.payment.dto.*;
import com.banking.platform.payment.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class P2PTransferService {
    private final P2PDirectoryService directoryService;
    private final FraudDetectionService fraudDetectionService;
    private final PostingAdapter postingAdapter;
    private final PaymentRepository paymentRepository;

    @Transactional
    public P2PTransferResponse initiateP2PTransfer(P2PTransferRequest request) {
        // Step 1: Lookup recipient by alias (phone/email)
        P2PRecipient recipient = directoryService.lookupRecipient(
            request.getRecipientAlias(),
            request.getRecipientAliasType()
        );

        if (recipient == null) {
            throw new RecipientNotFoundException(request.getRecipientAlias());
        }

        // Step 2: Fraud check
        FraudCheckResult fraudCheck = fraudDetectionService.checkP2PTransfer(
            request.getDebitAccountId(),
            recipient.getAccountId(),
            request.getAmount()
        );

        if (fraudCheck.isFlagged()) {
            return P2PTransferResponse.builder()
                .success(false)
                .message("Transfer flagged for fraud review")
                .build();
        }

        // Step 3: Create internal transfer
        Payment payment = Payment.builder()
            .paymentId(UUID.randomUUID().toString())
            .debitAccountId(request.getDebitAccountId())
            .creditAccountId(recipient.getAccountId())
            .amount(request.getAmount())
            .selectedRail(PaymentRail.P2P)
            .status(PaymentStatus.CONFIRMED)
            .description(request.getMemo())
            .createdAt(LocalDateTime.now())
            .confirmedAt(LocalDateTime.now())
            .build();

        Payment savedPayment = paymentRepository.save(payment);

        // Step 4: Post to ledger atomically
        PostingResult postingResult = postingAdapter.postEntry(
            LedgerEntryRequest.builder()
                .accountId(request.getDebitAccountId())
                .amount(request.getAmount())
                .direction(TransactionType.DEBIT)
                .transactionType(TransactionType.TRANSFER)
                .transactionId(savedPayment.getPaymentId())
                .posted(true)
                .build()
        );

        return P2PTransferResponse.builder()
            .success(true)
            .transferId(savedPayment.getPaymentId())
            .recipientName(recipient.getName())
            .amount(request.getAmount())
            .message("P2P transfer completed successfully")
            .build();
    }
}

public record P2PTransferRequest(
    String debitAccountId,
    String recipientAlias,          // Phone or email
    P2PAliasType recipientAliasType,
    BigDecimal amount,
    String memo
) {}

public enum P2PAliasType {
    PHONE, EMAIL, BANK_ACCOUNT
}
```

## Payment Rail Adapter Factory

```java
package com.banking.platform.payment.adapter;

import com.banking.platform.payment.domain.PaymentRail;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentRailAdapterFactory {
    private final WirePaymentAdapter wireAdapter;
    private final AchPaymentAdapter achAdapter;
    private final RtpPaymentAdapter rtpAdapter;
    private final InternalTransferAdapter internalAdapter;
    private final BillPayAdapter billPayAdapter;
    private final P2PAdapter p2pAdapter;

    public PaymentRailAdapter getAdapter(PaymentRail rail) {
        return switch (rail) {
            case WIRE -> wireAdapter;
            case ACH -> achAdapter;
            case RTP -> rtpAdapter;
            case INTERNAL -> internalAdapter;
            case BILL_PAY -> billPayAdapter;
            case P2P -> p2pAdapter;
            case CHECK -> throw new UnsupportedOperationException("Check payments not implemented");
        };
    }
}

public interface PaymentRailAdapter {
    PaymentRailResult submitPayment(Payment payment);
    PaymentRailResult checkStatus(String externalReference);
    PaymentRailResult reversePayment(String externalReference);
}

@Data
@Builder
public class PaymentRailResult {
    private boolean success;
    private String externalReference;  // Fed reference number
    private String statusCode;
    private String errorMessage;
}
```

## Flyway Migration

```sql
-- V3__init_payments_hub.sql
CREATE TABLE payments (
    payment_id VARCHAR(36) PRIMARY KEY,
    debit_account_id VARCHAR(36) NOT NULL,
    credit_account_id VARCHAR(36),
    payee_id VARCHAR(36),
    amount DECIMAL(20, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    description VARCHAR(500),
    selected_rail VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(36) UNIQUE,
    correlation_id VARCHAR(36),
    external_reference VARCHAR(50),
    scheduled_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    rejected_at TIMESTAMP,
    rejection_reason VARCHAR(500),
    version BIGINT DEFAULT 0,
    FOREIGN KEY (debit_account_id) REFERENCES bank_accounts(account_id),
    FOREIGN KEY (credit_account_id) REFERENCES bank_accounts(account_id)
);

CREATE TABLE external_payees (
    payee_id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    payee_name VARCHAR(255) NOT NULL,
    bank_account_number VARCHAR(20),
    bank_routing_number VARCHAR(9),
    bank_name VARCHAR(255),
    payee_category VARCHAR(50),
    verification_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP,
    verification_failed_at TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES bank_accounts(account_id),
    UNIQUE(account_id, bank_account_number, bank_routing_number)
);

CREATE TABLE payment_limits (
    limit_id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    limit_type VARCHAR(50) NOT NULL,
    limit_amount DECIMAL(20, 2) NOT NULL,
    effective_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    FOREIGN KEY (account_id) REFERENCES bank_accounts(account_id),
    UNIQUE(account_id, limit_type)
);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(36) PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES payments(payment_id)
);

CREATE INDEX idx_payment_status ON payments(status);
CREATE INDEX idx_payment_debit ON payments(debit_account_id);
CREATE INDEX idx_payment_rail ON payments(selected_rail);
CREATE INDEX idx_payee_account ON external_payees(account_id);
CREATE INDEX idx_payee_verification ON external_payees(verification_status);
```

---

## 10 Critical Rules

1. **Idempotency is Mandatory**: Every PaymentInitiationRequest must include idempotencyKey (UUID); store mapping idempotencyKey → paymentId immediately after creation; return cached response if same key resubmitted within 24 hours.

2. **Rail Selection Logic is Deterministic**: Implement PaymentRailSelector with explicit rules: amount>$25k→WIRE, same-bank→INTERNAL, real-time eligible→RTP, fallback→ACH; use cutoff times from CutoffTimeConfig for deterministic decision-making.

3. **Velocity Limits Block Before Submission**: Call LimitsService.checkLimits() BEFORE creating Payment entity; reject with VelocityLimitExceededException; maintain daily/monthly/per-transaction limits per risk tier in database, not hardcoded.

4. **External Payee Verification is Async**: Micro-deposit verification takes 1-3 business days; mark payee verificationStatus=PENDING; do not allow payments to PENDING payees; implement webhook callback from verification provider to update status.

5. **Payment Status Machine is Strict**: Enforce transitions INITIATED→VALIDATED→SUBMITTED→(CONFIRMED|REJECTED); prevent circular transitions; emit ApplicationEvent on every status change for audit trail and downstream notification.

6. **Resilience4j Circuit Breaker on Rail Adapters**: Use @CircuitBreaker on PaymentOrchestrator methods; define fallback strategy (queue for async retry); monitor circuit breaker metrics for payment rail health.

7. **Real-Time Payments (RTP/FedNow) Have Limits**: RTP supports up to $500,000 per transaction; cannot schedule RTP (only immediate); enable RTP only if ACCOUNT and BANK both RTP-capable; validate upfront.

8. **P2P Alias Resolution is Pluggable**: Directory service (P2PDirectoryService) abstracts Zelle/Venmo backend; lookup by phone/email/bank account token; return account mapping; never expose customer data cross-bank without consent.

9. **Payee Micro-Deposit Verification Store Amounts**: Store encrypted micro-deposit amounts in database; require customer to verify both deposits to $0.01 precision; block account if 3 failed verification attempts.

10. **Payment Confirmation Events Trigger Posting**: On PaymentStatus.CONFIRMED, emit PaymentConfirmedEvent; subscribe PostingAdapter to this event to journal entry atomically; prevent double-posting via transactional idempotency on ledger side.

