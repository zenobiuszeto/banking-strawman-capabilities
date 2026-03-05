---
name: spring-gatling
description: |
  **Gatling Performance Testing Skill**: Production-grade load, stress, soak, and spike test simulations using Gatling 3.x Java DSL for the banking platform. Covers scenario design, feeders, session variables, assertions, HTML report analysis, Gradle integration, and CI gate configuration.

  MANDATORY TRIGGERS: Gatling, performance test, load test, stress test, soak test, spike test, simulation, scenario, feeder, ramp users, constant users, throughput, response time, percentile, p95, p99, assertions, gatlingRun, GatlingPlugin, io.gatling.gradle, ChainBuilder, ScenarioBuilder, PopulationBuilder, HttpProtocol, exec, pause, repeat, foreach, doIf, check, jsonPath, status, ElFileBody, StringBody, CSV feeder, JSON feeder, gatling-charts, Grafana, InfluxDB, baseline, regression, SmokeTestSimulation, LoadTestSimulation, StressTestSimulation.
---

# Gatling Performance Testing Skill — Java DSL

You are writing performance tests for the **banking platform** using Gatling 3.10+ (Java DSL) integrated via the `io.gatling.gradle` plugin. Tests run against a live environment (staging or dedicated perf env) — never against production.

Project layout:
```
gatling/
└── src/gatling/java/
    ├── simulations/
    │   ├── SmokeTestSimulation.java
    │   ├── LoadTestSimulation.java
    │   ├── StressTestSimulation.java
    │   └── SoakTestSimulation.java
    └── scenarios/
        ├── AccountScenarios.java
        ├── TransactionScenarios.java
        └── AuthScenarios.java
    └── feeders/
        └── TestDataFeeders.java
```

---

## Gradle Setup

```groovy
// build.gradle
plugins {
    id 'io.gatling.gradle' version '3.10.1'
}

gatling {
    // JVM tuning for the load generator itself
    jvmArgs = [
        '-Xmx1g',
        '-XX:+UseZGC',
        '-Dfile.encoding=UTF-8'
    ]
    // System properties injected from Gradle
    systemProperties = [
        'baseUrl'   : System.getProperty('gatling.baseUrl', 'http://localhost:8080'),
        'rampUsers' : System.getProperty('gatling.rampUsers', '10'),
        'duration'  : System.getProperty('gatling.duration', '60'),
        'simulation': System.getProperty('gatling.simulation', 'simulations.SmokeTestSimulation')
    ]
}

// Run: ./gradlew gatlingRun -Dgatling.baseUrl=https://staging.bankingplatform.com
```

---

## HTTP Protocol (Shared Base)

```java
package simulations;

import io.gatling.javaapi.http.HttpProtocolBuilder;
import static io.gatling.javaapi.http.HttpDsl.*;

public final class HttpConfig {

    public static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");

    public static HttpProtocolBuilder httpProtocol() {
        return http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .acceptEncodingHeader("gzip, deflate")
            .userAgentHeader("Gatling/BankingPlatform")
            // Inject Bearer token from session variable (set by auth scenario)
            .authorizationHeader("Bearer #{token}")
            .check(status().not(500))       // Fail fast on any 5xx at protocol level
            .shareConnections();            // Realistic browser-like connection pooling
    }

    private HttpConfig() {}
}
```

---

## Feeders (Test Data)

```java
package feeders;

import io.gatling.javaapi.core.FeederBuilder;
import java.util.UUID;
import java.util.stream.Stream;
import static io.gatling.javaapi.core.CoreDsl.*;

public final class TestDataFeeders {

    // CSV feeder — data/users.csv must be in src/gatling/resources/
    public static FeederBuilder<String> userFeeder() {
        return csv("users.csv").circular();  // circular = never exhausted
    }

    // Random UUID feeder for creating accounts
    public static FeederBuilder<Object> accountFeeder() {
        return Stream.generate(() ->
            java.util.Map.of(
                "accountType",    randomAccountType(),
                "currency",       "USD",
                "initialDeposit", 500 + (int)(Math.random() * 9500)  // 500-10000
            )
        ).<Object>iterator()
         .asFeeder()
         .circular();
    }

    private static String randomAccountType() {
        String[] types = {"CHECKING", "SAVINGS"};
        return types[(int)(Math.random() * types.length)];
    }

    private TestDataFeeders() {}
}
```

