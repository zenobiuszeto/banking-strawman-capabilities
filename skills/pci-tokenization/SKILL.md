---
name: pci-tokenization
description: |
  **PCI DSS Tokenization & Format-Preserving Encryption**: Eliminate cardholder data environment scope through HSM-backed token vault, network tokenization adapters, and PCI-segmented Kubernetes networking.

  MANDATORY TRIGGERS: PCI, PCI DSS, PCI compliance, PCI zone, cardholder data environment, CDE, tokenization, PAN tokenization, PAN token vault, TokenVault, TokenizationService, detokenization, DetokenizationService, PAN, Primary Account Number, card number, token format, format-preserving encryption, FPE, FF3, Luhn, Luhn check, network token, network tokenization, Visa Token Service, VTS, Mastercard MDES, HSM, Hardware Security Module, key ceremony, key loading, HSM adapter, ThalesHSM, FuturexHSM, HsmAdapter, PIN block, PIN encryption, PIN verify, TR-31 key block, key rotation, key custodian, PCI segmentation, cardholder data, SAD, sensitive authentication data, scope reduction, PCI audit, QSA, PCI SAQ, PA-DSS
---

# PCI DSS Tokenization & Format-Preserving Encryption

PCI DSS compliance demands cryptographic separation of cardholder data from transactional systems. This skill implements a production-grade tokenization platform using HSM-backed format-preserving encryption (FF3-1 algorithm), network token provisioning for digital wallets, and strict Kubernetes segmentation to reduce PCI audit scope to a single isolated namespace.

---

## Card Data Flow Architecture

The platform segregates PAN handling into a dedicated CDE (Cardholder Data Environment) namespace. All external services receive tokens instead of live card data:

```
[Payment Entry Point] 
    ↓ (PAN + CVV)
[TokenizationService in CDE namespace]
    ↓ (encrypt PAN via HSM, generate FF3 token)
[TokenVault table: token ↔ encrypted PAN mapping]
    ↓ (return token + masked PAN for display)
[Payment orchestration/ledger in non-CDE: only uses token]
    ↓ (on settlement, DetokenizationService converts token → PAN in CDE only)
[Network: Visa/Mastercard settlement API]
```

---

## TokenizationService Implementation

```java
package com.banking.platform.payment.tokenization;

import com.banking.platform.payment.dto.CardData;
import com.banking.platform.security.vault.HsmAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenizationService {
    
    private final TokenVaultRepository tokenVaultRepository;
    private final HsmAdapter hsmAdapter;
    private final FormatPreservingEncryption fpeEngine;
    private final AuditEventPublisher auditEventPublisher;
    
    @Transactional
    public TokenizationResponse tokenizePAN(CardData cardData, String purpose) {
        String pan = cardData.getPan();
        
        // Validate PAN format and Luhn check
        if (!isValidLuhnChecksum(pan)) {
            auditEventPublisher.publish(AuditEvent.builder()
                .action("TOKENIZATION_FAILED_INVALID_LUHN")
                .resourceId(maskPAN(pan))
                .outcome("FAILURE")
                .metadata(Map.of("purpose", purpose))
                .build());
            throw new InvalidCardDataException("PAN failed Luhn validation");
        }
        
        // Check if token exists (idempotency)
        String panHash = hsmAdapter.hashPAN(pan);
        TokenVault existingToken = tokenVaultRepository.findByPanHash(panHash);
        if (existingToken != null && !existingToken.isExpired()) {
            log.debug("Token cache hit for PAN hash");
            return new TokenizationResponse(
                existingToken.getToken(),
                existingToken.getMaskedPan(),
                existingToken.getExpiryDate()
            );
        }
        
        // Generate FF3-1 format-preserving token (looks like valid PAN)
        String token = fpeEngine.encryptFF3_1(
            pan,
            HsmAdapter.KEY_FPE_FF3_1,
            cardData.getExpiryDate().toString() // Use expiry as tweak
        );
        
        // Ensure token passes Luhn check (FF3-1 preserves format)
        if (!isValidLuhnChecksum(token)) {
            token = adjustTokenForLuhn(token);
        }
        
        // Encrypt PAN using HSM Key Encryption Key (KEK)
        byte[] encryptedPan = hsmAdapter.encryptWithKEK(pan.getBytes());
        
        // Store mapping in vault
        TokenVault vault = TokenVault.builder()
            .id(UUID.randomUUID())
            .token(token)
            .panHash(panHash)
            .encryptedPan(encryptedPan)
            .maskedPan(maskPAN(pan))
            .expiryDate(cardData.getExpiryDate())
            .cvvHash(hsmAdapter.hashCVV(cardData.getCvv())) // Never store CVV
            .createdAt(LocalDateTime.now())
            .tokenizationPurpose(purpose)
            .build();
        
        tokenVaultRepository.save(vault);
        
        // Publish audit event
        auditEventPublisher.publish(AuditEvent.builder()
            .action("TOKENIZATION_SUCCESSFUL")
            .resourceId(token)
            .resourceType("TOKEN")
            .outcome("SUCCESS")
            .metadata(Map.of(
                "maskedPan", vault.getMaskedPan(),
                "purpose", purpose,
                "expiryDate", cardData.getExpiryDate().toString()
            ))
            .build());
        
        return new TokenizationResponse(token, vault.getMaskedPan(), vault.getExpiryDate());
    }
    
    private boolean isValidLuhnChecksum(String pan) {
        int sum = 0;
        boolean isEvenPosition = false;
        for (int i = pan.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(pan.charAt(i));
            if (isEvenPosition) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            isEvenPosition = !isEvenPosition;
        }
        return sum % 10 == 0;
    }
    
    private String adjustTokenForLuhn(String token) {
        // Adjust last digit to ensure Luhn compliance
        int expectedChecksum = (10 - (luhnSum(token.substring(0, token.length() - 1)) % 10)) % 10;
        return token.substring(0, token.length() - 1) + expectedChecksum;
    }
    
    private int luhnSum(String digits) {
        int sum = 0;
        boolean isEvenPosition = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(digits.charAt(i));
            if (isEvenPosition) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            isEvenPosition = !isEvenPosition;
        }
        return sum;
    }
    
    private String maskPAN(String pan) {
        if (pan.length() < 6) return "****";
        String bin = pan.substring(0, 6);
        String last4 = pan.substring(pan.length() - 4);
        return bin + "****" + last4;
    }
}
```

