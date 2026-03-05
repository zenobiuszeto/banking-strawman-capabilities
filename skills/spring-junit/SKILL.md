---
name: spring-junit
description: |
  **JUnit 5 Testing Skill**: Comprehensive unit, integration, slice, parameterized, and architecture testing for Java 21 + Spring Boot 3.x banking platform. Covers JUnit 5 Jupiter, Mockito 5, AssertJ, @WebMvcTest, @DataJpaTest, @SpringBootTest, Testcontainers, EmbeddedKafka, ArchUnit, JaCoCo, and the TestDataFactory pattern.

  MANDATORY TRIGGERS: JUnit, @Test, @BeforeEach, @AfterEach, @BeforeAll, @AfterAll, @Nested, @ParameterizedTest, @ValueSource, @CsvSource, @MethodSource, @EnumSource, @ExtendWith, Mockito, @Mock, @InjectMocks, @MockBean, @SpyBean, @Captor, given/when/then, assertThat, AssertJ, @WebMvcTest, @DataJpaTest, @SpringBootTest, MockMvc, WebTestClient, Testcontainers, @Container, PostgreSQLContainer, MongoDBContainer, RedisContainer, KafkaContainer, EmbeddedKafka, ArchUnit, JaCoCo, test coverage, TestDataFactory, unit test, integration test, slice test, spy, verify, argument captor, test fixture.
---

# JUnit 5 Testing Skill — Unit · Slice · Integration · Architecture

You are writing tests for the **Java 21 / Spring Boot 3.3+ banking platform** (`com.banking.platform`).

Stack: JUnit 5 Jupiter · Mockito 5 · AssertJ · MockMvc · Testcontainers · ArchUnit · JaCoCo
Coverage gate: **80% line coverage** enforced by JaCoCo in CI.

---

## Test Pyramid & When to Use Each Layer

| Layer | Annotation | Loads Spring? | Speed | Use For |
|-------|-----------|--------------|-------|---------|
| Unit | `@ExtendWith(MockitoExtension.class)` | ❌ | ⚡⚡⚡ | Services, mappers, utils |
| Controller slice | `@WebMvcTest` | Partial | ⚡⚡ | REST endpoints, validation, security |
| JPA slice | `@DataJpaTest` | Partial | ⚡⚡ | Repositories, JPQL, projections |
| Integration | `@SpringBootTest` + Testcontainers | ✅ | ⚡ | Full stack, Flyway, Kafka consumers |
| Architecture | `ArchUnit` | ❌ | ⚡⚡⚡ | Package dependencies, layer rules |

**Rule**: Use the lowest layer that proves correctness. `@SpringBootTest` is heavy — use it sparingly.

---

## Naming Convention

```java
// Pattern: given_{precondition}_when_{action}_then_{expectation}
void given_validRequest_when_createAccount_then_returns201AndPersists() {}
void given_insufficientFunds_when_transfer_then_throwsInsufficientFundsException() {}
void given_expiredToken_when_getAccount_then_returns401() {}
```

---

## Unit Test — Service with Mockito 5

