---
name: config-management
description: |
  **Config Management Skill**: Comprehensive configuration management for the banking platform using Spring Boot profile hierarchy, @ConfigurationProperties with @Validated, Spring Cloud Consul Config with hot-refresh (@RefreshScope), the actual banking domain config (wire limits, ACH, rewards, feature flags, batch crons, resilience, Kafka), Kubernetes ConfigMap integration, and config best practices.

  MANDATORY TRIGGERS: Spring Cloud Consul, Spring profiles, @ConfigurationProperties, @RefreshScope, Consul KV, config/banking-platform, application.yml, application-kubernetes.yml, application-local.yml, @Value, @EnableConfigurationProperties, config hot-refresh, config reload, Consul watch, bootstrap.yml, spring.config.import, ConfigMap, K8s ConfigMap, env vars, environment variables, banking config, wire limits, feature flags, feature toggle, banking.wire, banking.ach, banking.rewards, banking.features, banking.batch, banking.resilience, config-management, configuration properties, configuration management
---

# Config Management Skill — Banking Platform

This project uses a **layered configuration strategy**: static Spring YAML files for infrastructure defaults, Consul KV for dynamic domain configuration (hot-reloadable without restart), and Kubernetes ConfigMaps for environment-specific overrides.

---

## Configuration Layer Priority (Highest → Lowest)

```
1. Environment variables (K8s ConfigMap envFrom)
2. System properties (-Dkey=value)
3. spring.config.import sources (Consul KV)
4. application-{profile}.yml  (e.g., application-kubernetes.yml)
5. application.yml             (base defaults)
```

When the same key appears in multiple layers, the **highest layer wins**.

---

## Profile Strategy

| Profile      | When Active                          | What it configures                            |
|--------------|--------------------------------------|-----------------------------------------------|
| `local`      | Developer laptop (`-Dspring.profiles.active=local`) | H2/Testcontainers, no auth, verbose logging |
| `test`       | `@SpringBootTest` / `@DataJpaTest`   | Testcontainers DB, mocked external services   |
| `kubernetes` | K8s pod (set in ConfigMap env var)   | Consul, Vault, real DBs, Actuator probes      |
| `production` | Combined with `kubernetes` in prod   | Strict security, reduced logging, full metrics|

```yaml
# In K8s ConfigMap (infra/k8s/configmap.yml):
SPRING_PROFILES_ACTIVE: "kubernetes,production"

# For local dev — use IntelliJ Run Config or:
./gradlew bootRun --args='--spring.profiles.active=local'
```

---

## `@ConfigurationProperties` — The Right Way

Always prefer `@ConfigurationProperties` over `@Value` for structured config. It gives type safety, validation, and IDE autocompletion.

```java
// src/main/java/com/banking/platform/config/BankingProperties.java
@ConfigurationProperties(prefix = "banking")
@Validated
@Getter
@Setter
public class BankingProperties {

    private Wire wire = new Wire();
    private Ach ach = new Ach();
    private Rewards rewards = new Rewards();
    private RateLimits rateLimits = new RateLimits();
    private Features features = new Features();
    private Batch batch = new Batch();

    @Getter @Setter
    public static class Wire {
        @NotNull
        @DecimalMin("0")
        private BigDecimal dailyLimit;

        @NotNull
        @DecimalMin("0")
        private BigDecimal internationalFee;

        @NotNull
        @DecimalMin("0")
        private BigDecimal domesticFee;

        @NotNull
        @DecimalMin("0")
        private BigDecimal highValueThreshold;   // triggers Temporal compliance review
    }

    @Getter @Setter
    public static class Ach {
        @Min(1) @Max(10000)
        private int batchSize;

        @NotNull
        private String sameDayCutoff;            // "14:30" — 2:30 PM Eastern

        @NotNull
        @DecimalMin("0")
        private BigDecimal maxDailyOrigination;
    }

    @Getter @Setter
    public static class Rewards {
        private Tier tier = new Tier();
        private Multipliers multipliers = new Multipliers();

        @Getter @Setter
        public static class Tier {
            private BigDecimal silverThreshold;
            private BigDecimal goldThreshold;
            private BigDecimal platinumThreshold;
        }

        @Getter @Setter
        public static class Multipliers {
            private double bronze;
            private double silver;
            private double gold;
            private double platinum;
        }
    }

    @Getter @Setter
    public static class Features {
        private boolean newRewardsEngine;
        private boolean asyncReportGeneration;
        private boolean webhookDeliveryEnabled;
        private boolean temporalWireWorkflow;     // use Temporal for wire saga
        private boolean virtualThreadsEnabled;
    }

    @Getter @Setter
    public static class RateLimits {
        private int apiPerSecond;
        private int wirePerDay;
        private int achPerDay;
    }

    @Getter @Setter
    public static class Batch {
        private String eodSettlementCron;
        private String interestAccrualCron;
        private String rewardTierReviewCron;
    }
}
```

