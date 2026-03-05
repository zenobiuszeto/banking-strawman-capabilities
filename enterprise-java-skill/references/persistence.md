# Persistence Reference — JPA, MongoDB, Redis, Query Optimization

## 1. JPA Entity Best Practices

```java
@Entity
@Table(name = "accounts",
    indexes = {
        @Index(name = "idx_accounts_customer_id", columnList = "customer_id"),
        @Index(name = "idx_accounts_status_created", columnList = "status, created_at")
    })
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
// NEVER use @Data on JPA entities — equals/hashCode interacts badly with Hibernate proxies
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version  // Optimistic locking — prevents lost updates
    private Long version;

    @PrePersist
    void prePersist() { this.createdAt = Instant.now(); }

    @PreUpdate
    void preUpdate() { this.updatedAt = Instant.now(); }

    // Explicit equals/hashCode based on business key (not id for transient entities)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account a)) return false;
        return id != null && id.equals(a.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
```

## 2. Repository Layer

```java
public interface AccountRepository extends JpaRepository<Account, String>,
        JpaSpecificationExecutor<Account> {

    // Prefer projections for read-heavy queries — avoids loading entire entity
    @Query("SELECT new com.yourorg.model.dto.AccountSummary(a.id, a.customerId, a.balance, a.status) " +
           "FROM Account a WHERE a.customerId = :customerId AND a.status = :status")
    List<AccountSummary> findSummariesByCustomerAndStatus(
        @Param("customerId") String customerId,
        @Param("status") AccountStatus status
    );

    // Use @EntityGraph to avoid N+1 for known eager scenarios
    @EntityGraph(attributePaths = {"transactions"})
    Optional<Account> findWithTransactionsById(String id);

    // Paginated search with Specification
    Page<Account> findAll(Specification<Account> spec, Pageable pageable);

    // Update without loading entity — for high-throughput paths
    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance + :delta, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.version = :version")
    int updateBalance(@Param("id") String id,
                      @Param("delta") BigDecimal delta,
                      @Param("version") Long version,
                      @Param("now") Instant now);
}
```

## 3. Service Layer Transaction Management

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)  // default read-only — override per method
public class AccountService {

    private final AccountRepository accountRepository;

    // Reads use readOnly=true (default from class-level)
    public AccountDto getAccount(String id) {
        return accountRepository.findById(id)
            .map(AccountMapper::toDto)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }

    // Writes override readOnly
    @Transactional
    public AccountDto createAccount(CreateAccountRequest request) {
        validateNoDuplicate(request.email());
        Account account = Account.builder()
            .customerId(request.customerId())
            .accountType(request.accountType())
            .balance(BigDecimal.ZERO)
            .status(AccountStatus.ACTIVE)
            .build();
        Account saved = accountRepository.save(account);
        log.info("account.created accountId={} customerId={}", saved.getId(), saved.getCustomerId());
        return AccountMapper.toDto(saved);
    }

    @Transactional
    public void transferFunds(String fromId, String toId, BigDecimal amount) {
        // Lock in consistent order to prevent deadlock
        String firstId = fromId.compareTo(toId) < 0 ? fromId : toId;
        String secondId = firstId.equals(fromId) ? toId : fromId;

        Account from = accountRepository.findById(firstId.equals(fromId) ? fromId : toId)
            .orElseThrow(() -> new ResourceNotFoundException("Account: " + fromId));
        Account to = accountRepository.findById(firstId.equals(toId) ? toId : fromId)
            .orElseThrow(() -> new ResourceNotFoundException("Account: " + toId));

        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient balance in account: " + fromId);
        }
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        // Version-based optimistic locking handles concurrent transfers
    }
}
```

## 4. Redis Caching

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("accounts", defaultConfig.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration("products", defaultConfig.entryTtl(Duration.ofHours(1)))
            .build();
    }
}

@Service
public class AccountService {

    @Cacheable(value = "accounts", key = "#id", unless = "#result == null")
    public AccountDto getAccount(String id) { ... }

    @CacheEvict(value = "accounts", key = "#id")
    @Transactional
    public AccountDto updateAccount(String id, UpdateAccountRequest request) { ... }

    @CachePut(value = "accounts", key = "#result.id()")
    @Transactional
    public AccountDto createAccount(CreateAccountRequest request) { ... }
}
```

## 5. MongoDB Document Design

```java
@Document(collection = "events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDocument {

    @Id
    private String id;

    @Indexed
    private String aggregateId;

    @Indexed
    private String aggregateType;

    private String eventType;
    private Map<String, Object> payload;

    @Indexed
    private Instant occurredAt;

    // TTL index — auto-expire old events after 90 days
    @Indexed(expireAfterSeconds = 7_776_000)
    private Instant expiresAt;
}

public interface EventDocumentRepository extends MongoRepository<EventDocument, String> {

    @Query("{ 'aggregateId': ?0, 'occurredAt': { $gte: ?1 } }")
    List<EventDocument> findByAggregateIdAfter(String aggregateId, Instant after);

    // Aggregation pipeline
    @Aggregation(pipeline = {
        "{ $match: { aggregateType: ?0, occurredAt: { $gte: ?1 } } }",
        "{ $group: { _id: '$eventType', count: { $sum: 1 } } }",
        "{ $sort: { count: -1 } }"
    })
    List<EventTypeCount> countByEventType(String aggregateType, Instant since);
}
```

## 6. Persistence Checklist

- [ ] All entities have `@Version` for optimistic locking on mutable data
- [ ] Indexes documented and created for every query path
- [ ] `readOnly = true` at service class level; write methods override
- [ ] No N+1 queries — use `@EntityGraph` or JOIN FETCH
- [ ] BigDecimal for all monetary values — never double/float
- [ ] Cache eviction matches all write paths
- [ ] Connection pool sized for expected concurrency (HikariCP maxPoolSize = (cores * 2) + disk_spindles)
