---
name: microservices-patterns
description: |
  **Microservices Patterns Skill**: Production-grade implementation of the most critical microservices design patterns for Java 21 + Spring Boot 3.x. Covers Circuit Breaker, Retry, Bulkhead, Rate Limiter (Resilience4j), Saga (Orchestration), Outbox Pattern, CQRS, API Gateway, Sidecar, Service Discovery, Distributed Tracing, and Idempotency.

  MANDATORY TRIGGERS: circuit breaker, retry, bulkhead, rate limiter, Resilience4j, @CircuitBreaker, @Retry, @Bulkhead, @RateLimiter, fallback, saga, choreography, orchestration, outbox pattern, CQRS, command query, event sourcing, API gateway, service mesh, sidecar, service discovery, Consul, Eureka, distributed tracing, OpenTelemetry, idempotency, idempotent key, distributed lock, RedLock, two-phase commit, eventually consistent, compensating transaction, microservices pattern, strangler fig, anti-corruption layer.
---

# Microservices Patterns Skill — Resilience4j · Saga · CQRS · Outbox

You are implementing microservices patterns in the **Java 21 / Spring Boot 3.3+ banking platform**. Every pattern here solves a specific distributed systems failure mode — choose deliberately.

---

## Pattern Decision Matrix

| Problem | Pattern | Implementation |
|---------|---------|---------------|
| Downstream service is slow/down | Circuit Breaker | Resilience4j `@CircuitBreaker` |
| Transient failures (network blips) | Retry with backoff | Resilience4j `@Retry` |
| Concurrency limits | Bulkhead | Resilience4j `@Bulkhead` |
| Too many requests | Rate Limiter | Resilience4j `@RateLimiter` |
| Distributed transaction across services | Saga (Orchestration) | State machine + Kafka |
| DB write + Kafka publish atomically | Outbox Pattern | Outbox table + poller |
| Separate read/write models | CQRS | Command/Query services |
| Duplicate message processing | Idempotency | `processed_events` table |
| Single entry point | API Gateway | Spring Cloud Gateway |

---

## 1. Circuit Breaker (Resilience4j)

```yaml
# application.yml — Circuit Breaker config
resilience4j:
  circuitbreaker:
    instances:
      payment-service:         # Name used in @CircuitBreaker(name = "payment-service")
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10  # Evaluate last 10 calls
        failureRateThreshold: 50         # Open if >50% fail
        slowCallRateThreshold: 80        # Open if >80% are slow
        slowCallDurationThreshold: 2000ms
        waitDurationInOpenState: 30s     # Wait 30s before trying half-open
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        registerHealthIndicator: true    # Exposed via /actuator/health
      external-ledger:
        slidingWindowSize: 20
        failureRateThreshold: 40
        waitDurationInOpenState: 60s
```

```java
package com.banking.platform.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentServiceClient {

    private final RestClient restClient;

    @CircuitBreaker(name = "payment-service", fallbackMethod = "processPaymentFallback")
    @Retry(name = "payment-service")      // Retry before circuit breaker records failure
    public PaymentResponse processPayment(PaymentRequest request) {
        return restClient.post()
                .uri("/payments")
                .body(request)
                .retrieve()
                .body(PaymentResponse.class);
    }

    /**
     * Fallback — must have SAME signature + Throwable as last parameter.
     * Called when: all retries exhausted OR circuit is open.
     * In banking: return a pending/queued state — NEVER silently fail.
     */
    private PaymentResponse processPaymentFallback(PaymentRequest request, Throwable ex) {
        log.error("payment.service.circuit_open request={} error={}", request.paymentId(), ex.getMessage());
        // Queue for async retry rather than returning an error to the user
        paymentRetryQueue.enqueue(request);
        return PaymentResponse.pending(request.paymentId(), "Queued for processing");
    }
}
```

---

## 2. Retry with Exponential Backoff

