---
name: spring-security
description: |
  **Spring Security Skill**: Production-grade authentication and authorization for Java 21 + Spring Boot 3.x. Covers OAuth2 Resource Server, JWT validation, role-based access control (RBAC), method security, custom filters, security configuration, and mTLS setup.

  MANDATORY TRIGGERS: Spring Security, @EnableWebSecurity, SecurityFilterChain, OAuth2, JWT, JwtDecoder, BearerTokenAuthenticationFilter, @PreAuthorize, @Secured, hasRole, hasAuthority, RBAC, authentication, authorization, SecurityContext, UserDetails, PasswordEncoder, BCrypt, Argon2, CSRF, CORS security, HttpSecurity, resource server, OIDC, Keycloak, Okta, mTLS, client certificate, SecurityContextHolder, OncePerRequestFilter, BasicAuth, session management, stateless.
---

# Spring Security Skill — OAuth2 · JWT · RBAC · Filters

You are securing a **Java 21 / Spring Boot 3.3+ banking platform** using:
- **Spring Security 6.x** (Lambda DSL — no deprecated `WebSecurityConfigurerAdapter`)
- **OAuth2 Resource Server** with JWT (stateless — no HTTP sessions)
- **RBAC** via Spring's `@PreAuthorize` + `hasRole` / `hasAuthority`
- Roles: `ADMIN`, `USER`, `AUDITOR`, `SERVICE` (machine-to-machine)
- All endpoints under `/api/**` require authentication unless explicitly permitted

Every security decision must be explicit, documented, and auditable.

---

## Security Configuration

```java
package com.banking.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)   // Enables @PreAuthorize on service/controller methods
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtConverter,
                                           CorrelationIdFilter correlationIdFilter) throws Exception {
        http
            // Stateless — no cookies, no sessions for API clients
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF disabled — safe for stateless JWT APIs (no session cookies)
            .csrf(AbstractHttpConfigurer::disable)

            // CORS — allow configured origins only
            .cors(c -> c.configurationSource(corsConfigurationSource()))

            // Request authorization rules (most restrictive last = default deny)
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no token required
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                // Admin-only operations
                .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // Auditor read-only access
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/**").hasAnyRole("ADMIN", "AUDITOR")

                // Machine-to-machine (service accounts)
                .requestMatchers("/api/v1/internal/**").hasRole("SERVICE")

                // All other API calls require authentication
                .anyRequest().authenticated()
            )

            // JWT Resource Server — validates Bearer token on every request
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
            )

            // Add correlation ID filter before auth filter (for log tracing)
            .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Map JWT "roles" claim to Spring Security GrantedAuthority with ROLE_ prefix
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");   // Custom claim name
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");        // Required for hasRole()

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "https://banking.example.com",
            "https://admin.banking.example.com"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("Location", "X-Correlation-Id"));
        config.setAllowCredentials(false);     // false for Bearer token flows
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

---

## application.yml — JWT Resource Server

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Option A: Auth server JWKS endpoint (auto-rotates keys)
          jwk-set-uri: ${AUTH_SERVER_URL:http://localhost:8180}/realms/banking/protocol/openid-connect/certs

          # Option B: Static public key (simpler for internal services)
          # public-key-location: classpath:keys/public.pem

          issuer-uri: ${AUTH_SERVER_URL:http://localhost:8180}/realms/banking
```

---

## Method-Level Security

```java
// On controller or service methods — prefer service layer for security checks

@PreAuthorize("hasRole('USER')")
public AccountResponse getAccount(UUID accountId) { ... }

// Check ownership — user can only access their own accounts
@PreAuthorize("hasRole('ADMIN') or @accountSecurityService.isOwner(#accountId, authentication)")
public AccountResponse getAccount(UUID accountId) { ... }

// Parameterized authority
@PreAuthorize("hasAuthority('accounts:write')")
public AccountResponse createAccount(CreateAccountRequest request) { ... }

// Multiple roles — OR logic
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public Page<TransactionResponse> listAllTransactions(Pageable pageable) { ... }

// Deny all by default — allow only specific roles
@PreAuthorize("denyAll()")
public void dangerousOperation() { ... }
```

---