---

## DetokenizationService (CDE-Only)

```java
package com.banking.platform.payment.tokenization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetokenizationService {
    
    private final TokenVaultRepository tokenVaultRepository;
    private final HsmAdapter hsmAdapter;
    private final FormatPreservingEncryption fpeEngine;
    private final AuditEventPublisher auditEventPublisher;
    
    /**
     * CRITICAL: Only callable from CDE-zoned pods with explicit RBAC.
     * Every call is immutably logged to audit stream.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('CDE_SERVICE')")
    public String detokenizePAN(String token, String requestorServiceId, String purpose) {
        TokenVault vault = tokenVaultRepository.findByToken(token)
            .orElseThrow(() -> new TokenNotFoundException("Token not found in vault"));
        
        if (vault.isExpired()) {
            auditEventPublisher.publish(AuditEvent.builder()
                .action("DETOKENIZATION_FAILED_EXPIRED_TOKEN")
                .resourceId(token)
                .outcome("FAILURE")
                .metadata(Map.of("reason", "Token expired", "purpose", purpose))
                .build());
            throw new ExpiredTokenException("Token has expired");
        }
        
        // Decrypt PAN from HSM-encrypted form
        byte[] decryptedPanBytes = hsmAdapter.decryptWithKEK(vault.getEncryptedPan());
        String pan = new String(decryptedPanBytes);
        
        // IMMUTABLE AUDIT: Log every detokenization with requestor, purpose, timestamp
        auditEventPublisher.publish(AuditEvent.builder()
            .action("DETOKENIZATION_SUCCESSFUL")
            .actorId(requestorServiceId)
            .resourceId(token)
            .resourceType("TOKEN")
            .outcome("SUCCESS")
            .metadata(Map.of(
                "purpose", purpose,
                "detokenizedPan", maskPAN(pan),
                "timestamp", Instant.now().toString()
            ))
            .build());
        
        return pan;
    }
    
    private String maskPAN(String pan) {
        if (pan.length() < 6) return "****";
        return pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
    }
}
```

---

## TokenVault Entity