```yaml
resilience4j:
  retry:
    instances:
      payment-service:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2.0
        exponentialMaxWaitDuration: 10s
        retryExceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - org.springframework.web.client.ResourceAccessException
        ignoreExceptions:
          - com.banking.platform.exception.BusinessValidationException  # Don't retry business errors
          - org.springframework.web.client.HttpClientErrorException$BadRequest
```

---

## 3. Bulkhead — Concurrency Limits

```yaml
resilience4j:
  bulkhead:
    instances:
      payment-service:
        maxConcurrentCalls: 20           # Max simultaneous calls to this service
        maxWaitDuration: 500ms           # Wait before rejecting (BulkheadFullException)
  thread-pool-bulkhead:
    instances:
      report-generator:                  # Async bulkhead — own thread pool
        maxThreadPoolSize: 4
        coreThreadPoolSize: 2
        queueCapacity: 10
```

```java
@Bulkhead(name = "payment-service", type = Bulkhead.Type.SEMAPHORE)
public PaymentResponse processPayment(PaymentRequest req) { ... }

// Async bulkhead — returns CompletableFuture
@Bulkhead(name = "report-generator", type = Bulkhead.Type.THREADPOOL)
public CompletableFuture<ReportResponse> generateReport(ReportRequest req) { ... }
```

---

## 4. Rate Limiter

```yaml
resilience4j:
  ratelimiter:
    instances:
      external-api:
        limitForPeriod: 100             # 100 calls per refresh period
        limitRefreshPeriod: 1s
        timeoutDuration: 500ms          # Wait up to 500ms for a permit
```

```java
@RateLimiter(name = "external-api", fallbackMethod = "rateLimitedFallback")
public ExchangeRate fetchRate(String from, String to) { ... }

private ExchangeRate rateLimitedFallback(String from, String to, RequestNotPermitted ex) {
    log.warn("rate.limited from={} to={}", from, to);
    return exchangeRateCache.getLastKnown(from, to);  // Serve stale cached value
}
```

---

## 5. Saga Pattern — Orchestration

Use for distributed transactions spanning multiple services. The **orchestrator** drives the flow; each step can compensate (undo) on failure.

```java
package com.banking.platform.saga;

import com.banking.platform.account.service.AccountService;
import com.banking.platform.notification.service.NotificationService;
import com.banking.platform.payment.client.PaymentServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the "Create Account + Initial Deposit" saga.
 *
 * Steps:
 *   1. Create account          → compensate: delete account
 *   2. Process initial deposit → compensate: refund deposit
 *   3. Send welcome notification (no compensation needed — idempotent)
 *
 * On any failure: execute compensations in reverse order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountOnboardingSaga {

    private final AccountService      accountService;
    private final PaymentServiceClient paymentClient;
    private final NotificationService  notificationService;

    @Transactional   // TX wraps steps 1 only — cross-service calls are outside TX scope
    public OnboardingResult execute(OnboardingRequest request) {
        String accountId = null;
        String depositId = null;

        try {
            // Step 1 — Create account
            log.info("saga.account_onboarding.step1.start correlationId={}", request.correlationId());
            accountId = accountService.createAccount(request.toCreateAccountRequest()).accountId().toString();
            log.info("saga.account_onboarding.step1.done accountId={}", accountId);

            // Step 2 — Process initial deposit
            log.info("saga.account_onboarding.step2.start accountId={}", accountId);
            depositId = paymentClient.processDeposit(accountId, request.initialDeposit()).depositId();
            log.info("saga.account_onboarding.step2.done depositId={}", depositId);

            // Step 3 — Notify (best-effort, no compensation)
            notificationService.sendWelcome(request.email(), accountId);

            return OnboardingResult.success(accountId, depositId);

        } catch (Exception ex) {
            log.error("saga.account_onboarding.failed step=deposit correlationId={}", request.correlationId(), ex);
            compensate(accountId, depositId, request.correlationId());
            throw new SagaExecutionException("Account onboarding failed", ex);
        }
    }

    private void compensate(String accountId, String depositId, String correlationId) {
        // Reverse order — undo most recent step first
        if (depositId != null) {
            try {
                paymentClient.refundDeposit(depositId);
                log.info("saga.compensate.deposit.done depositId={}", depositId);
            } catch (Exception e) {
                log.error("saga.compensate.deposit.failed — manual intervention required depositId={}", depositId, e);
                // Alert ops — this needs manual reconciliation
            }
        }
        if (accountId != null) {
            try {
                accountService.closeAccount(java.util.UUID.fromString(accountId));
                log.info("saga.compensate.account.done accountId={}", accountId);
            } catch (Exception e) {
                log.error("saga.compensate.account.failed accountId={}", accountId, e);
            }
        }
    }
}
```

