# Core Java 21 Reference — Virtual Threads, Records, Sealed Classes, Pattern Matching

## 1. Virtual Threads (Project Loom)

```java
// application.yml — enable virtual threads for Spring MVC
spring:
  threads:
    virtual:
      enabled: true    # Spring Boot 3.2+ — Tomcat uses virtual threads automatically

// For @Async tasks
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public Executor asyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

**When to use virtual threads**: Blocking I/O-heavy workloads (DB queries, REST calls, file I/O). Not beneficial for CPU-bound work.

## 2. Records for DTOs and Value Objects

```java
// Simple DTO record — compact, immutable, auto-generates equals/hashCode/toString
public record AccountSummary(String id, String accountType, BigDecimal balance) {}

// Record with custom validation
public record MoneyAmount(BigDecimal value, Currency currency) {
    public MoneyAmount {   // compact constructor
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("MoneyAmount cannot be negative");
        }
        value = value.setScale(4, RoundingMode.HALF_UP);  // normalize scale
    }

    public MoneyAmount add(MoneyAmount other) {
        if (!this.currency.equals(other.currency))
            throw new IllegalArgumentException("Cannot add different currencies");
        return new MoneyAmount(this.value.add(other.value), this.currency);
    }
}
```

## 3. Sealed Classes for Domain Modeling

```java
// Model payment result as a sealed hierarchy — exhaustive pattern matching
public sealed interface PaymentResult permits PaymentResult.Success, PaymentResult.Failure, PaymentResult.Pending {

    record Success(String transactionId, BigDecimal amount, Instant processedAt) implements PaymentResult {}
    record Failure(String reason, String errorCode, boolean retryable) implements PaymentResult {}
    record Pending(String referenceId, Duration expectedWait) implements PaymentResult {}
}

// Usage — switch expression with exhaustive matching (no default needed)
String message = switch (result) {
    case PaymentResult.Success s -> "Payment processed: " + s.transactionId();
    case PaymentResult.Failure f -> "Payment failed: " + f.reason() + (f.retryable() ? " (retryable)" : "");
    case PaymentResult.Pending p -> "Payment pending, expected in: " + p.expectedWait().toMinutes() + "m";
};
```

## 4. Pattern Matching

```java
// instanceof pattern matching — no cast needed
public void processEvent(Object event) {
    if (event instanceof AccountCreatedEvent ace) {
        log.info("Account created: {}", ace.accountId());
    } else if (event instanceof PaymentProcessedEvent ppe && ppe.amount().compareTo(BigDecimal.valueOf(10000)) > 0) {
        log.warn("Large payment: {} {}", ppe.amount(), ppe.currency());
    }
}

// Switch pattern matching with guards
String classify(AccountType type) {
    return switch (type) {
        case CHECKING -> "transactional";
        case SAVINGS, MONEY_MARKET -> "interest-bearing";
        case CERTIFICATE_OF_DEPOSIT -> "fixed-term";
    };
}
```

## 5. SequencedCollections (Java 21)

```java
// Java 21 — SequencedCollection with defined encounter order
List<Transaction> transactions = new ArrayList<>(txnList);
Transaction first = transactions.getFirst();    // O(1), cleaner than get(0)
Transaction last  = transactions.getLast();     // O(1), cleaner than get(size-1)
transactions.addFirst(newTxn);
transactions.addLast(newTxn);
```

## 6. Structured Concurrency (Preview in Java 21)

```java
// For parallel calls that must all complete or all fail together
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Future<AccountDto> account = scope.fork(() -> accountService.getAccount(accountId));
    Future<List<TransactionDto>> txns = scope.fork(() -> txnService.getRecent(accountId, 10));

    scope.join().throwIfFailed();  // waits for both; throws if either fails

    return new AccountDetailView(account.get(), txns.get());
}
```

## 7. Java 21 Checklist

- [ ] Virtual threads enabled in `application.yml` for Spring Boot 3.2+
- [ ] Records used for DTOs and value objects instead of Lombok @Value classes
- [ ] Sealed interfaces for domain result types (PaymentResult, ValidationResult)
- [ ] Pattern matching switch replaces instanceof chains
- [ ] No raw thread creation — use `Executors.newVirtualThreadPerTaskExecutor()` for async
