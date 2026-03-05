# GitHub Copilot Instructions — Enterprise Java Platform

> Drop this file into `.github/copilot-instructions.md` in any repo and GitHub Copilot
> will automatically apply these instructions to every suggestion in VS Code, JetBrains,
> and the GitHub web editor. No plugin or extension required.
>
> Last updated: 2025 · Stack: Spring Boot 3.3 · Java 21 · Gradle 8

---

## Language & Framework

- **Language**: Java 21. Prefer modern idioms: records for DTOs, sealed interfaces for domain results, pattern matching switch, virtual threads.
- **Framework**: Spring Boot 3.3+. Use Spring Boot 3 APIs — `ProblemDetail` for errors, `spring.threads.virtual.enabled: true` for virtual threads, `@SpringBootTest` with Testcontainers for integration tests.
- **Build**: Gradle 8 with Groovy DSL and a `libs.versions.toml` version catalog. All dependency versions must come from the catalog — never hardcode versions in `build.gradle`.

---

## Architecture Rules (enforced by ArchUnit in tests)

1. **Controllers** (`..controller..`) must NOT import anything from `..repository..`.
2. **Services** (`..service..`) must NOT import anything from `..controller..`.
3. **Repositories** (`..repository..`) are accessed only from services.
4. **No field injection** — never use `@Autowired` on fields. Always use constructor injection via `@RequiredArgsConstructor`.
5. Business logic lives in `..service..` — never in controllers, repositories, or entities.
6. Custom exceptions live in `..exception..` — never throw `RuntimeException` directly.

---

## Code Conventions — Apply to Every File Generated

### Dependency Injection
```java
// CORRECT — constructor injection via Lombok
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {
    private final AccountRepository accountRepository;
    private final AccountEventPublisher eventPublisher;
}

// WRONG — never do this
@Service
public class AccountService {
    @Autowired  // ❌ field injection
    private AccountRepository accountRepository;
}
```

### DTOs — Use Java Records
```java
// Prefer records for simple DTOs on Java 17+
public record CreateAccountRequest(
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @Email @NotBlank String email,
    @NotNull AccountType accountType
) {}

public record AccountDto(String id, String accountType, BigDecimal balance, Instant createdAt) {}
```

### JPA Entities — Never @Data
```java
// CORRECT — explicit annotations, @Version for optimistic locking
@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version  // required — prevents lost updates
    private Long version;

    // Explicit equals/hashCode on business key
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account a)) return false;
        return id != null && id.equals(a.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}

// WRONG — @Data on JPA entities breaks Hibernate proxies
@Data  // ❌
@Entity
public class Account { ... }
```

### Error Handling — RFC 9457 ProblemDetail
```java
// CORRECT — Spring Boot 3 ProblemDetail
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        pd.setProperty("timestamp", Instant.now());
        return ResponseEntity.of(pd).build();
    }
}

// WRONG — custom error envelope (use ProblemDetail instead)
public class ErrorResponse { String message; int status; } // ❌
```

### HTTP Status Codes
| Operation | Status |
|---|---|
| GET (found) | 200 |
| POST (created) | 201 + Location header |
| PUT/PATCH (updated) | 200 |
| DELETE | 204 |
| Validation error | 400 |
| Unauthorized | 401 |
| Forbidden | 403 |
| Not found | 404 |
| Conflict/duplicate | 409 |

### Transactions
```java
@Service
@Transactional(readOnly = true)  // class-level default = read-only
public class AccountService {
    public AccountDto getAccount(String id) { ... }  // uses readOnly

    @Transactional  // override for writes
    public AccountDto createAccount(CreateAccountRequest req) { ... }
}
```

### Logging — Structured, No PII
```java
// CORRECT — key=value pairs, SLF4J, no PII
log.info("account.created accountId={} customerId={} type={}", id, customerId, type);
log.warn("payment.retry attempt={} orderId={}", attempt, orderId);
log.error("kafka.publish.failed topic={} key={}", topic, key, ex);

// WRONG
System.out.println("Account created: " + account);   // ❌ no System.out
log.info("Created account for " + email);             // ❌ PII in logs, string concat
```

---

## Security — Apply to Every Service

Every service is an **OAuth2 Resource Server**. JWTs are validated on every request.

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("OPS")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

Use `@PreAuthorize` for method-level access:
```java
@PreAuthorize("hasRole('ADMIN') or #userId == authentication.token.subject")
public AccountDto getAccount(String userId, String accountId) { ... }
```

Secrets are **never** hardcoded. Always use:
- `${ENV_VAR}` in `application.yml`
- HashiCorp Vault via Spring Cloud Vault in deployed environments

---

## Resilience — Wrap Every External Call