```java
package com.banking.platform.payment.tokenization;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "token_vault", indexes = {
    @Index(name = "idx_token", columnList = "token", unique = true),
    @Index(name = "idx_pan_hash", columnList = "pan_hash")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenVault {
    
    @Id
    private UUID id;
    
    @Column(nullable = false, unique = true, length = 19)
    private String token; // FF3-1 encrypted token (looks like PAN, Luhn-valid)
    
    @Column(nullable = false, unique = true, length = 64)
    private String panHash; // SHA-256(PAN) for uniqueness constraint
    
    @Column(nullable = false)
    @Lob
    private byte[] encryptedPan; // AES-256-GCM encrypted by HSM KEK
    
    @Column(nullable = false, length = 19)
    private String maskedPan; // Display format: 654321****9876
    
    @Column(nullable = false)
    private LocalDate expiryDate;
    
    @Column(nullable = false, length = 64)
    private String cvvHash; // Never store plaintext CVV
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime lastUsedAt;
    
    @Column(nullable = false)
    private String tokenizationPurpose; // e.g., "ECOMMERCE", "RECURRING", "TOKENIZATION_REQUEST"
    
    @Column
    private String networkTokenReference; // For Visa/Mastercard network tokens
    
    @Version
    private Long version;
    
    public boolean isExpired() {
        return LocalDate.now().isAfter(expiryDate);
    }
}
```

---

## HsmAdapter Interface & ThalesHSM Implementation

```java
package com.banking.platform.security.vault;

public interface HsmAdapter {
    
    static final String KEY_FPE_FF3_1 = "fpe.ff3.1.key";
    static final String KEY_KEK = "data.encryption.kek";
    
    byte[] encryptWithKEK(byte[] plaintext);
    byte[] decryptWithKEK(byte[] ciphertext);
    
    String encryptFF3_1(String plaintext, String keyId, String tweak);
    String decryptFF3_1(String ciphertext, String keyId, String tweak);
    
    String hashPAN(String pan);
    String hashCVV(String cvv);
    
    void keyLoad(String keyId, byte[] keyMaterial, String keyType);
    void keyRotate(String keyId);
    
    HsmHealthStatus getHealthStatus();
}
```

```java
package com.banking.platform.security.vault.thales;

import com.banking.platform.security.vault.HsmAdapter;
import com.thales.hsm.slb.HsmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThalesHsmAdapter implements HsmAdapter {
    
    private final HsmClient hsmClient;
    private final HsmConnectionPool hsmConnectionPool;
    
    @Override
    public byte[] encryptWithKEK(byte[] plaintext) {
        try {
            var connection = hsmConnectionPool.borrowConnection();
            try {
                var keySpec = new SecretKeySpec(
                    connection.getSymmetricKey(KEY_KEK),
                    0,
                    32,
                    "AES"
                );
                
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                byte[] nonce = new byte[12];
                SecureRandom.getInstanceStrong().nextBytes(nonce);
                
                GCMParameterSpec spec = new GCMParameterSpec(128, nonce);
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
                byte[] ciphertext = cipher.doFinal(plaintext);
                
                // Return nonce + ciphertext
                byte[] result = new byte[nonce.length + ciphertext.length];
                System.arraycopy(nonce, 0, result, 0, nonce.length);
                System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
                
                return result;
            } finally {
                hsmConnectionPool.returnConnection(connection);
            }
        } catch (Exception e) {
            log.error("HSM encryption failed", e);
            throw new HsmOperationException("KEK encryption failed", e);
        }
    }
    
    @Override
    public byte[] decryptWithKEK(byte[] combined) {
        try {
            var connection = hsmConnectionPool.borrowConnection();
            try {
                byte[] nonce = Arrays.copyOfRange(combined, 0, 12);
                byte[] ciphertext = Arrays.copyOfRange(combined, 12, combined.length);
                
                var keySpec = new SecretKeySpec(
                    connection.getSymmetricKey(KEY_KEK),
                    0,
                    32,
                    "AES"
                );
                
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec spec = new GCMParameterSpec(128, nonce);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
                
                return cipher.doFinal(ciphertext);
            } finally {
                hsmConnectionPool.returnConnection(connection);
            }
        } catch (Exception e) {
            log.error("HSM decryption failed", e);
            throw new HsmOperationException("KEK decryption failed", e);
        }
    }
    
    @Override
    public String encryptFF3_1(String plaintext, String keyId, String tweak) {
        try {
            var connection = hsmConnectionPool.borrowConnection();
            try {
                byte[] key = connection.getSymmetricKey(keyId);
                // Use ThalesHSM FF3-1 library or NIST reference implementation
                FF3_1Engine ff3 = new FF3_1Engine(key, tweak.getBytes());
                return ff3.encrypt(plaintext);
            } finally {
                hsmConnectionPool.returnConnection(connection);
            }
        } catch (Exception e) {
            log.error("FF3-1 encryption failed", e);
            throw new HsmOperationException("FF3-1 encryption failed", e);
        }
    }
    
    @Override
    public String decryptFF3_1(String ciphertext, String keyId, String tweak) {
        try {
            var connection = hsmConnectionPool.borrowConnection();
            try {
                byte[] key = connection.getSymmetricKey(keyId);
                FF3_1Engine ff3 = new FF3_1Engine(key, tweak.getBytes());
                return ff3.decrypt(ciphertext);
            } finally {
                hsmConnectionPool.returnConnection(connection);
            }
        } catch (Exception e) {
            log.error("FF3-1 decryption failed", e);
            throw new HsmOperationException("FF3-1 decryption failed", e);
        }
    }
    
    @Override
    public String hashPAN(String pan) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pan.getBytes());
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("PAN hash failed", e);
        }
    }
    
    @Override
    public String hashCVV(String cvv) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cvv.getBytes());
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("CVV hash failed", e);
        }
    }
    
    @Override
    public void keyLoad(String keyId, byte[] keyMaterial, String keyType) {
        // Implement HSM key loading during initialization
        log.info("Loading key {} of type {} into HSM", keyId, keyType);
    }
    
    @Override
    public void keyRotate(String keyId) {
        log.info("Rotating key {}", keyId);
    }
    
    @Override
    public HsmHealthStatus getHealthStatus() {
        try {
            hsmConnectionPool.borrowConnection();
            return new HsmHealthStatus(true, "HSM operational");
        } catch (Exception e) {
            return new HsmHealthStatus(false, "HSM unavailable: " + e.getMessage());
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
```