## Ownership Check Bean

```java
package com.banking.platform.security;

import com.banking.platform.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("accountSecurityService")
@RequiredArgsConstructor
public class AccountSecurityService {

    private final AccountRepository accountRepository;

    /**
     * Returns true if the authenticated user owns the given account.
     * Used in @PreAuthorize SpEL expressions.
     */
    public boolean isOwner(UUID accountId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        UUID callerUserId = UUID.fromString(jwt.getSubject());
        return accountRepository.findById(accountId)
                .map(account -> account.getUserId().equals(callerUserId))
                .orElse(false);
    }
}
```

---

## Extracting Claims from JWT

```java
package com.banking.platform.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityContextHelper {

    public UUID getCurrentUserId() {
        return UUID.fromString(getJwt().getSubject());
    }

    public String getCurrentUserEmail() {
        return getJwt().getClaimAsString("email");
    }

    public boolean hasRole(String role) {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private Jwt getJwt() {
        var auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        return auth.getToken();
    }
}
```

---

## Correlation ID Filter (Audit Tracing)

```java
package com.banking.platform.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();  // Generate if not provided by caller
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);  // Echo back to caller
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);     // Always clean up MDC to prevent thread-local leaks
        }
    }
}
```

---

## Password Encoding (Internal Users / Admin Accounts)

```java
@Bean
public PasswordEncoder passwordEncoder() {
    // Argon2 preferred for new systems (memory-hard, OWASP recommended)
    return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    // Alternative: BCrypt with strength 12 (minimum for financial systems)
    // return new BCryptPasswordEncoder(12);
}

// Usage:
String encoded = passwordEncoder.encode(rawPassword);
boolean matches = passwordEncoder.matches(rawPassword, encodedPassword);
```

---

## Security Testing

```java
@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerSecurityTest {

    @Autowired MockMvc mockMvc;

    @Test
    void getAccount_withValidJwt_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
               .andExpect(status().isOk());
    }

    @Test
    void getAccount_withNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccount_withUserRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/{id}", UUID.randomUUID())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
               .andExpect(status().isForbidden());
    }

    @Test
    void deleteAccount_withAdminRole_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/{id}", UUID.randomUUID())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
               .andExpect(status().isNoContent());
    }
}
```

---

## Critical Rules

1. **Never use `WebSecurityConfigurerAdapter`** — it's removed in Spring Security 6; use `SecurityFilterChain` beans.
2. **Never disable CSRF without documentation** — it's safe for stateless JWT APIs but must be an explicit decision.
3. **Always set `SessionCreationPolicy.STATELESS`** for JWT resource servers.
4. **Never store JWTs in cookies** without `HttpOnly` + `Secure` + `SameSite=Strict` flags.
5. **Always use `@EnableMethodSecurity`** — never rely on URL patterns alone for authorization.
6. **Never hardcode secrets** — use environment variables or Vault for `jwk-set-uri`, client secrets, etc.
7. **Never log JWT tokens** — they are bearer credentials; treat them like passwords.
8. **Clean up MDC in `finally` blocks** — thread-local leaks cause correlation IDs to bleed across requests.
9. **Use `Argon2` or `BCrypt(12)`** for password hashing — MD5, SHA-1, SHA-256 are NOT acceptable.
10. **Test all security rules explicitly** — both positive (access granted) and negative (access denied) cases.

---

## OAuth2 Flows — Decision Guide

| Scenario | Flow | Grant Type |
|----------|------|-----------|
| User logs in via browser (SPA / Web app) | Authorization Code + PKCE | `authorization_code` |
| Service-to-service (no user) | Client Credentials | `client_credentials` |
| Mobile app | Authorization Code + PKCE | `authorization_code` |
| Refresh an expired token | Refresh Token | `refresh_token` |
| Validate opaque token (legacy) | Token Introspection | N/A |

**PKCE is required for all public clients** (SPAs, mobile apps) — never use Authorization Code without PKCE on a public client.

---

## Keycloak Realm Setup

```bash
# Start Keycloak locally (matches docker-compose.yml)
docker run -p 8180:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:24.0 start-dev

# Base URL: http://localhost:8180
# Admin console: http://localhost:8180/admin
```

