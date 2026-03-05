---
name: spring-testing
description: |
  **Spring Testing Skill**: Production-grade unit tests, integration tests, MockMvc API tests, and Testcontainers-based database/Kafka tests for Java 21 + Spring Boot 3.x. Use whenever the user wants to write tests, set up test infrastructure, verify controller behavior, test service logic in isolation, run integration tests with real databases, or test Kafka consumers/producers.

  MANDATORY TRIGGERS: JUnit 5, @Test, @SpringBootTest, @WebMvcTest, @DataJpaTest, @ExtendWith, Mockito, @Mock, @InjectMocks, MockMvc, @MockBean, Testcontainers, @Container, PostgreSQLContainer, KafkaContainer, MongoDBContainer, given/when/then, assertThat, AssertJ, @BeforeEach, @AfterEach, unit test, integration test, slice test, MockMvcResultMatchers, status().isOk, test coverage, JaCoCo, @ParameterizedTest, @ValueSource, @CsvSource, ArchUnit, consumer-driven contract, Spring Cloud Contract, Pact, test data, TestDataFactory.
---

# Spring Testing Skill — Unit · Integration · MockMvc · Testcontainers

You are writing tests for a **Java 21 / Spring Boot 3.3+ banking platform** using:
- **JUnit 5** + **AssertJ** for assertions
- **Mockito 5** for service unit tests
- **MockMvc** for controller/API slice tests
- **Testcontainers** for PostgreSQL, MongoDB, Redis, and Kafka integration tests
- **@DataJpaTest** for repository-layer tests
- **JaCoCo** coverage gate: **80% minimum**

Tests follow the `given_precondition_when_action_then_expectation` naming convention.

---

## Test Pyramid — What to Write

```
          /\
         /  \
        / E2E\          ← Functional/journey tests (Testcontainers + full app)
       /------\
      /  Integ  \       ← @DataJpaTest, @SpringBootTest slices, Kafka consumer tests
     /------------\
    /  Unit Tests   \   ← Services, mappers, utils — Mockito, no Spring context
   /----------------\

Target ratio: 70% unit / 20% integration / 10% E2E
Coverage gate: 80% line coverage (enforced by JaCoCo in CI)
```

---

## Unit Test — Service Layer

```java
package com.banking.platform.account.service;

import com.banking.platform.account.mapper.AccountMapper;
import com.banking.platform.account.model.dto.AccountResponse;
import com.banking.platform.account.model.dto.CreateAccountRequest;
import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.model.entity.AccountStatus;
import com.banking.platform.account.repository.AccountRepository;
import com.banking.platform.exception.AccountNotFoundException;
import com.banking.platform.exception.DuplicateResourceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)   // No Spring context — pure unit test
@DisplayName("AccountService")
class AccountServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock AccountMapper accountMapper;
    @InjectMocks AccountService accountService;

    @Nested
    @DisplayName("createAccount()")
    class CreateAccount {

        @Test
        @DisplayName("given valid request, when createAccount, then saves and returns response")
        void given_validRequest_when_createAccount_then_savesAndReturnsResponse() {
            // Given
            var request = new CreateAccountRequest("CHECKING", new BigDecimal("500.00"), "USD");
            var entity  = TestDataFactory.account().status(AccountStatus.PENDING_VERIFICATION).build();
            var response = TestDataFactory.accountResponse(entity);

            given(accountRepository.existsByAccountNumber(any())).willReturn(false);
            given(accountMapper.toEntity(request)).willReturn(entity);
            given(accountRepository.save(entity)).willReturn(entity);
            given(accountMapper.toResponse(entity)).willReturn(response);

            // When
            AccountResponse result = accountService.createAccount(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.currency()).isEqualTo("USD");
            then(accountRepository).should(times(1)).save(entity);
        }

        @Test
        @DisplayName("given duplicate account number, when createAccount, then throws DuplicateResourceException")
        void given_duplicateAccountNumber_when_createAccount_then_throwsDuplicateResourceException() {
            // Given
            var request = new CreateAccountRequest("CHECKING", new BigDecimal("500.00"), "USD");
            given(accountRepository.existsByAccountNumber(any())).willReturn(true);

            // When / Then
            assertThatThrownBy(() -> accountService.createAccount(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already exists");

            then(accountRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("getAccount()")
    class GetAccount {

        @Test
        @DisplayName("given existing account, when getAccount, then returns response")
        void given_existingAccount_when_getAccount_then_returnsResponse() {
            var accountId = UUID.randomUUID();
            var entity   = TestDataFactory.account().id(accountId).build();
            var response = TestDataFactory.accountResponse(entity);

            given(accountRepository.findById(accountId)).willReturn(Optional.of(entity));
            given(accountMapper.toResponse(entity)).willReturn(response);

            assertThat(accountService.getAccount(accountId)).isEqualTo(response);
        }

        @Test
        @DisplayName("given missing account, when getAccount, then throws AccountNotFoundException")
        void given_missingAccount_when_getAccount_then_throwsAccountNotFoundException() {
            var accountId = UUID.randomUUID();
            given(accountRepository.findById(accountId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccount(accountId))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }
}
```

---

## Test Data Factory

```java
package com.banking.platform;

import com.banking.platform.account.model.dto.AccountResponse;
import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.model.entity.AccountStatus;
import com.banking.platform.account.model.entity.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Centralised test data factory.
 * Use builder overrides to customise specific fields per test.
 * Never inline UUID.randomUUID() or hardcoded values in test methods.
 */
public final class TestDataFactory {

    private TestDataFactory() {}

    public static Account.AccountBuilder account() {
        return Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("ACC-" + System.nanoTime())
                .accountType(AccountType.CHECKING)
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .userId(UUID.randomUUID())
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now());
    }

    public static AccountResponse accountResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountType().name(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus().name(),
                account.getCreatedAt()
        );
    }
}
```