```java
package com.banking.platform.account.service;

import com.banking.platform.account.mapper.AccountMapper;
import com.banking.platform.account.model.dto.CreateAccountRequest;
import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.repository.AccountRepository;
import com.banking.platform.exception.DuplicateResourceException;
import com.banking.platform.exception.InsufficientFundsException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock  AccountRepository accountRepository;
    @Mock  AccountMapper     accountMapper;
    @Captor ArgumentCaptor<Account> accountCaptor;   // Capture what gets saved

    @InjectMocks AccountService accountService;

    @Nested
    @DisplayName("createAccount")
    class CreateAccount {

        @Test
        @DisplayName("given valid request, when createAccount, then saves entity and returns response")
        void given_validRequest_when_createAccount_then_savesEntityAndReturnsResponse() {
            var request  = TestDataFactory.createAccountRequest();
            var entity   = TestDataFactory.account().build();
            var response = TestDataFactory.accountResponse(entity);

            given(accountRepository.existsByAccountNumber(any())).willReturn(false);
            given(accountMapper.toEntity(request)).willReturn(entity);
            given(accountRepository.save(entity)).willReturn(entity);
            given(accountMapper.toResponse(entity)).willReturn(response);

            var result = accountService.createAccount(request);

            assertThat(result).isNotNull()
                    .extracting("currency").isEqualTo("USD");

            // Verify the exact entity saved — use ArgumentCaptor for field-level assertions
            then(accountRepository).should().save(accountCaptor.capture());
            assertThat(accountCaptor.getValue().getStatus().name()).isEqualTo("PENDING_VERIFICATION");
        }

        @Test
        @DisplayName("given duplicate account number, when createAccount, then throws and never saves")
        void given_duplicateAccountNumber_when_createAccount_then_throwsAndNeverSaves() {
            given(accountRepository.existsByAccountNumber(any())).willReturn(true);

            assertThatThrownBy(() -> accountService.createAccount(TestDataFactory.createAccountRequest()))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already exists");

            then(accountRepository).should(never()).save(any());
        }

        @ParameterizedTest(name = "currency={0}")
        @ValueSource(strings = {"USD", "EUR", "GBP", "JPY"})
        @DisplayName("given supported currencies, when createAccount, then succeeds")
        void given_supportedCurrency_when_createAccount_then_succeeds(String currency) {
            var request = new CreateAccountRequest("CHECKING", new BigDecimal("100.00"), currency);
            var entity  = TestDataFactory.account().currency(currency).build();

            given(accountRepository.existsByAccountNumber(any())).willReturn(false);
            given(accountMapper.toEntity(request)).willReturn(entity);
            given(accountRepository.save(entity)).willReturn(entity);
            given(accountMapper.toResponse(entity)).willReturn(TestDataFactory.accountResponse(entity));

            assertThatNoException().isThrownBy(() -> accountService.createAccount(request));
        }

        @ParameterizedTest
        @EnumSource(value = com.banking.platform.account.model.entity.AccountType.class)
        @DisplayName("given any account type, when createAccount, then succeeds")
        void given_anyAccountType_when_createAccount_then_succeeds(
                com.banking.platform.account.model.entity.AccountType type) {
            var entity = TestDataFactory.account().accountType(type).build();
            given(accountRepository.existsByAccountNumber(any())).willReturn(false);
            given(accountMapper.toEntity(any())).willReturn(entity);
            given(accountRepository.save(any())).willReturn(entity);
            given(accountMapper.toResponse(any())).willReturn(TestDataFactory.accountResponse(entity));

            assertThatNoException().isThrownBy(() ->
                    accountService.createAccount(new CreateAccountRequest(type.name(), BigDecimal.TEN, "USD")));
        }
    }

    @Nested
    @DisplayName("transfer")
    class Transfer {

        @Test
        @DisplayName("given insufficient balance, when transfer, then throws InsufficientFundsException")
        void given_insufficientBalance_when_transfer_then_throwsInsufficientFundsException() {
            var from = TestDataFactory.account().balance(new BigDecimal("10.00")).build();
            var to   = TestDataFactory.account().build();

            given(accountRepository.findByIdForUpdate(from.getId())).willReturn(Optional.of(from));
            given(accountRepository.findByIdForUpdate(to.getId())).willReturn(Optional.of(to));

            assertThatThrownBy(() ->
                    accountService.transfer(from.getId(), to.getId(), new BigDecimal("500.00")))
                    .isInstanceOf(InsufficientFundsException.class);

            then(accountRepository).should(never()).save(any());
        }
    }
}
```

---

## Controller Slice Test — @WebMvcTest