```
Realm:   banking
Clients:
  banking-web-app    — public client, PKCE, Authorization Code
  banking-service    — confidential client, Client Credentials (service-to-service)
  banking-swagger    — public client, for Swagger UI token acquisition

Roles:
  realm roles:  ADMIN, USER, AUDITOR, SERVICE

Mappers (on banking-web-app client):
  - "roles" claim → maps realm roles → added to JWT access token
  - "email" claim → standard OIDC claim (from user profile)
```

```bash
# Create realm via Keycloak Admin CLI (kcadm.sh)
kcadm.sh config credentials \
  --server http://localhost:8180 \
  --realm master \
  --user admin --password admin

# Create realm
kcadm.sh create realms \
  -s realm=banking \
  -s enabled=true \
  -s displayName="Banking Platform"

# Create confidential client (service-to-service)
kcadm.sh create clients -r banking \
  -s clientId=banking-service \
  -s enabled=true \
  -s "serviceAccountsEnabled=true" \
  -s "standardFlowEnabled=false" \
  -s publicClient=false \
  -s secret=banking-service-secret

# Create public client (web app with PKCE)
kcadm.sh create clients -r banking \
  -s clientId=banking-web-app \
  -s enabled=true \
  -s publicClient=true \
  -s "standardFlowEnabled=true" \
  -s 'redirectUris=["http://localhost:3000/*","https://banking.example.com/*"]' \
  -s "pkceCodeChallengeMethod=S256"

# Create realm roles
kcadm.sh create roles -r banking -s name=ADMIN
kcadm.sh create roles -r banking -s name=USER
kcadm.sh create roles -r banking -s name=AUDITOR
kcadm.sh create roles -r banking -s name=SERVICE

# Add mapper: include roles in access token as "roles" claim
kcadm.sh create clients/$CLIENT_ID/protocol-mappers/models -r banking \
  -s name=roles-claim \
  -s protocolMapper=oidc-usermodel-realm-role-mapper \
  -s 'config."claim.name"=roles' \
  -s 'config."jsonType.label"=String' \
  -s 'config."multivalued"=true' \
  -s 'config."id.token.claim"=true' \
  -s 'config."access.token.claim"=true'
```

---

## OAuth2 Resource Server — JWT Validation

The banking platform acts as an **OAuth2 Resource Server** — it validates Bearer tokens issued by Keycloak.

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_URL:http://localhost:8180}/realms/banking
          jwk-set-uri: ${KEYCLOAK_URL:http://localhost:8180}/realms/banking/protocol/openid-connect/certs
          # jwk-set-uri is preferred — auto-rotates keys, no restart needed on key rotation

# application-kubernetes.yaml — points to in-cluster Keycloak
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.bankingplatform.com/realms/banking
          jwk-set-uri: https://keycloak.bankingplatform.com/realms/banking/protocol/openid-connect/certs
```

---

## OAuth2 Client — Authorization Code + PKCE (Web App Backend)

When the banking platform serves a web frontend that needs to obtain tokens:

```yaml
# application.yml — OAuth2 Login (for web apps with sessions)
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: banking-web-app
            client-secret: ""          # Empty — public client uses PKCE
            authorization-grant-type: authorization_code
            scope: openid, profile, email, roles
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            provider: keycloak
        provider:
          keycloak:
            issuer-uri: ${KEYCLOAK_URL:http://localhost:8180}/realms/banking
            user-name-attribute: preferred_username  # Keycloak's username claim
```

```java
// SecurityConfig — OAuth2 Login chain (separate from the API resource server chain)
@Bean
@Order(1)   // Higher priority than resource server chain
public SecurityFilterChain webLoginFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/web/**", "/login/**", "/oauth2/**")
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/web/public/**").permitAll()
            .anyRequest().authenticated())
        .oauth2Login(oauth2 -> oauth2
            .loginPage("/web/login")
            .defaultSuccessUrl("/web/dashboard", true)
            .failureUrl("/web/login?error")
            .userInfoEndpoint(userInfo -> userInfo
                .oidcUserService(oidcUserService()))
        )
        .logout(logout -> logout
            .logoutUrl("/web/logout")
            .logoutSuccessHandler(keycloakLogoutHandler())  // Keycloak back-channel logout
            .invalidateHttpSession(true)
            .clearAuthentication(true));
    return http.build();
}

// Keycloak logout — must invalidate session on the Keycloak side too
@Bean
public LogoutSuccessHandler keycloakLogoutHandler() {
    return (request, response, authentication) -> {
        String keycloakLogoutUrl = keycloakUrl + "/realms/banking/protocol/openid-connect/logout"
            + "?client_id=banking-web-app"
            + "&post_logout_redirect_uri=" + URLEncoder.encode(appBaseUrl, StandardCharsets.UTF_8);
        response.sendRedirect(keycloakLogoutUrl);
    };
}
```

---

## OAuth2 Client Credentials — Service-to-Service

Internal microservices calling each other use Client Credentials (no user context):

```yaml
# application.yml — service-to-service OAuth2 client
spring:
  security:
    oauth2:
      client:
        registration:
          banking-service:
            client-id: banking-service
            client-secret: ${OAUTH2_CLIENT_SECRET}   # From Vault/Secrets Manager
            authorization-grant-type: client_credentials
            scope: api.internal
            provider: keycloak
        provider:
          keycloak:
            issuer-uri: ${KEYCLOAK_URL}/realms/banking
            token-uri: ${KEYCLOAK_URL}/realms/banking/protocol/openid-connect/token
```

```java
package com.banking.platform.client;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebClientConfig {

    /**
     * WebClient with automatic OAuth2 Client Credentials token injection.
     * Token is fetched, cached, and auto-refreshed by Spring Security.
     */
    @Bean
    public WebClient internalWebClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        var oauth2Filter = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Filter.setDefaultClientRegistrationId("banking-service");  // Registration from above

        return WebClient.builder()
                .filter(oauth2Filter)
                .build();
    }
}

