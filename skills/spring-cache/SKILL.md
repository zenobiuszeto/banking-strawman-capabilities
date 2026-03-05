---
name: spring-cache
description: |
  **Spring Cache Skill**: Production-grade caching with Redis and Spring Cache abstraction for Java 21 + Spring Boot 3.x. Covers @Cacheable, @CachePut, @CacheEvict, multi-level caching, cache-aside pattern, TTL configuration, cache warming, conditional caching, distributed cache with Lettuce, cache serialization, key generation, monitoring with Micrometer, and cache stampede prevention.

  MANDATORY TRIGGERS: @Cacheable, @CachePut, @CacheEvict, @Caching, @EnableCaching, CacheManager, RedisCacheManager, CacheConfiguration, RedisTemplate, Spring Cache, Redis, Lettuce, cache, TTL, cache eviction, cache hit, cache miss, cache invalidation, cache stampede, dogpile, multi-level cache, L1 cache, L2 cache, Caffeine, local cache, distributed cache, cache-aside, read-through, write-through, cache warming, preload, cache key, KeyGenerator, cache serialization, Jackson2JsonRedisSerializer, cache monitoring, cache metrics.
---

# Spring Cache Skill — Redis · Multi-Level · Cache-Aside

You are implementing caching for the **Java 21 / Spring Boot 3.3+ banking platform** using:
- **Redis** (Lettuce client) as L2 distributed cache via `RedisCacheManager`
- **Caffeine** as L1 in-process cache (optional, for ultra-hot data)
- **Spring Cache abstraction** (`@Cacheable`, `@CachePut`, `@CacheEvict`)
- **Micrometer** cache metrics → Prometheus → Grafana

---

## Redis Cache Configuration

```java
package com.banking.platform.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    /** Default TTL — override per cache in cacheConfigurations() */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        var defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer()))
                .disableCachingNullValues()       // Never cache null — avoids null-masking real errors
                .prefixCacheNameWith("banking:"); // Key prefix: banking:accounts::<key>

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations(defaultConfig))
                .transactionAware()              // Cache writes only committed after TX commit
                .build();
    }

    /**
     * Per-cache TTL overrides — tune based on data volatility.
     * Format: cacheConfigurations.put("cacheName", defaultConfig.entryTtl(Duration))
     */
    private Map<String, RedisCacheConfiguration> cacheConfigurations(RedisCacheConfiguration base) {
        return Map.of(
            CacheNames.ACCOUNTS,       base.entryTtl(Duration.ofMinutes(15)),   // Account details
            CacheNames.TRANSACTIONS,   base.entryTtl(Duration.ofMinutes(5)),    // Recent transactions
            CacheNames.EXCHANGE_RATES, base.entryTtl(Duration.ofHours(1)),      // Forex rates
            CacheNames.USER_PROFILES,  base.entryTtl(Duration.ofMinutes(30)),   // User preferences
            CacheNames.PRODUCT_CONFIG, base.entryTtl(Duration.ofHours(6))       // Static config
        );
    }

    /** Jackson serializer with type information — required for correct deserialization */
    private Jackson2JsonRedisSerializer<Object> jsonSerializer() {
        var mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .activateDefaultTyping(
                    LaissezFaireSubTypeValidator.instance,
                    ObjectMapper.DefaultTyping.NON_FINAL,
                    JsonTypeInfo.As.PROPERTY   // Stores class name in JSON — required for polymorphism
                );
        return new Jackson2JsonRedisSerializer<>(mapper, Object.class);
    }
}
```

```java
// Cache name constants — single source of truth
public final class CacheNames {
    public static final String ACCOUNTS       = "accounts";
    public static final String TRANSACTIONS   = "transactions";
    public static final String EXCHANGE_RATES = "exchange-rates";
    public static final String USER_PROFILES  = "user-profiles";
    public static final String PRODUCT_CONFIG = "product-config";
    private CacheNames() {}
}
```

---

## application.yml — Redis & Cache

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 16     # Max connections in pool
          max-idle: 8
          min-idle: 2
          max-wait: 1000ms   # Fail fast if pool exhausted — don't queue indefinitely
        shutdown-timeout: 100ms

  cache:
    type: redis
    redis:
      time-to-live: 600000    # 10 min default (ms) — overridden per-cache in CacheConfig
      cache-null-values: false
      key-prefix: "banking:"
      use-key-prefix: true
