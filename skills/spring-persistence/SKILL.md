---
name: spring-persistence
description: |
  **Spring Data Persistence Skill**: Production-grade JPA entities, Spring Data repositories, Flyway migrations, query methods, projections, specifications, and MongoDB/Redis integration for Java 21 + Spring Boot 3.x. Use whenever the user wants to add or modify database models, write queries, create migrations, configure datasources, or integrate MongoDB or Redis.

  MANDATORY TRIGGERS: @Entity, @Table, @Column, @ManyToOne, @OneToMany, @ManyToMany, JPA, Hibernate, Spring Data JPA, JpaRepository, @Query, JPQL, native query, Specification, Criteria API, Flyway, migration, V1__, schema, datasource, HikariCP, connection pool, @Document, MongoRepository, MongoDB, @Cacheable, Redis, @EnableCaching, @Transactional, @Lock, optimistic locking, @Version, fetch type, N+1, EntityGraph, Pageable, Page, Slice, projection.
---

# Spring Persistence Skill — JPA · Flyway · MongoDB · Redis

You are building persistence layers for a **Java 21 / Spring Boot 3.3+ banking platform** using:
- **PostgreSQL 15** via Spring Data JPA (Hibernate 6) + HikariCP
- **Flyway 10** for schema migrations
- **MongoDB** via Spring Data MongoDB (for event/audit stores)
- **Redis** via Spring Cache + Lettuce for distributed caching
- **Lombok** for entity boilerplate
- **MapStruct** for entity ↔ DTO mapping

Every generated file must be **commit-ready** and safe to run in production.

---

## JPA Entity Template

```java
package com.banking.platform.account.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "accounts",
    indexes = {
        @Index(name = "idx_accounts_user_id", columnList = "user_id"),
        @Index(name = "idx_accounts_account_number", columnList = "account_number", unique = true),
        @Index(name = "idx_accounts_status", columnList = "status")
    }
)
// NEVER use @Data on JPA entities — breaks Hibernate proxies and equals/hashCode contracts
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Account {

    @Id
    @UuidGenerator                          // Hibernate 6 native UUID generation (no sequence overhead)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Enumerated(EnumType.STRING)            // Always STRING — never ORDINAL (breaks on reorder)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "user_id", nullable = false)
    private UUID userId;                    // Store as FK value — join via service, not @ManyToOne across domains

    @Version                                // Optimistic locking — prevents lost updates without DB locks
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // One-to-many within the SAME bounded context only
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    // Business-key equals/hashCode — NEVER use @EqualsAndHashCode with @Entity
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account other)) return false;
        return accountNumber != null && accountNumber.equals(other.accountNumber);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
```

---

## Enums

```java
// Always define DB-safe enums as String
public enum AccountType   { CHECKING, SAVINGS, MONEY_MARKET, CD }
public enum AccountStatus { ACTIVE, FROZEN, CLOSED, PENDING_VERIFICATION }
```

---

## Spring Data Repository

```java
package com.banking.platform.account.repository;

import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.model.entity.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID>,
        JpaSpecificationExecutor<Account> {

    // Derived query — simple, readable, zero JPQL
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByUserIdAndStatus(UUID userId, AccountStatus status);
    boolean existsByAccountNumber(String accountNumber);

    // JPQL — use when derived query becomes unreadable
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.balance >= :minBalance")
    Page<Account> findByUserIdWithMinBalance(
            @Param("userId") UUID userId,
            @Param("minBalance") BigDecimal minBalance,
            Pageable pageable);

    // Pessimistic lock — use for balance updates to prevent concurrent overwrites
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    // Projection — avoid loading the full entity when only a subset of fields is needed
    @Query("SELECT a.id as id, a.accountNumber as accountNumber, a.balance as balance FROM Account a WHERE a.userId = :userId")
    List<AccountSummary> findSummariesByUserId(@Param("userId") UUID userId);

    // Count by status for reporting
    long countByStatus(AccountStatus status);
}
```

### Projection Interface

```java
// Closed projection — only the fields declared here are loaded from DB
public interface AccountSummary {
    UUID getId();
    String getAccountNumber();
    java.math.BigDecimal getBalance();
}
```

---

## JPA Specification (Dynamic Filtering)

```java
package com.banking.platform.account.repository;

import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.model.entity.AccountStatus;
import com.banking.platform.account.model.entity.AccountType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.UUID;

public final class AccountSpecification {

    private AccountSpecification() {}

    public static Specification<Account> hasUserId(UUID userId) {
        return (root, query, cb) ->
                userId == null ? cb.conjunction() : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Account> hasStatus(AccountStatus status) {
        return (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Account> hasType(AccountType type) {
        return (root, query, cb) ->
                type == null ? cb.conjunction() : cb.equal(root.get("accountType"), type);
    }

    public static Specification<Account> balanceAtLeast(BigDecimal min) {
        return (root, query, cb) ->
                min == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("balance"), min);
    }
}

// Usage in service:
// Specification<Account> spec = where(hasUserId(userId))
//         .and(hasStatus(status))
//         .and(hasType(type));
// return accountRepository.findAll(spec, pageable);
```

---

## Flyway Migrations

### Naming Convention — CRITICAL
```
src/main/resources/db/migration/
  V1__create_applications_tables.sql
  V2__create_accounts_tables.sql
  V3__create_transactions_table.sql
  ...
  V{N}__{description_in_snake_case}.sql

Rules:
  - Sequential integer versions — never skip, never reuse
  - NEVER edit a committed migration — create a new one instead
  - Descriptions use double underscore __ separator
  - Always test rollback path in dev before merging
```