---

## PCI Network Segmentation in Kubernetes

```yaml
# kubernetes/cde-namespace.yml
---
apiVersion: v1
kind: Namespace
metadata:
  name: cde
  labels:
    pci-segment: "true"
    compliance: "pci-dss-v3.2.1"
    
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: cde-tokenization-only
  namespace: cde
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
  ingress:
    # Only card-auth pod can call tokenization
    - from:
        - podSelector:
            matchLabels:
              app: card-auth
              namespace: payment
        - podSelector:
            matchLabels:
              app: tokenization-worker
              namespace: cde
      ports:
        - protocol: TCP
          port: 8080
    # Prometheus scraping
    - from:
        - podSelector:
            matchLabels:
              app: prometheus
              namespace: monitoring
      ports:
        - protocol: TCP
          port: 9090
  egress:
    # Only to HSM (no internet!)
    - to:
        - ipBlock:
            cidr: 10.0.1.0/24 # HSM VLAN
      ports:
        - protocol: TCP
          port: 5000
    # To database (vault storage)
    - to:
        - podSelector:
            matchLabels:
              app: postgres
              namespace: data
      ports:
        - protocol: TCP
          port: 5432
    # To Kafka audit topic
    - to:
        - podSelector:
            matchLabels:
              app: kafka
              namespace: data
      ports:
        - protocol: TCP
          port: 9092
    # DNS only
    - to:
        - namespaceSelector: {}
      ports:
        - protocol: UDP
          port: 53

---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: detokenization-restricted
  namespace: cde
spec:
  podSelector:
    matchLabels:
      app: detokenization-worker
  policyTypes:
    - Ingress
  ingress:
    # Only settlement and reconciliation services
    - from:
        - podSelector:
            matchLabels:
              app: settlement-service
              namespace: operations
        - podSelector:
            matchLabels:
              app: reconciliation-service
              namespace: operations
      ports:
        - protocol: TCP
          port: 8080
```

---

## Spring Security Method-Level Detokenization Protection

```java
package com.banking.platform.payment.tokenization;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class DetokenizationSecurityConfig {
    // PreAuthorize annotations on DetokenizationService methods enforce:
    // - Only CDE_SERVICE role can call
    // - Only from within CDE namespace (validated by network policy)
    // - All calls logged to immutable audit trail
}
```

---

## Visa Token Service Adapter