```

---

## Service-Layer Caching Patterns

### Pattern 1 — Cache-Aside (most common)

```java
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository  accountRepository;
    private final AccountMapper      accountMapper;

    // ── CACHE ASIDE: Read ────────────────────────────────────────────────────
    @Cacheable(
        value     = CacheNames.ACCOUNTS,
        key       = "#accountId",
        condition = "#accountId != null",    // Guard — don't cache null keys
        unless    = "#result == null"        // Don't cache null results
    )
    public AccountResponse getAccount(UUID accountId) {
        log.info("cache.miss accounts accountId={}", accountId);   // Only logs on miss
        return accountRepository.findById(accountId)
                .map(accountMapper::toResponse)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    // ── CACHE ASIDE: Write ────────────────────────────────────────────────────
    @Transactional
    @CachePut(                        // Updates cache WITH the new value (avoids stale reads)
        value = CacheNames.ACCOUNTS,
        key   = "#result.accountId()"
    )
    public AccountResponse createAccount(CreateAccountRequest request) {
        var entity = accountMapper.toEntity(request);
        entity.setAccountNumber(generateAccountNumber());
        var saved = accountRepository.save(entity);
        log.info("cache.put accounts accountId={}", saved.getId());
        return accountMapper.toResponse(saved);
    }

    // ── CACHE ASIDE: Invalidate ────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = CacheNames.ACCOUNTS, key = "#accountId")
    public AccountResponse updateAccount(UUID accountId, UpdateAccountRequest request) {
        // Cache evicted on method exit — next read will reload from DB
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        accountMapper.updateEntity(request, account);
        return accountMapper.toResponse(accountRepository.save(account));
    }

    @Transactional
    @CacheEvict(value = CacheNames.ACCOUNTS, key = "#accountId")
    public void closeAccount(UUID accountId) {
        // ... business logic
    }

    // ── MULTIPLE CACHE OPERATIONS ──────────────────────────────────────────────
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheNames.ACCOUNTS,     key = "#accountId"),
        @CacheEvict(value = CacheNames.TRANSACTIONS, key = "#accountId + ':recent'")
    })
    public void processTransfer(UUID accountId, TransferRequest req) {
        // Evict both caches — transaction affects both account balance and tx history
    }
}
```

### Pattern 2 — Conditional Caching

```java
// Only cache accounts with ACTIVE status — don't cache transitional states
@Cacheable(
    value     = CacheNames.ACCOUNTS,
    key       = "#accountId",
    condition = "#accountId != null",
    unless    = "#result == null || #result.status() != 'ACTIVE'"
)
public AccountResponse getAccount(UUID accountId) { ... }

// Cache only if the result has a large page (small pages aren't worth caching)
@Cacheable(
    value  = CacheNames.TRANSACTIONS,
    key    = "'list:' + #accountId + ':p' + #pageable.pageNumber",
    unless = "#result.content.size() < 5"
)
public Page<TransactionResponse> listTransactions(UUID accountId, Pageable pageable) { ... }
```

---

## Multi-Level Caching (L1 Caffeine + L2 Redis)

```java
package com.banking.platform.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Two-tier cache:
 *   L1 — Caffeine (in-process, nanosecond reads) — for ultra-hot, small datasets
 *   L2 — Redis (distributed, millisecond reads) — for all other cached data
 *
 * Use @Cacheable(cacheManager = "caffeineCacheManager") for L1
 * Use @Cacheable (default) for L2
 */
@Configuration
public class MultiLevelCacheConfig {

    @Bean
    public CacheManager caffeineCacheManager() {
        var manager = new CaffeineCacheManager(
            CacheNames.EXCHANGE_RATES,   // High read, infrequent write — perfect for L1
            CacheNames.PRODUCT_CONFIG
        );
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1_000)           // Bounded — prevent heap pressure
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats());              // Enable Micrometer metrics
        return manager;
    }
}

// Usage:
@Cacheable(value = CacheNames.EXCHANGE_RATES, cacheManager = "caffeineCacheManager")
public ExchangeRate getExchangeRate(String from, String to) { ... }
```

---

## Cache Warming / Preloading

```java
package com.banking.platform.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Warm critical caches on startup to avoid cold-start latency.
 * Runs asynchronously after ApplicationReady to avoid blocking startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmer {

    private final ExchangeRateService exchangeRateService;
    private final ProductConfigService productConfigService;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmCaches() {
        log.info("cache.warm.start");
        try {
            // Warm exchange rates (called millions of times per day)
            exchangeRateService.getAllRates().forEach(rate ->
                exchangeRateService.getExchangeRate(rate.from(), rate.to())
            );

            // Warm product config (static, rarely changes)
            productConfigService.getAllProducts();

            log.info("cache.warm.complete");
        } catch (Exception ex) {
            log.error("cache.warm.failed — continuing with cold cache", ex);
            // Never block startup due to cache warm failure
        }
    }
}
```

---

## Cache Stampede Prevention (Probabilistic Early Expiration)

```java
package com.banking.platform.cache;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Prevents cache stampede (thundering herd) when a hot key expires.
 * Uses probabilistic early recomputation — a small % of requests recompute
 * slightly before expiry, so the cache is refreshed before it expires for everyone.
 * Based on: https://cseweb.ucsd.edu/~avattani/papers/cache_stampede.pdf
 */