// Usage — token is injected automatically, no manual token management
@Service
@RequiredArgsConstructor
public class ReportingServiceClient {
    private final WebClient internalWebClient;

    public ReportResponse fetchReport(UUID reportId) {
        return internalWebClient.get()
                .uri("http://reporting-service/api/v1/reports/{id}", reportId)
                .retrieve()
                .bodyToMono(ReportResponse.class)
                .block();
    }
}
```

---

## OIDC — UserInfo and Claims

```java
package com.banking.platform.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class UserInfoController {

    // For OAuth2 Login (session-based) — OidcUser contains full UserInfo
    @GetMapping("/web/me")
    public Map<String, Object> currentUser(@AuthenticationPrincipal OidcUser oidcUser) {
        return Map.of(
            "sub",              oidcUser.getSubject(),
            "email",            oidcUser.getEmail(),
            "name",             oidcUser.getFullName(),
            "preferred_username", oidcUser.getPreferredUsername(),
            "roles",            oidcUser.getClaimAsStringList("roles")
        );
    }

    // For JWT Resource Server — Jwt contains all claims from the access token
    @GetMapping("/api/v1/me")
    public Map<String, Object> currentApiUser(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "sub",    jwt.getSubject(),
            "email",  jwt.getClaimAsString("email"),
            "roles",  jwt.getClaimAsStringList("roles"),
            "iss",    jwt.getIssuer().toString(),
            "exp",    jwt.getExpiresAt()
        );
    }
}
```

---

## Token Introspection (Opaque Tokens)

For cases where you cannot or don't want to use JWT (e.g., immediate revocation is required):

```yaml
# application.yml — opaque token introspection
spring:
  security:
    oauth2:
      resourceserver:
        opaquetoken:
          introspection-uri: ${KEYCLOAK_URL}/realms/banking/protocol/openid-connect/token/introspect
          client-id: banking-service
          client-secret: ${OAUTH2_CLIENT_SECRET}
```

```java
// SecurityConfig — switch from JWT to opaque token validation
.oauth2ResourceServer(oauth2 -> oauth2
    .opaqueToken(opaque -> opaque
        .introspectionUri(introspectionUri)
        .introspectionClientCredentials(clientId, clientSecret)
        .authenticationConverter(opaqueTokenAuthenticationConverter())
    )
)
```

**Trade-off**: Opaque tokens require a network call to Keycloak on **every request**. Use JWT for stateless validation; use opaque tokens only when immediate revocation (< 1s) is a hard requirement.

---

## Token Refresh + Revocation

```java
package com.banking.platform.security;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/web/auth")
@RequiredArgsConstructor
public class TokenController {