```java
// src/main/java/com/banking/platform/BankingPlatformApplication.java
@SpringBootApplication
@EnableConfigurationProperties(BankingProperties.class)
public class BankingPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankingPlatformApplication.class, args);
    }
}
```

```java
// Inject and use in services
@Service
@RequiredArgsConstructor
public class WireTransferService {

    private final BankingProperties config;

    public void validateWireAmount(BigDecimal amount, String currency) {
        if (amount.compareTo(config.getWire().getDailyLimit()) > 0) {
            throw new WireLimitExceededException(
                "Amount exceeds daily limit of " + config.getWire().getDailyLimit());
        }
        if (amount.compareTo(config.getWire().getHighValueThreshold()) > 0) {
            // Trigger Temporal compliance workflow
            temporalWorkflowService.startComplianceReview(amount, currency);
        }
    }
}
```

---

## Spring Cloud Consul Config Setup

```yaml
# src/main/resources/application-kubernetes.yml
spring:
  cloud:
    consul:
      host: ${CONSUL_HOST:consul.infra.svc.cluster.local}
      port: 8500
      config:
        enabled: true
        prefix: config                        # reads config/banking-platform/data in Consul KV
        default-context: banking-platform
        data-key: data
        format: YAML
        watch:
          enabled: true
          delay: 1000                         # Poll Consul every 1 second for changes
          wait-time: 55                       # Long-poll wait time in seconds
      discovery:
        enabled: true
        register: true
        instance-id: ${spring.application.name}:${random.value}
        health-check-path: /api/actuator/health
        health-check-interval: 10s
```

```groovy
// build.gradle — Spring Cloud Consul dependency
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-consul-config'
    implementation 'org.springframework.cloud:spring-cloud-starter-consul-discovery'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:2023.0.3"
    }
}
```

---

## Consul KV — Actual Banking Domain Config

This is the live config at path `config/banking-platform/data` in Consul KV:

```yaml
banking:
  # Wire transfer business rules
  wire:
    daily-limit: 50000               # USD — max per-customer daily wire amount
    international-fee: 45.00         # flat fee for international wires
    domestic-fee: 25.00
    high-value-threshold: 10000      # triggers compliance review via Temporal

  # ACH (Automated Clearing House) settings
  ach:
    batch-size: 500                  # transactions per ACH batch file
    same-day-cutoff: "14:30"         # 2:30 PM Eastern — after this, same-day ACH rejected
    max-daily-origination: 250000    # max ACH origination per day

  # Rewards tier thresholds (monthly transaction volume in USD)
  rewards:
    tier:
      silver-threshold: 1000
      gold-threshold: 5000
      platinum-threshold: 20000
    multipliers:
      bronze: 1.0
      silver: 1.25
      gold: 1.5
      platinum: 2.0

  # API and transaction rate limits
  rate-limits:
    api-per-second: 100
    wire-per-day: 10                 # max wires per customer per day
    ach-per-day: 25

  # Feature flags — flip without restart via Consul KV update
  features:
    new-rewards-engine: false        # set true to enable v2 rewards calculation
    async-report-generation: true
    webhook-delivery-enabled: true
    temporal-wire-workflow: true     # use Temporal for wire approval saga
    virtual-threads-enabled: true

  # Scheduled batch job crons (Spring @Scheduled cron expression format)
  batch:
    eod-settlement-cron: "0 0 0 * * ?"      # midnight daily
    interest-accrual-cron: "0 0 1 * * ?"    # 1 AM daily
    reward-tier-review-cron: "0 0 2 1 * ?"  # 2 AM on 1st of each month

  # Resilience4j circuit breaker tuning
  resilience:
    circuit-breaker:
      payment-rails:
        failure-rate-threshold: 50   # % failures before opening circuit
        wait-duration-seconds: 30
    retry:
      payment-rails:
        max-attempts: 3

  # Kafka consumer tuning
  kafka:
    consumer:
      concurrency: 3
      max-poll-records: 500
```