---

## 6. Outbox Pattern — Atomic DB + Kafka

Guarantees that Kafka events are published **if and only if** the DB transaction commits.

```java
// Outbox entity
@Entity
@Table(name = "outbox_events",
    indexes = @Index(name = "idx_outbox_published_created", columnList = "published, created_at"))
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutboxEvent {
    @Id @UuidGenerator private UUID id;
    @Column(name = "aggregate_type", nullable = false) private String aggregateType;
    @Column(name = "aggregate_id",   nullable = false) private UUID   aggregateId;
    @Column(name = "event_type",     nullable = false) private String eventType;
    @Column(name = "payload", columnDefinition = "TEXT") private String payload;  // JSON
    @Column(name = "published")      private boolean published;
    @CreationTimestamp @Column(name = "created_at") private Instant createdAt;
}

// Service — write domain entity + outbox in same transaction
@Transactional
public TransactionResponse createTransaction(CreateTransactionRequest req) {
    var tx = transactionRepository.save(toEntity(req));

    // Save outbox event atomically (same DB transaction)
    outboxRepository.save(OutboxEvent.builder()
        .aggregateType("Transaction")
        .aggregateId(tx.getId())
        .eventType("TransactionCreated")
        .payload(objectMapper.writeValueAsString(TransactionCreatedEvent.of(tx)))
        .published(false)
        .build());

    return transactionMapper.toResponse(tx);
}

// Outbox poller — separate scheduled job
@Component @Slf4j @RequiredArgsConstructor
public class OutboxEventPoller {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)    // Every 1 second
    @Transactional
    public void pollAndPublish() {
        var unpublished = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        unpublished.forEach(event -> {
            try {
                kafkaTemplate.send(topicFor(event.getEventType()), event.getAggregateId().toString(),
                        event.getPayload()).get(5, java.util.concurrent.TimeUnit.SECONDS);
                event.setPublished(true);
                log.info("outbox.published eventType={} aggregateId={}", event.getEventType(), event.getAggregateId());
            } catch (Exception ex) {
                log.error("outbox.publish.failed eventId={}", event.getId(), ex);
                // Retry on next poll cycle
            }
        });
    }

    private String topicFor(String eventType) {
        return switch (eventType) {
            case "TransactionCreated" -> KafkaTopicConfig.TRANSACTION_CREATED;
            case "AccountClosed"      -> KafkaTopicConfig.ACCOUNT_STATUS_CHANGED;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
```

---

## 7. CQRS — Separate Read and Write Models

