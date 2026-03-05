---
name: spring-rest-api
description: |
  **Spring Boot REST API Builder**: Production-grade REST controller, DTO, validation, and OpenAPI scaffolding for Java 21 + Spring Boot 3.x. Use whenever the user wants to add or modify HTTP endpoints, request/response objects, input validation, Swagger docs, versioning, or global error handling.

  MANDATORY TRIGGERS: @RestController, @RequestMapping, @GetMapping, @PostMapping, @PutMapping, @PatchMapping, @DeleteMapping, REST endpoint, DTO, record, ProblemDetail, @Valid, @Validated, @NotNull, @NotBlank, ResponseEntity, SpringDoc, OpenAPI, Swagger, @Operation, @Tag, API versioning, path variable, request param, request body, HTTP status, controller advice, @RestControllerAdvice, GlobalExceptionHandler.
---

# Spring Boot 3.x — REST API Skill

You are building REST APIs for a **Java 21 / Spring Boot 3.3+ banking platform** that uses:
- Spring Web MVC (servlet stack, virtual-thread executor)
- Lombok (`@Slf4j`, `@RequiredArgsConstructor`)
- Java records for DTOs
- Bean Validation (`jakarta.validation`)
- SpringDoc OpenAPI 2.x (`springdoc-openapi-starter-webmvc-ui`)
- RFC 9457 `ProblemDetail` for errors
- Gradle (Groovy DSL)

Every generated file must be **commit-ready** and pass a principal engineer's code review.

---

## Package Structure

```
com.banking.platform.{domain}
├── controller/          ← @RestController  (HTTP only, no business logic)
├── service/             ← @Service, @Transactional
├── repository/          ← Spring Data interfaces
├── model/
│   ├── entity/          ← JPA / Mongo entities
│   ├── dto/             ← Java records (request + response)
│   └── event/           ← Kafka event payloads
├── mapper/              ← MapStruct interfaces
└── exception/           ← Custom exceptions
```

---

## Controller Template

```java
package com.banking.platform.account.controller;

import com.banking.platform.account.model.dto.AccountResponse;
import com.banking.platform.account.model.dto.CreateAccountRequest;
import com.banking.platform.account.model.dto.UpdateAccountRequest;
import com.banking.platform.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/accounts")          // Versioned prefix — matches server.servlet.context-path=/api
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account management operations")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "List accounts", description = "Returns a paginated list of accounts for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<AccountResponse>> listAccounts(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("account.list page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(accountService.listAccounts(pageable));
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account by ID")
    @ApiResponse(responseCode = "200", description = "Account found")
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AccountResponse> getAccount(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId) {
        log.info("account.get accountId={}", accountId);
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }

    @PostMapping
    @Operation(summary = "Create a new account")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "409", description = "Account already exists")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        log.info("account.create type={}", request.accountType());
        AccountResponse created = accountService.createAccount(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.accountId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{accountId}")
    @Operation(summary = "Partially update an account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        log.info("account.update accountId={}", accountId);
        return ResponseEntity.ok(accountService.updateAccount(accountId, request));
    }

    @DeleteMapping("/{accountId}")
    @Operation(summary = "Close an account")
    @ApiResponse(responseCode = "204", description = "Account closed successfully")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> closeAccount(@PathVariable UUID accountId) {
        log.info("account.close accountId={}", accountId);
        accountService.closeAccount(accountId);
        return ResponseEntity.noContent().build();
    }
}
```

---

## DTO Templates (Java Records)

```java
// Request DTO — always validate fields
package com.banking.platform.account.model.dto;

import com.banking.platform.account.model.entity.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Request body for creating a new bank account")
public record CreateAccountRequest(

    @NotBlank(message = "Account type is required")
    @Schema(description = "Type of account", example = "CHECKING", allowableValues = {"CHECKING", "SAVINGS"})
    String accountType,

    @NotNull(message = "Initial deposit is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "Initial deposit must be positive")
    @Digits(integer = 12, fraction = 2, message = "Invalid monetary amount format")
    @Schema(description = "Initial deposit amount", example = "500.00")
    BigDecimal initialDeposit,

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO 4217 code")
    @Schema(description = "ISO 4217 currency code", example = "USD")
    String currency
) {}

// Response DTO — immutable, never expose entity internals
@Schema(description = "Bank account details")
public record AccountResponse(
    @Schema(description = "Unique account identifier")
    UUID accountId,

    @Schema(description = "Human-readable account number")
    String accountNumber,

    String accountType,
    BigDecimal balance,
    String currency,
    String status,
    java.time.Instant createdAt
) {}
```