```bash
# Write config to Consul KV (run from the project root)
consul kv put config/banking-platform/data - < infra/consul/banking-platform-config.yml

# Read it back
consul kv get config/banking-platform/data

# Watch for changes (useful for debugging hot-refresh)
consul watch -type=key -key=config/banking-platform/data cat
```

---

## Hot-Refresh with `@RefreshScope`

Beans annotated with `@RefreshScope` are re-created when a `/actuator/refresh` POST is triggered or when Consul watch detects a KV change.

```java
// Annotate any bean that reads config you want to hot-reload
@Service
@RefreshScope                                    // ← re-created on config change
@RequiredArgsConstructor
public class FeatureFlagService {

    private final BankingProperties config;

    public boolean isNewRewardsEngineEnabled() {
        return config.getFeatures().isNewRewardsEngine();
    }

    public boolean isTemporalWireWorkflowEnabled() {
        return config.getFeatures().isTemporalWireWorkflow();
    }
}
```

```java
// @Scheduled beans also need @RefreshScope for cron changes to take effect
@Component
@RefreshScope
@RequiredArgsConstructor
@Slf4j
public class EodSettlementJob {

    private final BankingProperties config;

    @Scheduled(cron = "${banking.batch.eod-settlement-cron}")
    public void runEodSettlement() {
        log.info("Running EOD settlement batch");
        // ... batch logic
    }
}
```

```bash
# Manually trigger config refresh (useful after Consul KV update in non-watch environments)
curl -X POST http://localhost:8080/api/actuator/refresh

# Confirm changed keys in the response:
# ["banking.wire.daily-limit","banking.features.new-rewards-engine"]
```

---

## Kubernetes ConfigMap — Environment Overrides

```yaml
# infra/k8s/configmap.yml
apiVersion: v1
kind: ConfigMap
metadata:
  name: banking-platform-config
  namespace: banking
data:
  SPRING_PROFILES_ACTIVE: "kubernetes,production"
  SPRING_APPLICATION_NAME: "banking-platform"

  # Infrastructure endpoints (non-sensitive — safe in ConfigMap)
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres.infra.svc.cluster.local:5432/banking_db"
  SPRING_DATA_MONGODB_URI: "mongodb://mongo.infra.svc.cluster.local:27017"
  SPRING_DATA_REDIS_HOST: "redis.infra.svc.cluster.local"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "kafka.infra.svc.cluster.local:9092"

  # Consul for dynamic config
  CONSUL_HOST: "consul.infra.svc.cluster.local"

  # Keycloak / OAuth2
  KEYCLOAK_ISSUER_URI: "https://keycloak.infra.svc.cluster.local/realms/banking"

  # Temporal workflow engine
  TEMPORAL_SERVICE_TARGET: "temporal.infra.svc.cluster.local:7233"
```

```yaml
# In deployment.yml — mount ConfigMap as env vars
spec:
  containers:
    - name: banking-platform
      envFrom:
        - configMapRef:
            name: banking-platform-config
        - secretRef:
            name: banking-platform-secrets    # injected by ESO or Vault Agent
```

---

## `@Value` vs `@ConfigurationProperties` Decision Guide

| Use Case                                           | Recommended Approach                |
|----------------------------------------------------|-------------------------------------|
| Single scalar value, used once                     | `@Value("${key}")`                  |
| Group of related properties                        | `@ConfigurationProperties(prefix=…)`|
| Needs `@Validated` JSR-303 validation              | `@ConfigurationProperties` only     |
| Needs hot-refresh via `@RefreshScope`              | Either (both work with `@RefreshScope`) |
| Used in multiple beans                             | `@ConfigurationProperties` bean + inject |
| Feature flags checked in many places               | `@ConfigurationProperties` + `FeatureFlagService` |
| Infrastructure URLs (DB, Redis, Kafka)             | Use Spring Boot's auto-configured properties directly |