```java
package com.banking.platform.account.controller;

import com.banking.platform.TestDataFactory;
import com.banking.platform.account.service.AccountService;
import com.banking.platform.exception.AccountNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@DisplayName("AccountController — HTTP layer")
class AccountControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper mapper;
    @MockBean  AccountService accountService;

    private static final SimpleGrantedAuthority ROLE_USER  = new SimpleGrantedAuthority("ROLE_USER");
    private static final SimpleGrantedAuthority ROLE_ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");

    @Test
    void getAccount_withValidJwt_returns200AndBody() throws Exception {
        var id       = UUID.randomUUID();
        var response = TestDataFactory.accountResponse(TestDataFactory.account().id(id).build());
        given(accountService.getAccount(id)).willReturn(response);

        mockMvc.perform(get("/api/v1/accounts/{id}", id).with(jwt().authorities(ROLE_USER)))
               .andExpect(status().isOk())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.accountId").value(id.toString()))
               .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getAccount_whenNotFound_returns404WithProblemDetail() throws Exception {
        var id = UUID.randomUUID();
        given(accountService.getAccount(id)).willThrow(new AccountNotFoundException(id));

        mockMvc.perform(get("/api/v1/accounts/{id}", id).with(jwt().authorities(ROLE_USER)))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.title").value("Resource Not Found"))
               .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createAccount_withInvalidBody_returns400WithViolations() throws Exception {
        var bad = """
            {"accountType":"","initialDeposit":-1,"currency":"XX"}
            """;
        mockMvc.perform(post("/api/v1/accounts")
                        .with(jwt().authorities(ROLE_USER))
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.violations").isMap())
               .andExpect(jsonPath("$.violations.accountType").isNotEmpty());
    }

    @Test
    void createAccount_withValidBody_returns201WithLocation() throws Exception {
        var request  = TestDataFactory.createAccountRequest();
        var entity   = TestDataFactory.account().build();
        var response = TestDataFactory.accountResponse(entity);
        given(accountService.createAccount(any())).willReturn(response);

        mockMvc.perform(post("/api/v1/accounts")
                        .with(jwt().authorities(ROLE_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(header().exists("Location"))
               .andExpect(jsonPath("$.accountId").isNotEmpty());
    }

    @Test
    void deleteAccount_withUserRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/{id}", UUID.randomUUID())
                        .with(jwt().authorities(ROLE_USER)))
               .andExpect(status().isForbidden());
    }

    @Test
    void deleteAccount_withAdminRole_returns204() throws Exception {
        willDoNothing().given(accountService).closeAccount(any());
        mockMvc.perform(delete("/api/v1/accounts/{id}", UUID.randomUUID())
                        .with(jwt().authorities(ROLE_ADMIN)))
               .andExpect(status().isNoContent());
    }

    @Test
    void anyEndpoint_withNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
               .andExpect(status().isUnauthorized());
    }
}
```

---

## JPA Slice Test — @DataJpaTest

```java
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // Use real PostgreSQL via Testcontainers
@Testcontainers
class AccountRepositoryTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired AccountRepository repo;

    @Test
    void findByAccountNumber_whenExists_returnsAccount() {
        var saved = repo.save(TestDataFactory.account().build());
        assertThat(repo.findByAccountNumber(saved.getAccountNumber()))
                .isPresent()
                .get().extracting("id").isEqualTo(saved.getId());
    }

    @Test
    void findByIdForUpdate_acquiresPessimisticLock() {
        var saved = repo.save(TestDataFactory.account().build());
        assertThat(repo.findByIdForUpdate(saved.getId())).isPresent();
    }
}
```

---

## Parameterized Tests with @MethodSource

```java
@ParameterizedTest(name = "[{index}] amount={0}, expected={1}")
@MethodSource("transferScenarios")
void given_variousAmounts_when_transfer_then_expectedOutcome(
        BigDecimal amount, Class<? extends Throwable> expectedException) {
    if (expectedException != null) {
        assertThatThrownBy(() -> accountService.transfer(fromId, toId, amount))
                .isInstanceOf(expectedException);
    } else {
        assertThatNoException().isThrownBy(() -> accountService.transfer(fromId, toId, amount));
    }
}

private static Stream<Arguments> transferScenarios() {
    return Stream.of(
        Arguments.of(new BigDecimal("50.00"),   null),                          // valid
        Arguments.of(new BigDecimal("999.00"),  InsufficientFundsException.class), // over limit
        Arguments.of(BigDecimal.ZERO,           IllegalArgumentException.class),   // zero
        Arguments.of(new BigDecimal("-10.00"),  IllegalArgumentException.class)    // negative
    );
}
```