@Component
@RequiredArgsConstructor
public class StampedeProtectedCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final double BETA = 1.0;

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Duration ttl, Supplier<T> loader) {
        String metaKey = key + ":meta";
        Long storedExpiry = (Long) redisTemplate.opsForValue().get(metaKey);
        T value = (T) redisTemplate.opsForValue().get(key);

        if (value != null && storedExpiry != null) {
            long now = System.currentTimeMillis();
            double noise = -BETA * Math.log(Math.random()) * ttl.toMillis() * 0.1;
            if (now < storedExpiry - noise) {
                return value;   // Return cached value (not yet expired)
            }
        }

        // Recompute
        T fresh = loader.get();
        long expiry = System.currentTimeMillis() + ttl.toMillis();
        redisTemplate.opsForValue().set(key, fresh, ttl);
        redisTemplate.opsForValue().set(metaKey, expiry, ttl);
        return fresh;
    }
}
```

---

## Cache Monitoring with Micrometer

```java
// Auto-configured by Spring Boot + Micrometer — no extra code needed
// Access metrics:
//   GET /api/actuator/metrics/cache.gets?tag=name:accounts&tag=result:hit
//   GET /api/actuator/metrics/cache.gets?tag=name:accounts&tag=result:miss
//   GET /api/actuator/metrics/cache.puts?tag=name:accounts
//   GET /api/actuator/metrics/cache.evictions?tag=name:accounts

// Grafana query (Prometheus) — hit ratio for accounts cache:
//   sum(rate(cache_gets_total{name="accounts",result="hit"}[5m]))
//   / sum(rate(cache_gets_total{name="accounts"}[5m]))

// Alert: hit ratio drops below 80% → investigate feeder or TTL configuration
```

```yaml
# Alert rule (prometheus/alerts.yml)
- alert: CacheHitRateLow
  expr: |
    sum(rate(cache_gets_total{result="hit"}[5m])) by (name)
    / sum(rate(cache_gets_total[5m])) by (name) < 0.80
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Cache hit rate below 80% for {{ $labels.name }}"
```

---

## Custom Key Generation

```java
@Bean
public KeyGenerator accountKeyGenerator() {
    return (target, method, params) -> {
        // Build key: {className}:{methodName}:{param0}:{param1}
        var builder = new StringBuilder();
        builder.append(target.getClass().getSimpleName()).append(":");
        builder.append(method.getName()).append(":");
        for (Object param : params) {
            builder.append(param).append(":");
        }
        return builder.toString();
    };
}

// Usage
@Cacheable(value = CacheNames.TRANSACTIONS, keyGenerator = "accountKeyGenerator")
public Page<TransactionResponse> listTransactions(UUID accountId, Pageable pageable) { ... }
```

---

## Redis Key Design

```
# Pattern: {prefix}:{cacheName}::{key}
banking:accounts::3f2a1b4c-...       ← Single entity by UUID
banking:transactions::acc-123:p0     ← Paginated list
banking:exchange-rates::USD:EUR      ← Composite key
banking:product-config::all          ← Singleton config

# Rules:
# - Always use meaningful keys — avoid numeric IDs alone
# - Include version in key if schema changes: banking:v2:accounts::...
# - Use : as separator, :: before the key value (Spring convention)
# - Keep keys under 100 chars — Redis has no hard limit but long keys waste memory
```

---

## Critical Rules

1. **`@EnableCaching` must be on a `@Configuration` class** — not on `@SpringBootApplication`.
2. **Never cache mutable state** — only cache read-only snapshots (DTOs/records, not entities).
3. **`disableCachingNullValues()`** in config — never cache null; it masks bugs and causes confusion.
4. **Always set `unless = "#result == null"`** on `@Cacheable` for extra null-safety.
5. **Use `@CachePut` on create/update** — ensures cache is fresh without an extra read round-trip.
6. **Use `@CacheEvict` on update/delete** when you can't produce the new cached value immediately.
7. **Set TTL per cache** — not a global TTL — based on data volatility.
8. **Use `@Transactional` + `transactionAware()` RedisCacheManager** — cache writes roll back if TX fails.
9. **Monitor hit ratio in Grafana** — a hit ratio below 80% signals TTL too short or key explosion.
10. **Protect hot keys from stampede** — use probabilistic early expiration or distributed locking for critical keys.