---

## Global Exception Handler

```java
package com.banking.platform.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        log.warn("resource.not_found message={}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleConflict(DuplicateResourceException ex) {
        log.warn("resource.duplicate message={}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Conflict");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> violations = new java.util.LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> violations.put(e.getField(), e.getDefaultMessage()));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setTitle("Invalid Request");
        pd.setProperty("violations", violations);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("unhandled.exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setTitle("Internal Server Error");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
```

---

## Custom Exceptions

```java
// Base
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found with id: " + id);
    }
}

// Domain-specific
public class AccountNotFoundException extends ResourceNotFoundException {
    public AccountNotFoundException(UUID accountId) {
        super("Account", accountId);
    }
}

public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) { super(message); }
}

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(UUID accountId, BigDecimal required, BigDecimal available) {
        super("Insufficient funds in account %s: required %s, available %s"
                .formatted(accountId, required, available));
    }
}
```

---

## SpringDoc OpenAPI Configuration

```java
package com.banking.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankingPlatformOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking Platform API")
                        .description("Core banking services — accounts, transactions, ledger, payments")
                        .version("1.0.0")
                        .contact(new Contact().name("Platform Team").email("platform@banking.com"))
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token obtained from the /auth/token endpoint")));
    }
}
```

```yaml
# application.yml additions
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true                 # Disable in prod: springdoc.swagger-ui.enabled=false
    operations-sorter: method
    tags-sorter: alpha
  show-actuator: false
  default-produces-media-type: application/json
```

---

## API Versioning Strategy

```
URL versioning (default for this project):
  /api/v1/accounts
  /api/v2/accounts   ← breaking changes only

When to bump version:
  - Removing a field from a response
  - Changing a field's type or semantics
  - Removing an endpoint
  - Changing required → optional validation

When NOT to bump:
  - Adding new optional fields to responses
  - Adding new optional request parameters
  - New endpoints in the same domain
```

---

## HTTP Status Code Rules

| Scenario | Code | Notes |
|----------|------|-------|
| Successful read / update | `200 OK` | |
| Resource created | `201 Created` | Always include `Location` header |
| Async accepted | `202 Accepted` | Return task/job ID |
| Delete with no body | `204 No Content` | |
| Validation failure | `400 Bad Request` | Include field-level violations |
| Missing/invalid JWT | `401 Unauthorized` | |
| Valid JWT, wrong role | `403 Forbidden` | |
| Entity not found | `404 Not Found` | |
| Duplicate / state conflict | `409 Conflict` | |
| Business rule violation | `422 Unprocessable Entity` | e.g. insufficient funds |
| Rate limited | `429 Too Many Requests` | Include `Retry-After` |
| Unexpected server error | `500 Internal Server Error` | Never leak stack trace |

---

## Critical Rules

1. **No business logic in controllers** — delegate everything to `@Service`.
2. **`@Valid` on every `@RequestBody`** — without it Bean Validation is silently skipped.
3. **Return `ResponseEntity<Void>` for 204 responses**, not `ResponseEntity<?>`.
4. **Always set `Location` header** on 201 Created responses.
5. **Use `ProblemDetail`** (RFC 9457) — never custom error envelope classes.
6. **Never expose JPA entities** in controller methods — always map to DTOs.
7. **Log at INFO** for business events, **WARN** for 4xx, **ERROR** for 5xx.
8. **Never log passwords, tokens, card numbers, or PII** — mask or omit.
9. **Use constructor injection** (`@RequiredArgsConstructor` + `final` fields) — no `@Autowired`.
10. **Version every API from day one** — `/v1/` prefix on all `@RequestMapping` values.