```java
// ✅ CORRECT — structured, validated, injectable
@ConfigurationProperties(prefix = "banking.wire")
@Validated
@Getter @Setter
public class WireConfig {
    @NotNull @DecimalMin("0")
    private BigDecimal dailyLimit;
}

// ❌ AVOID for structured config — no validation, no grouping
@Value("${banking.wire.daily-limit}")
private BigDecimal wireDailyLimit;
```

---

## Consul KV Management Commands

```bash
# Set a single value
consul kv put config/banking-platform/data/banking/wire/daily-limit 75000

# Update entire config file atomically
consul kv put config/banking-platform/data @infra/consul/banking-platform-config.yml

# List all keys under a prefix
consul kv get -recurse config/banking-platform/

# Delete a key (use with caution — triggers refresh in all pods)
consul kv delete config/banking-platform/data/banking/features/new-rewards-engine

# Export Consul config to file (for backup or migration)
consul kv get config/banking-platform/data > backup-$(date +%Y%m%d).yml

# Lock a key to prevent accidental changes (Consul sessions)
consul lock config/banking-platform/maintenance ./deploy.sh
```

---

## Environment-Specific Config Files

```
src/main/resources/
├── application.yml               ← Base defaults (all environments)
├── application-local.yml         ← Local dev: H2, no auth, debug logging
├── application-test.yml          ← Test: Testcontainers, mocked externals
└── application-kubernetes.yml    ← K8s: Consul, Vault, real infrastructure
```

```yaml
# application-local.yml
spring:
  datasource:
    url: jdbc:h2:mem:banking_db
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  cloud:
    consul:
      config:
        enabled: false             # Consul not available locally
    vault:
      enabled: false               # Vault not available locally
logging:
  level:
    com.banking: DEBUG
    org.hibernate.SQL: DEBUG
```

```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:tc:postgresql:16:///banking_db   # Testcontainers JDBC URL
  cloud:
    consul:
      config:
        enabled: false
    vault:
      enabled: false
  kafka:
    bootstrap-servers: ${spring.embedded.kafka.brokers}
```

---

## 10 Critical Rules

1. **Never put secrets in `application.yml`** — passwords, API keys, and tokens belong in Vault KV or AWS/GCP Secrets Manager only; ConfigMaps are for non-sensitive infrastructure endpoints.
2. **Always use `@ConfigurationProperties` for groups of 2+ related properties** — never scatter `@Value` annotations across multiple beans for the same domain concept.
3. **Apply `@Validated` with JSR-303 annotations** (`@NotNull`, `@DecimalMin`, `@Min`) to all `@ConfigurationProperties` classes — this surfaces misconfiguration at startup, not at runtime during a financial transaction.
4. **`@RefreshScope` only what needs it** — avoid annotating `@Repository` or `@Component` beans that hold database connections; only annotate beans whose behavior changes based on config values (feature flags, business rule services).
5. **Consul KV changes trigger refresh in all pods simultaneously** — coordinate config changes with a deployment window for high-impact values (wire limits, rate limits); don't update feature flags mid-transaction surge.
6. **Use `format: YAML`** for `spring.cloud.consul.config.format` — this allows hierarchical structured config; `PROPERTIES` format works but loses nesting clarity.
7. **Always set `watch.enabled: true` with `wait-time: 55`** — Consul long-polling prevents thundering-herd refresh storms; setting `delay` lower than 1000ms can overwhelm the Consul agent.
8. **Document units in config** — always add comments specifying units (`# USD`, `# milliseconds`, `# Eastern Time`); bare numbers like `50000` are ambiguous without them.
9. **Test config loading in `@SpringBootTest`** — use `@TestPropertySource(properties = "banking.features.new-rewards-engine=true")` to test feature-flag-gated code paths; never rely on Consul being available in tests.
10. **Version your Consul config** — store `infra/consul/banking-platform-config.yml` in Git and deploy it as part of the CD pipeline; treat config changes with the same review process as code changes.