```java
// COMMAND side — write model (normalized, consistent, transactional)
@Service
@Transactional
public class AccountCommandService {
    public AccountResponse createAccount(CreateAccountRequest cmd) { ... }
    public void closeAccount(UUID accountId) { ... }
    public void updateBalance(UUID accountId, BigDecimal delta) { ... }
}

// QUERY side — read model (denormalized, fast, eventually consistent)
@Service
@Transactional(readOnly = true)
public class AccountQueryService {

    @Cacheable(value = CacheNames.ACCOUNTS, key = "#accountId")
    public AccountDetailView getAccountDetail(UUID accountId) { ... }

    // Read from dedicated read replica or materialized view
    public Page<AccountSummaryView> searchAccounts(AccountSearchCriteria criteria, Pageable pageable) { ... }

    // Pre-aggregated report view — updated asynchronously by event consumer
    public AccountActivityReport getActivityReport(UUID accountId, YearMonth month) { ... }
}

// Projection builder — listens to domain events and updates the read model
@Component
@KafkaListener(topics = KafkaTopicConfig.TRANSACTION_CREATED)
public class AccountActivityProjection {
    public void on(TransactionCreatedEvent event) {
        // Update the pre-aggregated AccountActivityReport in MongoDB/Redis
        activityReportRepository.incrementTransactionCount(event.accountId(), event.occurredAt());
    }
}
```

---

## 8. Idempotency Key Pattern

```java
// Controller — accept idempotency key from client
@PostMapping("/payments")
public ResponseEntity<PaymentResponse> createPayment(
        @RequestHeader("Idempotency-Key") UUID idempotencyKey,
        @Valid @RequestBody PaymentRequest request) {
    return ResponseEntity.ok(paymentService.createPayment(idempotencyKey, request));
}

// Service — deduplicate by idempotency key
@Transactional
public PaymentResponse createPayment(UUID idempotencyKey, PaymentRequest request) {
    // 1. Check if already processed
    return idempotencyKeyRepository.findByKey(idempotencyKey)
        .map(record -> objectMapper.convertValue(record.getResponse(), PaymentResponse.class))
        .orElseGet(() -> {
            // 2. Process first time
            var response = doCreatePayment(request);

            // 3. Store response against key (TTL: 24h — clients may retry within a day)
            idempotencyKeyRepository.save(IdempotencyRecord.builder()
                    .key(idempotencyKey)
                    .response(objectMapper.convertValue(response, java.util.Map.class))
                    .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                    .build());

            return response;
        });
}
```

---

## 9. Distributed Lock (Redisson)

```java
// Prevent concurrent processing of the same account
@Component @RequiredArgsConstructor
public class DistributedLockService {

    private final RedissonClient redissonClient;

    public <T> T withLock(String lockKey, Duration timeout, Supplier<T> action) {
        RLock lock = redissonClient.getLock("lock:" + lockKey);
        try {
            if (!lock.tryLock(timeout.toMillis(), timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                throw new ConcurrentModificationException("Could not acquire lock for: " + lockKey);
            }
            return action.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock acquisition interrupted", ex);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}

// Usage:
public TransactionResponse transfer(UUID fromId, UUID toId, BigDecimal amount) {
    // Lock both accounts in consistent order (prevents deadlock)
    String lockKey = Stream.of(fromId, toId).sorted().map(UUID::toString).collect(Collectors.joining(":"));
    return lockService.withLock(lockKey, Duration.ofSeconds(10), () -> doTransfer(fromId, toId, amount));
}
```

---

## Critical Rules

1. **Circuit Breaker fallback must never silently drop data** — queue, return pending, or propagate the error.
2. **Never retry non-idempotent operations** without idempotency keys — double payments are catastrophic.
3. **Saga compensation must be logged at ERROR level** with enough context for manual reconciliation.
4. **Outbox poller must be idempotent** — if a message is already published, skip (don't re-publish).
5. **CQRS read model is eventually consistent** — document the lag SLA; never read from write model in queries.
6. **Always lock accounts in a consistent order** (e.g., sorted UUID) to prevent distributed deadlocks.
7. **Idempotency keys must expire** (24h default) — unbounded storage will grow indefinitely.
8. **Circuit breaker + retry order matters** — apply retry first, circuit breaker wraps retry.
9. **Bulkhead per downstream service** — one slow service must not starve threads for all others.
10. **Test resilience patterns with Chaos Monkey for Spring Boot** (`chaos-monkey-spring-boot` dependency).