`src/gatling/resources/users.csv`:
```csv
username,email,password
user1,user1@bank.com,Test@1234
user2,user2@bank.com,Test@1234
user3,user3@bank.com,Test@1234
```

---

## Reusable Scenarios (ChainBuilder)

```java
package scenarios;

import io.gatling.javaapi.core.ChainBuilder;
import java.time.Duration;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public final class AccountScenarios {

    /** POST /api/v1/accounts — saves accountId to session */
    public static ChainBuilder createAccount() {
        return exec(
            http("Create Account")
                .post("/api/v1/accounts")
                .body(StringBody("""
                    {
                      "accountType": "#{accountType}",
                      "initialDeposit": #{initialDeposit},
                      "currency": "#{currency}"
                    }
                    """))
                .check(
                    status().is(201),
                    jsonPath("$.accountId").saveAs("accountId"),  // Store for later steps
                    responseTimeInMillis().lte(500)               // Soft SLA check
                )
        ).pause(Duration.ofMillis(200), Duration.ofMillis(800));  // Think time
    }

    /** GET /api/v1/accounts/{accountId} */
    public static ChainBuilder getAccount() {
        return exec(
            http("Get Account")
                .get("/api/v1/accounts/#{accountId}")
                .check(
                    status().is(200),
                    jsonPath("$.balance").exists(),
                    responseTimeInMillis().lte(200)
                )
        ).pause(Duration.ofMillis(100), Duration.ofMillis(400));
    }

    /** GET /api/v1/accounts with pagination */
    public static ChainBuilder listAccounts() {
        return exec(
            http("List Accounts")
                .get("/api/v1/accounts?page=0&size=20&sort=createdAt,desc")
                .check(
                    status().is(200),
                    jsonPath("$.content").exists()
                )
        );
    }

    private AccountScenarios() {}
}
```

```java
package scenarios;

import io.gatling.javaapi.core.ChainBuilder;
import java.time.Duration;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public final class TransactionScenarios {

    public static ChainBuilder createTransaction() {
        return exec(
            http("Create Transaction")
                .post("/api/v1/transactions")
                .body(StringBody("""
                    {
                      "accountId": "#{accountId}",
                      "amount": 50.00,
                      "currency": "USD",
                      "type": "DEBIT",
                      "description": "Gatling load test"
                    }
                    """))
                .check(
                    status().in(201, 422),    // 422 = insufficient funds (acceptable)
                    responseTimeInMillis().lte(1000)
                )
        ).pause(Duration.ofMillis(300), Duration.ofSeconds(1));
    }
}
```

---

## Smoke Test Simulation

```java
package simulations;

import feeders.TestDataFeeders;
import io.gatling.javaapi.core.*;
import scenarios.AccountScenarios;
import scenarios.TransactionScenarios;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Smoke Test — runs with 1 user for 60s.
 * Purpose: verify the API is up and basic flows work before heavier tests.
 * CI trigger: runs on every staging deploy in cd.yml
 */
public class SmokeTestSimulation extends Simulation {

    ScenarioBuilder scenario = scenario("Smoke Test")
        .feed(TestDataFeeders.accountFeeder())
        .exec(AccountScenarios.createAccount())
        .exec(AccountScenarios.getAccount())
        .exec(AccountScenarios.listAccounts())
        .exec(TransactionScenarios.createTransaction());

    {
        setUp(
            scenario.injectOpen(atOnceUsers(1))
        )
        .protocols(HttpConfig.httpProtocol())
        .assertions(
            global().responseTime().percentile(95).lt(2000),  // p95 < 2s
            global().successfulRequests().percent().gt(99.0)  // >99% success
        );
    }
}
```