### Migration Template

```sql
-- V11__add_interest_rate_to_accounts.sql
-- Purpose: Add interest_rate column to support savings account APY calculations
-- Author: platform-team | Date: 2026-03-04
-- Rollback: V11__rollback_interest_rate.sql (if needed)

BEGIN;

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS interest_rate NUMERIC(5, 4) DEFAULT 0.0000 NOT NULL;

COMMENT ON COLUMN accounts.interest_rate IS 'Annual interest rate (APR) as a decimal, e.g. 0.0450 = 4.50%';

-- Backfill: savings accounts get a default rate
UPDATE accounts SET interest_rate = 0.0450 WHERE account_type = 'SAVINGS';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_interest_rate
    ON accounts (interest_rate)
    WHERE account_type = 'SAVINGS';   -- Partial index — only the relevant rows

COMMIT;
```

### application.yml — Flyway config

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false        # true only for existing DBs being brought under Flyway
    out-of-order: false               # Enforce sequential versioning
    validate-on-migrate: true         # Fail fast if checksum mismatch
    table: flyway_schema_history      # Default — leave as-is
```

---

## MongoDB Document Template

```java
package com.banking.platform.audit.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Document(collection = "audit_events")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuditEvent {

    @Id
    private String id;                              // MongoDB ObjectId as String

    @Indexed
    @Field("correlation_id")
    private UUID correlationId;

    @Indexed
    @Field("entity_type")
    private String entityType;                      // e.g. "Account", "Transaction"

    @Indexed
    @Field("entity_id")
    private UUID entityId;

    @Field("action")
    private String action;                          // e.g. "CREATED", "UPDATED", "CLOSED"

    @Field("actor_id")
    private UUID actorId;                           // Who performed the action

    @Field("payload")
    private Map<String, Object> payload;            // Before/after state snapshot

    @CreatedDate
    @Indexed(expireAfterSeconds = 7_776_000)        // TTL: 90 days auto-purge
    @Field("created_at")
    private Instant createdAt;
}
```

### MongoDB Repository

```java
public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {
    List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);
    Page<AuditEvent> findByActorId(UUID actorId, Pageable pageable);
}
```

---

## Redis Caching

```java
package com.banking.platform.account.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)             // Default read-only — override per write method
public class AccountService {

    private static final String CACHE_NAME = "accounts";

    @Cacheable(value = CACHE_NAME, key = "#accountId", unless = "#result == null")
    public AccountResponse getAccount(UUID accountId) {
        log.info("account.get.db accountId={}", accountId);     // Only logs on cache miss
        return accountRepository.findById(accountId)
                .map(accountMapper::toResponse)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Transactional
    @CachePut(value = CACHE_NAME, key = "#result.accountId()")
    public AccountResponse createAccount(CreateAccountRequest request) {
        // ... create logic
    }

    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "#accountId")
    public void closeAccount(UUID accountId) {
        // ... close logic
    }
}
```

```yaml
# application.yml — Redis / Cache config
spring:
  cache:
    type: redis
    redis:
      time-to-live: 600000    # 10 minutes default TTL (ms)
      cache-null-values: false
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2
```

---

## MapStruct Mapper

```java
package com.banking.platform.account.mapper;

import com.banking.platform.account.model.dto.AccountResponse;
import com.banking.platform.account.model.dto.CreateAccountRequest;
import com.banking.platform.account.model.entity.Account;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,   // Fail build if unmapped fields exist
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface AccountMapper {

    @Mapping(target = "accountId", source = "id")
    AccountResponse toResponse(Account account);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)   // Generated in service
    @Mapping(target = "balance", ignore = true)
    @Mapping(target = "status", constant = "PENDING_VERIFICATION")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "transactions", ignore = true)
    Account toEntity(CreateAccountRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateAccountRequest request, @MappingTarget Account account);
}
```

---

## N+1 Query Prevention

```java
// BAD: triggers N+1 if transactions are accessed in a loop
List<Account> accounts = accountRepository.findAll();
accounts.forEach(a -> a.getTransactions().size());  // N+1!

// GOOD: use @EntityGraph or JPQL JOIN FETCH
@EntityGraph(attributePaths = {"transactions"})
List<Account> findAllWithTransactions();

// GOOD: JPQL with JOIN FETCH
@Query("SELECT DISTINCT a FROM Account a LEFT JOIN FETCH a.transactions WHERE a.userId = :userId")
List<Account> findByUserIdWithTransactions(@Param("userId") UUID userId);

// GOOD: Use projections when you don't need child collections
List<AccountSummary> findSummariesByUserId(UUID userId);
```

---

## Critical Rules

1. **Never use `@Data` on JPA entities** — use `@Getter @Setter @Builder @NoArgsConstructor`.
2. **Always use `@Enumerated(EnumType.STRING)`** — never `ORDINAL`.
3. **Every foreign key column must have a database index** — document in `@Table(indexes = ...)`.
4. **Never edit a committed Flyway migration** — always create a new version.
5. **Use `@Version` for optimistic locking** on any entity updated concurrently.
6. **Use `@Transactional(readOnly = true)`** as the class-level default; override per write method.
7. **Never expose JPA entities across domain boundaries** — always map to DTOs.
8. **Use `SELECT ... FOR UPDATE`** (`@Lock(PESSIMISTIC_WRITE)`) for financial balance updates.
9. **Avoid `FetchType.EAGER`** — it silently loads data you don't need.
10. **Test all migrations with `Testcontainers + PostgreSQL`** before merging to main.