---

## Integration Test — Full Stack + Testcontainers

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AccountIntegrationTest {

    // Declare containers STATIC — shared across test methods for speed
    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host",     redis::getHost);
        registry.add("spring.data.redis.port",     () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accountRepository;

    @BeforeEach
    void cleanUp() { accountRepository.deleteAll(); }

    @Test
    void createThenGetAccount_fullRoundTrip_persistsCorrectly() throws Exception {
        // Create
        String body = """
            {"accountType":"CHECKING","initialDeposit":500.00,"currency":"USD"}
            """;
        var createResult = mockMvc.perform(post("/api/v1/accounts")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType("application/json").content(body))
               .andExpect(status().isCreated())
               .andReturn();

        String location = createResult.getResponse().getHeader("Location");
        String accountId = location.substring(location.lastIndexOf('/') + 1);

        // Get
        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.balance").value(500.00))
               .andExpect(jsonPath("$.currency").value("USD"));

        // Assert DB state
        assertThat(accountRepository.count()).isEqualTo(1);
    }
}
```

---

## ArchUnit — Architecture Rules

```java
@AnalyzeClasses(packages = "com.banking.platform")
class BankingPlatformArchTest {

    @ArchTest
    static final ArchRule no_business_logic_in_controllers =
        noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule services_do_not_reference_controllers =
        noClasses().that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule entities_not_in_controller_layer =
        noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..model.entity..");

    @ArchTest
    static final ArchRule no_field_injection =
        noFields().that().areDeclaredInClassesThat().resideInAPackage("com.banking.platform..")
            .should().beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
            .because("Use constructor injection with @RequiredArgsConstructor");

    @ArchTest
    static final ArchRule log_via_slf4j_only =
        noClasses().should().accessClassesThat().haveFullyQualifiedName("java.util.logging.Logger")
            .orShould().accessClassesThat().haveFullyQualifiedName("org.apache.log4j.Logger");
}
```

---

## Test Data Factory (centralised)

```java
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

    public static CreateAccountRequest createAccountRequest() {
        return new CreateAccountRequest("CHECKING", new BigDecimal("500.00"), "USD");
    }

    public static AccountResponse accountResponse(Account a) {
        return new AccountResponse(a.getId(), a.getAccountNumber(),
                a.getAccountType().name(), a.getBalance(), a.getCurrency(),
                a.getStatus().name(), a.getCreatedAt());
    }
}
```

---

## build.gradle — JaCoCo Coverage Gate

```groovy
jacoco {
    toolVersion = "0.8.12"
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true    // Required by SonarQube / CI
        html.required = true
    }
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, excludes: [
                '**/model/entity/**',   // Entities — Lombok-generated, no logic
                '**/model/dto/**',      // Records — no logic
                '**/config/**',         // Spring config beans
                '**/*Application*',     // Main class
                '**/mapper/**'          // MapStruct generated
            ])
        }))
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = 'LINE'
                value   = 'COVEREDRATIO'
                minimum = 0.80          // 80% minimum — fail build if below
            }
        }
    }
}

check.dependsOn jacocoTestCoverageVerification
```

---

## Critical Rules

1. **One assertion concept per test** — split multiple unrelated assertions into separate tests.
2. **Use `TestDataFactory`** — never inline `UUID.randomUUID()` or hardcoded values in test bodies.
3. **Use BDDMockito** (`given/when/then`) — never `when(...).thenReturn(...)` style.
4. **Mark Testcontainers fields `static`** — one container per test class, reused across methods.
5. **`@BeforeEach` cleanup** — always reset DB state between integration tests.
6. **Always test both happy path and error paths** — at minimum: valid, not-found, unauthorized, invalid-input.
7. **Never mock the class under test** — mock its dependencies only.
8. **Use `@Captor` for save-argument verification** — don't just verify `save()` was called; verify what was saved.
9. **Exclude Lombok/MapStruct generated code** from JaCoCo to avoid inflated exclusions.
10. **`@DisplayName` on every class and test** — makes CI test reports readable without reading code.
