# Security Reference — Spring Security, OAuth2, JWT, mTLS, Vault

## Overview

Every service in the enterprise platform must implement authentication and authorization. This reference covers the full security stack: Spring Security, OAuth2 Resource Server, JWT validation, method-level authorization, mTLS for service-to-service calls, audit logging, and secret management with HashiCorp Vault.

---

## 1. Spring Security Dependency Setup

```groovy
// build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
    // For service-to-service OAuth2 client calls
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
    testImplementation 'org.springframework.security:spring-security-test'
}
```

---

## 2. OAuth2 Resource Server Configuration

Configure every customer-facing or internal API as an OAuth2 Resource Server that validates JWTs issued by your identity provider (Okta, Keycloak, Azure AD, etc.).

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Slf4j
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)           // REST APIs — CSRF not needed
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("OPS")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");     // match your IdP claim
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }

    // CORS — tighten in UAT/prod via properties
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("${app.security.allowed-origins}"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OAUTH2_ISSUER_URI}   # e.g. https://your-idp.com/realms/myrealm
          # jwk-set-uri: alternative if issuer discovery is not available

app:
  security:
    allowed-origins: "https://*.yourdomain.com"
```

---

## 3. JWT Claims Extraction — Custom Principal

Create a typed security context object instead of working with raw JWT claims:

```java
public record AuthenticatedUser(
    String userId,
    String email,
    Set<String> roles,
    String tenantId
) {}

@Component
public class SecurityContextHelper {

    public AuthenticatedUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return new AuthenticatedUser(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                Set.copyOf(jwtAuth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(a -> a.replace("ROLE_", ""))
                    .toList()),
                jwt.getClaimAsString("tenant_id")
            );
        }
        throw new IllegalStateException("No authenticated user in context");
    }
}
```

---

## 4. Method-Level Authorization

Use `@PreAuthorize` for fine-grained control:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.token.subject")
    public AccountDto getAccount(String userId, String accountId) { ... }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteAccount(String accountId) { ... }

    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public TransactionDto createTransaction(String accountId, TransactionRequest request) { ... }
}
```

---

## 5. Service-to-Service Authentication (mTLS + Client Credentials)

For internal service communication, use either mTLS or OAuth2 Client Credentials:

```java
// OAuth2 client credentials approach
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient internalServiceClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
            new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Client.setDefaultClientRegistrationId("internal-service");

        return WebClient.builder()
            .filter(oauth2Client)
            .filter(correlationIdFilter())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    private ExchangeFilterFunction correlationIdFilter() {
        return (request, next) -> {
            String traceId = MDC.get("traceId");
            return next.exchange(ClientRequest.from(request)
                .header("X-Correlation-ID", traceId != null ? traceId : UUID.randomUUID().toString())
                .build());
        };
    }
}
```

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          internal-service:
            client-id: ${SERVICE_CLIENT_ID}
            client-secret: ${SERVICE_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: internal.read,internal.write
        provider:
          internal-service:
            token-uri: ${OAUTH2_TOKEN_URI}
```

---

## 6. Audit Logging

All state-changing operations must emit an audit event:

```java
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditEventPublisher auditPublisher;
    private final SecurityContextHelper securityHelper;

    @AfterReturning(
        pointcut = "@annotation(auditAction)",
        returning = "result")
    public void logAuditEvent(JoinPoint jp, AuditAction auditAction, Object result) {
        try {
            AuthenticatedUser user = securityHelper.getCurrentUser();
            auditPublisher.publish(AuditEvent.builder()
                .userId(user.userId())
                .tenantId(user.tenantId())
                .action(auditAction.value())
                .resource(jp.getSignature().getDeclaringTypeName())
                .method(jp.getSignature().getName())
                .timestamp(Instant.now())
                .traceId(MDC.get("traceId"))
                .build());
        } catch (Exception e) {
            log.error("Audit logging failed — not blocking operation", e);
        }
    }
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAction {
    String value();
}
```

Usage:
```java
@AuditAction("ACCOUNT_CREATED")
public AccountDto createAccount(CreateAccountRequest request) { ... }
```

---

## 7. Secrets Management with HashiCorp Vault

```groovy
// build.gradle
implementation 'org.springframework.cloud:spring-cloud-starter-vault-config'
```

```yaml
# application.yml
spring:
  cloud:
    vault:
      uri: ${VAULT_URI:http://localhost:8200}
      authentication: KUBERNETES          # or TOKEN for local dev
      kubernetes:
        role: ${VAULT_ROLE}
        service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token
      kv:
        enabled: true
        default-context: ${spring.application.name}
        backend: secret
```

Vault secrets are injected as standard `@Value` or `@ConfigurationProperties` — no code changes needed when rotating secrets.

---

## 8. Security Testing

```java
@WebMvcTest(AccountController.class)
class AccountControllerSecurityTest {

    @Autowired MockMvc mockMvc;

    @Test
    void unauthenticatedRequest_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/123"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCanReadOwnAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/123"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts"))
            .andExpect(status().isForbidden());
    }
}
```

---

## 9. Security Checklist for Every Service

- [ ] JWT validation configured with correct `issuer-uri`
- [ ] All actuator endpoints locked down except `health`
- [ ] No secrets in `application.yml` (Vault or env vars only)
- [ ] `@PreAuthorize` on all sensitive service methods
- [ ] Audit logging on all state-changing operations
- [ ] Correlation ID propagated on all outbound calls
- [ ] PII/card data never logged
- [ ] HTTPS enforced in UAT and prod
- [ ] Security tests cover 401, 403, and happy path