```java
@CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
@Retry(name = "paymentService")
@Bulkhead(name = "paymentService", type = Bulkhead.Type.SEMAPHORE)
public PaymentResponse processPayment(PaymentRequest request) {
    return gatewayClient.charge(request);
}

private PaymentResponse paymentFallback(PaymentRequest request, Throwable ex) {
    log.warn("payment.fallback orderId={} reason={}", request.orderId(), ex.getMessage());
    throw new PaymentUnavailableException("Payment service temporarily unavailable");
}
```

Resilience4j config belongs in `application.yml` — not in code.

---

## Kafka — Idempotent Consumers, Dead Letter

```java
@KafkaListener(topics = "payment.initiated")
public void onPaymentInitiated(@Payload PaymentInitiatedEvent event, Acknowledgment ack) {
    String eventId = event.getEventId().toString();
    if (processedEventRepository.existsByEventId(eventId)) {
        ack.acknowledge(); return;  // already processed — skip
    }
    try {
        paymentService.process(event);
        processedEventRepository.save(ProcessedEvent.of(eventId));
        ack.acknowledge();
    } catch (NonRetryableException ex) {
        ack.acknowledge();  // ack to avoid infinite retry; DLT configured for permanent failures
        throw ex;
    }
}
```

- `enable-auto-commit: false` always — manual acknowledgment only.
- `enable.idempotence: true` and `acks: all` on all producers.
- All Kafka schemas in Avro under `src/main/avro/` registered with Schema Registry.

---

## Testing Standards

| Test Type | Tool | Coverage |
|---|---|---|
| Unit | JUnit 5 + Mockito | Services, mappers, utils |
| Integration | @SpringBootTest + Testcontainers | Full stack with real DB/Kafka/Redis |
| API | MockMvc / WebTestClient | Controller layer |
| Architecture | ArchUnit | Package rules, no field injection |
| Performance | Gatling | Critical paths before UAT |

Naming: `given_<context>_when_<action>_then_<expected>` — e.g. `given_userNotFound_when_getAccount_then_returns404`.

**Minimum 80% line coverage** — enforced by JaCoCo in CI.

---

## CI/CD Pipeline

| Stage | Trigger | What Runs |
|---|---|---|
| CI | Every PR | Build, test, JaCoCo, Checkstyle, SpotBugs, SonarQube |
| Dev deploy | Main merge | Auto Helm deploy, image tagged with Git SHA |
| UAT deploy | Manual dispatch | Gatling load tests → manual approval → Helm deploy |
| Prod deploy | Manual dispatch | Change ticket required → 2-reviewer approval → canary 10% → promote |

- Image tags = **Git SHA**, never `latest`.
- Secrets in **GitHub Environments secrets**, never in workflow YAML.
- Canary deployment to prod — monitor 10 minutes, then promote to 100%.

---

## Database Migrations (Flyway)

- All schema changes via versioned Flyway migrations in `src/main/resources/db/migration/`.
- File naming: `V{n}__{description}.sql` — e.g. `V3__add_transaction_indexes.sql`.
- **Never** use `spring.jpa.hibernate.ddl-auto=create` or `update` in any environment except local.
- `CREATE INDEX` uses `CONCURRENTLY` for tables > 100k rows to avoid locking.
- Never drop a column in one migration — use 3-step process: add new → backfill → drop old.

---

## Kubernetes / Deployment

- Resource `requests` and `limits` set on every container.
- Liveness: `/actuator/health/liveness` — readiness: `/actuator/health/readiness`.
- `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: 30s`.
- `runAsNonRoot: true`, `runAsUser: 1000` in pod security context.
- Secrets injected via `secretKeyRef` — never in ConfigMap.

---

## What to Never Do

| Never | Because |
|---|---|
| `@Autowired` on fields | Breaks testability; use `@RequiredArgsConstructor` |
| `@Data` on JPA entities | Breaks Hibernate proxy equals/hashCode |
| `spring.jpa.open-in-view=true` | Holds DB connections across HTTP lifecycle |
| Return `null` from public methods | Use `Optional<T>` |
| `System.out.println` | Use `@Slf4j` |
| Hardcode secrets | Use `${ENV_VAR}` or Vault |
| `catch (Exception e) {}` swallow | Log and rethrow or handle explicitly |
| Auto-commit Kafka consumers | `enable-auto-commit: false` always |
| `latest` Docker tag in CI | Tag with Git SHA |
| `@Data` with `@Entity` | Hibernate proxy issue |
| Business logic in controllers | Services only |
| `ddl-auto: create` in non-local | Use Flyway migrations |

---

## Quick Reference — Layer Responsibilities

```
HTTP Request
    → Controller          validates input, delegates, returns ResponseEntity
    → Service             business logic, @Transactional, calls repositories and clients
    → Repository          data access, no business logic
    → Entity              state, no logic beyond simple validation
    → Mapper              entity ↔ DTO, no logic
    ← DTO (record)        response
HTTP Response
```

---

*This file is maintained by the Platform Engineering team. PRs welcome.*