---

## Controller Slice Test — MockMvc

```java
package com.banking.platform.account.controller;

import com.banking.platform.TestDataFactory;
import com.banking.platform.account.service.AccountService;
import com.banking.platform.exception.AccountNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)   // Only loads the web layer — fast
class AccountControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AccountService accountService;  // Mock the service dependency

    @Test
    void getAccount_withValidJwt_returns200() throws Exception {
        var accountId = UUID.randomUUID();
        var entity    = TestDataFactory.account().id(accountId).build();
        var response  = TestDataFactory.accountResponse(entity);

        given(accountService.getAccount(accountId)).willReturn(response);

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .with(jwt().authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))))
               .andExpect(status().isOk())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.accountId").value(accountId.toString()))
               .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getAccount_whenNotFound_returns404WithProblemDetail() throws Exception {
        var accountId = UUID.randomUUID();
        given(accountService.getAccount(accountId)).willThrow(new AccountNotFoundException(accountId));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .with(jwt().authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.title").value("Resource Not Found"))
               .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createAccount_withInvalidBody_returns400WithViolations() throws Exception {
        String invalidBody = """
            { "accountType": "", "initialDeposit": -100, "currency": "INVALID" }
            """;

        mockMvc.perform(post("/api/v1/accounts")
                        .with(jwt().authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.violations").isMap());
    }

    @Test
    void getAccount_withNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
               .andExpect(status().isUnauthorized());
    }
}
```

---

## Repository Slice Test — @DataJpaTest

```java
package com.banking.platform.account.repository;

import com.banking.platform.TestDataFactory;
import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.model.entity.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest                    // Loads only JPA layer + H2 (or Testcontainers if configured)
@ActiveProfiles("test")
class AccountRepositoryTest {

    @Autowired AccountRepository accountRepository;

    @Test
    void findByAccountNumber_whenExists_returnsAccount() {
        var saved = accountRepository.save(TestDataFactory.account().build());

        var found = accountRepository.findByAccountNumber(saved.getAccountNumber());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void existsByAccountNumber_whenNotExists_returnsFalse() {
        assertThat(accountRepository.existsByAccountNumber("NONEXISTENT")).isFalse();
    }

    @Test
    void findByUserIdAndStatus_returnsOnlyMatchingAccounts() {
        var userId = java.util.UUID.randomUUID();
        accountRepository.save(TestDataFactory.account().userId(userId).status(AccountStatus.ACTIVE).build());
        accountRepository.save(TestDataFactory.account().userId(userId).status(AccountStatus.FROZEN).build());

        var active = accountRepository.findByUserIdAndStatus(userId, AccountStatus.ACTIVE);

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
}
```

---

## Integration Test — Testcontainers

```java
package com.banking.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class AccountIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("banking_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;

    @Test
    void createAccount_persistsToDatabase_andReturns201() throws Exception {
        String body = """
            {"accountType": "CHECKING", "initialDeposit": 500.00, "currency": "USD"}
            """;

        mockMvc.perform(post("/api/v1/accounts")
                        .with(jwt().authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))
                        .contentType("application/json")
                        .content(body))
               .andExpect(status().isCreated())
               .andExpect(header().exists("Location"))
               .andExpect(jsonPath("$.accountId").isNotEmpty());
    }
}
```

---

## Kafka Consumer Integration Test

```java
@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"prod.banking.transaction.created"})
class TransactionEventConsumerTest {

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @MockBean  LedgerService ledgerService;

    @Test
    void onTransactionCreated_callsLedgerService() throws InterruptedException {
        var event = new TransactionCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), new BigDecimal("250.00"), "USD", "DEBIT", Instant.now(), 1
        );

        kafkaTemplate.send("prod.banking.transaction.created", event.accountId().toString(), event);

        // Wait for async processing
        Thread.sleep(2000);

        verify(ledgerService, times(1)).processTransactionEvent(any());
    }
}
```

---

## ArchUnit — Architecture Rules

```java
package com.banking.platform;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class ArchitectureTest {

    private final com.tngtech.archunit.core.domain.JavaClasses classes =
            new ClassFileImporter().importPackages("com.banking.platform");

    @Test
    void services_shouldNotDependOnControllers() {
        noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .check(classes);
    }

    @Test
    void controllers_shouldNotDependOnRepositories() {
        noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .check(classes);
    }

    @Test
    void entities_shouldNotBeUsedInControllers() {
        noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..model.entity..")
            .check(classes);
    }
}
```

---

## Critical Rules

1. **Test naming**: `given_{precondition}_when_{action}_then_{expectation}` — every test.
2. **Use `@WebMvcTest`** for controllers (fast, no full context) and `@DataJpaTest` for repos.
3. **Never use `@SpringBootTest` for unit tests** — it loads the full application context unnecessarily.
4. **Use `TestDataFactory`** — never inline `UUID.randomUUID()` or hardcoded values inside test methods.
5. **Use BDDMockito style**: `given(...).willReturn(...)`, `then(...).should(...)`, not `when/verify`.
6. **Always test the unhappy path** — 404, 400, 403, conflict — not just the happy path.
7. **Mock security with `.with(jwt()...)`** from `spring-security-test` — never disable security in tests.
8. **Use AssertJ** (`assertThat`) — never JUnit `assertEquals`; AssertJ gives better failure messages.
9. **Testcontainers containers must be `static`** — one container per test class, not per test.
10. **Enforce 80% coverage via JaCoCo** in `build.gradle` — fail the build if coverage drops below threshold.