```java
package com.banking.platform.payment.tokenization.network;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisaTokenServiceAdapter {
    
    private final VisaTokenServiceClient visaClient;
    private final TokenVaultRepository tokenVaultRepository;
    private final AuditEventPublisher auditEventPublisher;
    
    /**
     * Provision network token for digital wallet (Apple Pay, Google Pay, Samsung Pay).
     * DPAN = Device Primary Account Number (temporary, device-bound)
     * FPAN = Funding Primary Account Number (actual card, never exposed to merchant)
     */
    public NetworkTokenProvisioningResponse provisionNetworkToken(
        String fpan,
        String deviceId,
        String walletProvider) {
        
        var request = VisaTokenServiceRequest.builder()
            .fundingAccountInfo(FundingAccountInfo.builder()
                .pan(fpan)
                .expiryMonth(expiryMonth)
                .expiryYear(expiryYear)
                .cvv2(cvv2)
                .build())
            .tokenServiceProvider(TokenServiceProvider.VISA)
            .deviceId(deviceId)
            .walletProvider(walletProvider)
            .tokenRequestorId("12345") // Visa-assigned
            .build();
        
        var vtsResponse = visaClient.provisionToken(request);
        
        // Store network token reference in vault
        TokenVault vault = tokenVaultRepository.findByToken(internalToken).orElseThrow();
        vault.setNetworkTokenReference(vtsResponse.getNetworkToken());
        tokenVaultRepository.save(vault);
        
        auditEventPublisher.publish(AuditEvent.builder()
            .action("NETWORK_TOKEN_PROVISIONED")
            .resourceId(vtsResponse.getNetworkToken())
            .metadata(Map.of(
                "provider", walletProvider,
                "fpan", maskPAN(fpan),
                "vtsTokenReference", vtsResponse.getTokenReference()
            ))
            .build());
        
        return new NetworkTokenProvisioningResponse(
            vtsResponse.getNetworkToken(),
            vtsResponse.getTokenExpiry(),
            vtsResponse.getTokenReference()
        );
    }
    
    public void suspendNetworkToken(String networkToken) {
        var request = VisaTokenLifecycleRequest.builder()
            .action("SUSPEND")
            .networkToken(networkToken)
            .reason("CARDHOLDER_REQUESTED")
            .build();
        
        visaClient.updateTokenStatus(request);
        
        auditEventPublisher.publish(AuditEvent.builder()
            .action("NETWORK_TOKEN_SUSPENDED")
            .resourceId(networkToken)
            .outcome("SUCCESS")
            .build());
    }
    
    private String maskPAN(String pan) {
        return pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
    }
}
```

---

## Audit Logging Configuration

```yaml
# application-kubernetes.yml
spring:
  kafka:
    bootstrap-servers: kafka:9092
    producer:
      acks: all
      retries: 3
      linger-ms: 10
      batch-size: 32768
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQL10Dialect

management:
  endpoints:
    web:
      exposure:
        include: health,metrics

logging:
  pattern:
    default: "%d{ISO8601} [%X{traceId}] [%X{spanId}] %logger - %msg%n"
  level:
    com.banking.platform.payment.tokenization: DEBUG
    com.banking.platform.security.vault: DEBUG

banking:
  pci:
    tokenization:
      hsm:
        endpoint: ${HSM_ENDPOINT:hsm.cde.svc.cluster.local}
        port: 5000
        timeout-ms: 5000
        connection-pool-size: 10
      audit:
        kafka-topic: audit-events
        retention-days: 2555 # 7 years
        immutable: true
```

---

## 10 Critical Rules

1. **Never log raw PAN/CVV**: All card data must be masked in logs. Use `maskPAN()` and never reference plaintext in messages. CVV must never be stored, only hashed.

2. **FF3-1 tokens must pass Luhn check**: Format-preserving encryption ensures tokens are indistinguishable from real PANs. Always validate Luhn checksum post-encryption and adjust last digit if needed.

3. **Detokenization audit is immutable**: Every `detokenizePAN()` call must publish an AuditEvent to Kafka before returning. Never allow UPDATE/DELETE on audit logs; configure PostgreSQL ROW LEVEL SECURITY if needed.

4. **CDE namespace isolation is mandatory**: Kubernetes NetworkPolicy MUST restrict ingress/egress. Detokenization pod receives traffic ONLY from settlement/reconciliation services. Zero internet egress allowed.

5. **HSM connection pooling prevents exhaustion**: Use connection pooling (BorroweConnection/ReturnConnection pattern). Never create unbounded HSM client instances or handle requests will hang.

6. **Encrypted PAN storage uses GCM mode**: AES-256-GCM with random nonce ensures authenticated encryption. Never use ECB or CBC without authentication. Nonce must be stored with ciphertext (first 12 bytes).

7. **Token expiry matches card expiry**: TokenVault.expiryDate must equal card expiry month/year. Expired tokens must not be detokenized; audit logs this attempt as suspicious activity.

8. **Key rotation requires graceful migration**: New keys must be loaded into HSM before activating. Old keys remain functional for 30 days to decrypt existing tokens. Track key version in TokenVault.

9. **PII redaction in logs is cryptographic**: Use secure hashing (SHA-256) for audit PAN references, never Base64 encoding. Exception: masked PAN for display (BIN + "****" + last 4) is permitted.

10. **Network tokenization reference must be tracked**: When Visa/Mastercard provisions a network token, store the reference in TokenVault.networkTokenReference. On wallet deletion, call suspend/delete API; never orphan network tokens.