---

## Load Test Simulation

```java
package simulations;

import feeders.TestDataFeeders;
import io.gatling.javaapi.core.*;
import scenarios.AccountScenarios;
import scenarios.TransactionScenarios;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Load Test — sustained expected production load.
 * Duration: 10 min ramp + 20 min steady state.
 * Target: 200 concurrent users, ~500 req/s.
 * Run: ./gradlew gatlingRun -Dgatling.simulation=simulations.LoadTestSimulation
 *              -Dgatling.baseUrl=https://perf.bankingplatform.com
 */
public class LoadTestSimulation extends Simulation {

    private static final int  RAMP_USERS    = Integer.parseInt(System.getProperty("rampUsers", "200"));
    private static final long RAMP_SECS     = 600;   // 10 min ramp-up
    private static final long SUSTAINED_SECS = 1200; // 20 min steady

    // Read-heavy scenario (70% of traffic)
    ScenarioBuilder reads = scenario("Read Traffic")
        .feed(TestDataFeeders.accountFeeder())
        .exec(AccountScenarios.createAccount())
        .repeat(5).on(AccountScenarios.getAccount())
        .exec(AccountScenarios.listAccounts());

    // Write scenario (30% of traffic)
    ScenarioBuilder writes = scenario("Write Traffic")
        .feed(TestDataFeeders.accountFeeder())
        .exec(AccountScenarios.createAccount())
        .exec(TransactionScenarios.createTransaction());

    {
        setUp(
            reads.injectOpen(
                rampUsers((int)(RAMP_USERS * 0.7)).during(Duration.ofSeconds(RAMP_SECS)),
                constantUsersPerSec((RAMP_USERS * 0.7) / 10).during(Duration.ofSeconds(SUSTAINED_SECS))
            ),
            writes.injectOpen(
                rampUsers((int)(RAMP_USERS * 0.3)).during(Duration.ofSeconds(RAMP_SECS)),
                constantUsersPerSec((RAMP_USERS * 0.3) / 10).during(Duration.ofSeconds(SUSTAINED_SECS))
            )
        )
        .protocols(HttpConfig.httpProtocol())
        .assertions(
            // Hard gates — fail the build if breached
            global().responseTime().percentile(95).lt(500),   // p95 < 500ms
            global().responseTime().percentile(99).lt(2000),  // p99 < 2s
            global().responseTime().max().lt(10_000),         // max < 10s
            global().successfulRequests().percent().gt(99.5), // >99.5% success
            // Per-request type gate
            details("Create Account").responseTime().percentile(95).lt(800),
            details("Get Account").responseTime().percentile(95).lt(200)
        );
    }
}
```

---

## Stress Test Simulation

```java
package simulations;

import feeders.TestDataFeeders;
import io.gatling.javaapi.core.*;
import scenarios.AccountScenarios;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Stress Test — find the breaking point.
 * Ramps users beyond expected peak until the system degrades.
 * Run ONLY in isolated perf environment — NOT on staging.
 */
public class StressTestSimulation extends Simulation {

    ScenarioBuilder scenario = scenario("Stress Test")
        .feed(TestDataFeeders.accountFeeder())
        .exec(AccountScenarios.createAccount())
        .exec(AccountScenarios.getAccount());

    {
        setUp(
            scenario.injectOpen(
                rampUsersPerSec(1).to(50).during(Duration.ofMinutes(2)),    // ramp to 50 rps
                rampUsersPerSec(50).to(200).during(Duration.ofMinutes(5)),   // ramp to 200 rps
                rampUsersPerSec(200).to(500).during(Duration.ofMinutes(5)),  // push to breaking
                constantUsersPerSec(500).during(Duration.ofMinutes(5))       // hold at peak
            )
        )
        .protocols(HttpConfig.httpProtocol())
        .assertions(
            // More lenient — we expect some degradation
            global().successfulRequests().percent().gt(95.0),
            global().responseTime().percentile(99).lt(5000)
        );
    }
}
```