    private final OAuth2AuthorizedClientService authorizedClientService;

    // Inject the authorized client (Spring auto-refreshes if expired)
    @GetMapping("/token")
    public String getAccessToken(
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client) {
        return client.getAccessToken().getTokenValue();
    }

    // Revoke token via Keycloak's revocation endpoint
    @PostMapping("/revoke")
    public void revokeToken(
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client,
            @AuthenticationPrincipal org.springframework.security.core.Authentication auth) {
        // Remove from local authorized client store
        authorizedClientService.removeAuthorizedClient(
            client.getClientRegistration().getRegistrationId(),
            auth.getName()
        );
        // Also call Keycloak token revocation endpoint
        // POST ${KEYCLOAK_URL}/realms/banking/protocol/openid-connect/revoke
    }
}
```

---

## Keycloak Terraform Provisioning

```hcl
# terraform/keycloak.tf — provision Keycloak realm + clients via Terraform
terraform {
  required_providers {
    keycloak = {
      source  = "mrparkers/keycloak"
      version = "~> 4.0"
    }
  }
}

provider "keycloak" {
  client_id     = "admin-cli"
  username      = var.keycloak_admin_user
  password      = var.keycloak_admin_password
  url           = var.keycloak_url
}

resource "keycloak_realm" "banking" {
  realm                    = "banking"
  enabled                  = true
  display_name             = "Banking Platform"
  access_token_lifespan    = "15m"
  sso_session_idle_timeout = "30m"
  sso_session_max_lifespan = "12h"
  refresh_token_max_reuse  = 0
  revoke_refresh_token     = true   # Refresh token rotation — security best practice
}

resource "keycloak_openid_client" "banking_service" {
  realm_id      = keycloak_realm.banking.id
  client_id     = "banking-service"
  name          = "Banking Service (M2M)"
  enabled       = true
  access_type   = "CONFIDENTIAL"
  service_accounts_enabled = true
  standard_flow_enabled    = false
  client_secret = var.banking_service_client_secret   # From Vault
}

resource "keycloak_openid_client" "banking_web" {
  realm_id    = keycloak_realm.banking.id
  client_id   = "banking-web-app"
  name        = "Banking Web App"
  enabled     = true
  access_type = "PUBLIC"
  standard_flow_enabled = true
  pkce_code_challenge_method = "S256"
  valid_redirect_uris = [
    "https://banking.example.com/*",
    "http://localhost:3000/*"
  ]
  web_origins = ["+"]
}

resource "keycloak_role" "roles" {
  for_each = toset(["ADMIN", "USER", "AUDITOR", "SERVICE"])
  realm_id = keycloak_realm.banking.id
  name     = each.value
}
```

---

## Critical Rules (Updated)

1. **Never use `WebSecurityConfigurerAdapter`** — it's removed in Spring Security 6; use `SecurityFilterChain` beans.
2. **Always use PKCE for public clients** — SPAs and mobile apps must use `code_challenge_method=S256`.
3. **Never use Implicit Flow** — it's deprecated and insecure; use Authorization Code + PKCE instead.
4. **Always set `SessionCreationPolicy.STATELESS`** for JWT resource server chains.
5. **Never store JWTs in `localStorage`** — use `HttpOnly` cookies with `Secure` + `SameSite=Strict` for web apps.
6. **Enable refresh token rotation in Keycloak** (`revoke_refresh_token=true`) — prevents refresh token replay attacks.
7. **Set short access token TTL** (15 min) — long-lived access tokens are a security liability.
8. **Always use `jwk-set-uri`** over static public keys — JWKS auto-rotates without service restarts.
9. **Provision Keycloak via Terraform** — never configure realms/clients by hand in production.
10. **Test all OAuth2 flows explicitly** — unit test token claims; integration test the full OIDC discovery flow with a Keycloak Testcontainer.
