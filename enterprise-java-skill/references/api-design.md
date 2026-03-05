# API Design Reference — REST, gRPC, OpenAPI, Versioning, Pagination

## 1. REST Controller Conventions

```java
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Accounts", description = "Account management APIs")
public class AccountController {

    private final AccountService accountService;
    private final SecurityContextHelper securityHelper;

    @GetMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN') or @accountService.isOwner(#accountId, authentication)")
    @Operation(summary = "Get account by ID")
    public ResponseEntity<AccountDto> getAccount(@PathVariable String accountId) {
        log.info("account.get.request accountId={}", accountId);
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @AuditAction("ACCOUNT_CREATED")
    @Operation(summary = "Create new account")
    public ResponseEntity<AccountDto> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        AccountDto created = accountService.createAccount(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{accountId}")
    @AuditAction("ACCOUNT_UPDATED")
    public ResponseEntity<AccountDto> updateAccount(
            @PathVariable String accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        return ResponseEntity.ok(accountService.updateAccount(accountId, request));
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @HasRole("ADMIN")
    @AuditAction("ACCOUNT_DELETED")
    public ResponseEntity<Void> deleteAccount(@PathVariable String accountId) {
        accountService.deleteAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    // Paginated list
    @GetMapping
    public ResponseEntity<PagedResponse<AccountDto>> listAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(accountService.listAccounts(status, pageable));
    }
}
```

## 2. Request/Response DTOs — Use Java Records

```java
// Request DTOs — immutable records with Bean Validation
public record CreateAccountRequest(
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @Email @NotBlank String email,
    @NotNull AccountType accountType,
    @Valid AddressRequest address
) {}

public record AddressRequest(
    @NotBlank String street,
    @NotBlank String city,
    @NotBlank @Size(min = 2, max = 2) String state,
    @NotBlank @Pattern(regexp = "\\d{5}(-\\d{4})?") String zipCode
) {}

// Response DTOs — also records
public record AccountDto(
    String id,
    String firstName,
    String lastName,
    String email,
    AccountType accountType,
    AccountStatus status,
    BigDecimal availableBalance,
    Instant createdAt
) {}
```

## 3. Pagination Envelope

```java
public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast()
        );
    }
}
```

## 4. API Versioning Strategy

- **URL versioning**: `/api/v1/`, `/api/v2/` — preferred for discoverability
- Never remove a version without a deprecation period + consumer notification
- Deprecate via `Deprecation` and `Sunset` HTTP headers:

```java
@GetMapping("/api/v1/accounts/{id}")
@Deprecated
public ResponseEntity<AccountDto> getAccountV1(@PathVariable String id) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Deprecation", "true");
    headers.set("Sunset", "Sat, 31 Dec 2025 23:59:59 GMT");
    headers.set("Link", "</api/v2/accounts/" + id + ">; rel=\"successor-version\"");
    return ResponseEntity.ok().headers(headers).body(accountService.getAccount(id));
}
```

## 5. OpenAPI Configuration

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Account Service API")
                .version("1.0.0")
                .description("Enterprise account management service")
                .contact(new Contact().name("Platform Team").email("platform@yourorg.com")))
            .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
            .components(new Components()
                .addSecuritySchemes("BearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
```

## 6. gRPC Service Implementation

```java
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class AccountGrpcService extends AccountServiceGrpc.AccountServiceImplBase {

    private final AccountService accountService;

    @Override
    public void getAccount(GetAccountRequest request,
                           StreamObserver<GetAccountResponse> responseObserver) {
        try {
            log.info("grpc.account.get accountId={}", request.getAccountId());
            AccountDto account = accountService.getAccount(request.getAccountId());
            GetAccountResponse response = AccountProtoMapper.toProto(account);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (ResourceNotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription(ex.getMessage())
                .withCause(ex)
                .asRuntimeException());
        } catch (Exception ex) {
            log.error("grpc.account.get.failed accountId={}", request.getAccountId(), ex);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Internal server error")
                .asRuntimeException());
        }
    }
}
```

## 7. Idempotency Key Pattern

For POST operations that must not be duplicated (payments, orders):

```java
@PostMapping("/api/v1/payments")
public ResponseEntity<PaymentDto> createPayment(
        @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
        @Valid @RequestBody CreatePaymentRequest request) {
    return ResponseEntity.ok(paymentService.createPayment(idempotencyKey, request));
}

// Service checks for existing result before processing
public PaymentDto createPayment(String idempotencyKey, CreatePaymentRequest request) {
    return idempotencyStore.get(idempotencyKey)
        .orElseGet(() -> {
            PaymentDto result = processPayment(request);
            idempotencyStore.put(idempotencyKey, result, Duration.ofHours(24));
            return result;
        });
}
```

## 8. API Design Checklist

- [ ] All endpoints return proper HTTP status codes (201 for create, 204 for delete, 409 for conflict)
- [ ] Paginated endpoints use `PagedResponse<T>` envelope
- [ ] All request DTOs use `@Valid` and Bean Validation annotations
- [ ] OpenAPI annotations on all public endpoints
- [ ] Idempotency keys on all non-idempotent financial operations
- [ ] Deprecation headers on old API versions
- [ ] Request body size limits configured
- [ ] Rate limiting applied to public-facing endpoints