---

## Soak Test Simulation

```java
package simulations;

import feeders.TestDataFeeders;
import io.gatling.javaapi.core.*;
import scenarios.AccountScenarios;
import scenarios.TransactionScenarios;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Soak Test — detect memory leaks, connection pool exhaustion, thread leaks.
 * Runs at moderate load for an extended period (2–8 hours).
 * Run: ./gradlew gatlingRun -Dgatling.simulation=simulations.SoakTestSimulation
 *              -Dgatling.duration=14400  (4 hours in seconds)
 */
public class SoakTestSimulation extends Simulation {

    private static final long DURATION_SECS = Long.parseLong(System.getProperty("duration", "14400"));

    ScenarioBuilder scenario = scenario("Soak Test")
        .feed(TestDataFeeders.accountFeeder())
        .exec(AccountScenarios.createAccount())
        .exec(AccountScenarios.getAccount())
        .exec(TransactionScenarios.createTransaction());

    {
        setUp(
            scenario.injectOpen(
                rampUsers(50).during(Duration.ofMinutes(5)),
                constantUsersPerSec(5).during(Duration.ofSeconds(DURATION_SECS))
            )
        )
        .protocols(HttpConfig.httpProtocol())
        .assertions(
            global().responseTime().percentile(95).lt(800),
            global().successfulRequests().percent().gt(99.9),
            // Response time must not degrade over time — compare first vs last 20%
            global().responseTime().percentile(95).lt(800)
        );
    }
}
```

---

## GitHub Actions Integration

```yaml
# In .github/workflows/cd.yml — after staging deploy
- name: Run Gatling smoke test
  run: |
    ./gradlew gatlingRun \
      -Dgatling.simulation=simulations.SmokeTestSimulation \
      -Dgatling.baseUrl=https://staging.bankingplatform.com \
      --no-daemon

- name: Upload Gatling HTML report
  uses: actions/upload-artifact@v4
  if: always()
  with:
    name: gatling-smoke-report-${{ github.sha }}
    path: build/reports/gatling/
    retention-days: 30
```

---

## Reading the HTML Report

| Metric | Green | Yellow | Red |
|--------|-------|--------|-----|
| p50 | < 200ms | 200–500ms | > 500ms |
| p95 | < 500ms | 500ms–2s | > 2s |
| p99 | < 2s | 2s–5s | > 5s |
| Error rate | < 0.1% | 0.1–1% | > 1% |
| Throughput | > target | 80–100% | < 80% |

**Response time distribution** — look for a bimodal distribution (two humps) which indicates cache misses or connection pool saturation on one code path.

---

## Critical Rules

1. **Never run load/stress tests against production** — use a dedicated perf environment or staging.
2. **Smoke test runs on every staging deploy** (in CD pipeline) — catch regressions early.
3. **Set hard assertions** — `global().successfulRequests().percent().gt(99.5)` fails the build.
4. **Use `circular()` feeders** — so tests never run out of data mid-run.
5. **Add think-time (`pause()`)** between requests — real users don't hammer APIs with 0ms latency.
6. **Use session variables** (`saveAs`) to chain requests — create then read the same entity.
7. **Separate scenarios by read/write ratio** — mirror actual production traffic distribution.
8. **Archive HTML reports as CI artifacts** with retention — compare runs over time.
9. **Watch memory on the load generator**, not just the service — use `-Xmx1g` for Gatling JVM.
10. **Correlate Gatling results with Grafana dashboards** (JVM metrics, DB connection pool, Kafka lag) to pinpoint the bottleneck.
