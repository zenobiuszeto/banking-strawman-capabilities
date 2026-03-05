---
name: banking-domain-model
description: |
  **Customer Profile & Onboarding Domain Model**: Design and implement the core customer, party, household, and account opening entities with KYC integration, document management, and identity verification workflows.

  MANDATORY TRIGGERS: Customer Profile, CustomerProfile, Party Management, party, household, KYC, Know Your Customer, Account Opening, account opening, eligibility, product catalog, ProductCatalog, EligibilityEngine, identity verification, document upload, eSignature, onboarding, com.banking.platform.customer, com.banking.platform.onboarding, CIP, Customer Identification Program, CustomerService, PartyService, AccountOpeningService, KycService, IdentityVerificationAdapter, ConsentManagement, consent, preferences, CustomerPreferences
---

# Banking Domain Model: Customer Profile & Onboarding

The banking domain model establishes the foundational entities for customer relationship management, party hierarchies, account origination workflows, and KYC/identity verification integrations. This skill covers designing resilient, compliant customer data structures with pluggable identity verification adapters, document management workflows with S3 presigned URLs, and state machine-driven account opening and KYC processes.

---

## Customer Profile Entity Design

The `CustomerProfile` entity serves as the root aggregate for all customer-related data, storing personally identifiable information (PII) with encryption for sensitive fields like SSN and date of birth. The entity supports multi-address, multi-contact configurations through child collections.

```java
package com.banking.platform.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "customer_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String customerId;

    private String firstName;
    private String lastName;
    
    // Encrypted SSN via Vault Transit
    private String ssn;  // Stored encrypted; decrypt on demand
    
    private LocalDate dateOfBirth;
    private String email;
    private String primaryPhone;
    
    @Enumerated(EnumType.STRING)
    private KycStatus kycStatus;
    
    @Enumerated(EnumType.STRING)
    private CustomerStatus status;
    
    private String partyId;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "customerProfile")
    private Set<CustomerAddress> addresses = new HashSet<>();
    
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "customerProfile")
    private Set<CustomerDocument> documents = new HashSet<>();
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

public enum KycStatus {
    PENDING, IN_REVIEW, APPROVED, REJECTED, EXEMPTED
}

public enum CustomerStatus {
    PROSPECT, ACTIVE, INACTIVE, DORMANT, CLOSED
}
```

## Party & Household Relationship Model

A `Party` represents a household or organizational entity containing multiple customer profiles. This hierarchical relationship enables shared accounts, joint account holders, and delegated access scenarios.

```java
package com.banking.platform.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Table(name = "parties")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Party {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String partyId;

    @Enumerated(EnumType.STRING)
    private PartyType partyType;

    private String partyName;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "partyId")
    private Set<CustomerProfile> members = new HashSet<>();

    private String primaryContact;

    private LocalDateTime createdAt;
}

public enum PartyType {
    INDIVIDUAL, HOUSEHOLD, JOINT, ORGANIZATION
}
```

## Product Catalog with Rates & Fees

```java
package com.banking.platform.product.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_catalog")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCatalog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String productId;

    @Enumerated(EnumType.STRING)
    private ProductType productType;

    private String productName;
    private String description;

    private BigDecimal baseAPY;
    private BigDecimal tier2APY;
    private BigDecimal tier2MinBalance;

    private BigDecimal monthlyMaintenanceFee;
    private BigDecimal outOfNetworkATMFee;
    private BigDecimal overdraftFee;
    private BigDecimal nsf_Fee;

    private Boolean minimumBalanceRequired;
    private BigDecimal minimumBalance;

    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    private LocalDateTime effectiveDate;
}

public enum ProductType {
    CHECKING, SAVINGS, MONEY_MARKET, CD, IRA, BROKERAGE
}

public enum ProductStatus {
    ACTIVE, DEPRECATED, CLOSED
}
```

## Account Opening Workflow & Eligibility Engine

The `AccountOpeningService` orchestrates the eligibility check, KYC initiation, and account creation workflow.

```java
package com.banking.platform.onboarding.service;

import com.banking.platform.customer.dto.*;
import com.banking.platform.customer.entity.*;
import com.banking.platform.onboarding.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EligibilityEngine {

    public EligibilityCheckResult evaluate(EligibilityCheckRequest request) {
        EligibilityCheckResult result = EligibilityCheckResult.builder()
            .customerId(request.getCustomerId())
            .eligible(true)
            .reasons(new ArrayList<>())
            .build();

        LocalDate minAgeDate = LocalDate.now().minusYears(18);
        if (request.getDateOfBirth().isAfter(minAgeDate)) {
            result.setEligible(false);
            result.getReasons().add("Customer must be 18 or older");
        }

        if (request.getSSN() == null || request.getSSN().isEmpty()) {
            result.setEligible(false);
            result.getReasons().add("Valid SSN required");
        }

        CreditCheckResult creditResult = performCreditCheck(request.getSSN());
        if (creditResult.getCreditScore() < 300) {
            result.setEligible(false);
            result.getReasons().add("Credit score too low");
        }

        return result;
    }

    private CreditCheckResult performCreditCheck(String ssn) {
        return CreditCheckResult.builder()
            .creditScore(750)
            .riskTier("STANDARD")
            .build();
    }
}

@Service
@RequiredArgsConstructor
public class AccountOpeningService {
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final KycService kycService;
    private final EligibilityEngine eligibilityEngine;
    private final ApplicationEventPublisher eventPublisher;
    private final VaultTransitService vaultTransitService;

    @Transactional
    public AccountOpeningResponse openAccount(AccountOpeningRequest request) {
        EligibilityCheckResult eligibility = eligibilityEngine.evaluate(
            EligibilityCheckRequest.builder()
                .customerId(request.getCustomerId())
                .dateOfBirth(request.getDateOfBirth())
                .ssn(request.getSsn())
                .build()
        );

        if (!eligibility.isEligible()) {
            return AccountOpeningResponse.builder()
                .success(false)
                .message("Customer failed eligibility check")
                .reasons(eligibility.getReasons())
                .build();
        }

        CustomerProfile profile = customerRepository.findById(request.getCustomerId())
            .orElseGet(() -> CustomerProfile.builder()
                .customerId(request.getCustomerId())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .primaryPhone(request.getPhone())
                .dateOfBirth(request.getDateOfBirth())
                .status(CustomerStatus.PROSPECT)
                .kycStatus(KycStatus.PENDING)
                .build());

        profile.setSsn(encryptSSN(request.getSsn()));
        customerRepository.save(profile);

        KycIntakeResponse kycResponse = kycService.initiateKycIntake(profile.getCustomerId());

        BankAccount account = BankAccount.builder()
            .accountNumber(generateAccountNumber())
            .customerId(profile.getCustomerId())
            .productId(request.getProductId())
            .accountStatus(AccountStatus.PENDING_OPEN)
            .createdAt(LocalDateTime.now())
            .build();

        BankAccount savedAccount = accountRepository.save(account);

        eventPublisher.publishEvent(new AccountOpenedEvent(
            savedAccount.getAccountId(),
            profile.getCustomerId(),
            savedAccount.getProductId()
        ));

        return AccountOpeningResponse.builder()
            .success(true)
            .accountId(savedAccount.getAccountId())
            .kycWorkflowId(kycResponse.getWorkflowId())
            .message("Account opened successfully; KYC workflow initiated")
            .build();
    }

    private String encryptSSN(String ssn) {
        return vaultTransitService.encrypt(ssn, "ssn-transit-key");
    }

    private String generateAccountNumber() {
        return System.nanoTime() % 10000000000000000L + "";
    }
}
```

## KYC Intake Service with Document Upload

The KYC service manages document upload workflows using S3 presigned URLs and integrates with pluggable identity verification adapters.

```java
package com.banking.platform.kyc.service;

import com.banking.platform.kyc.dto.*;
import com.banking.platform.kyc.entity.*;
import com.banking.platform.customer.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.*;
import lombok.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KycService {
    private final KycWorkflowRepository kycRepository;
    private final DocumentUploadService documentUploadService;
    private final IdentityVerificationAdapter identityVerificationAdapter;
    private final S3Presigner s3Presigner;
    private final ApplicationEventPublisher eventPublisher;

    public KycIntakeResponse initiateKycIntake(String customerId) {
        KycWorkflow workflow = KycWorkflow.builder()
            .customerId(customerId)
            .status(KycStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .documents(new ArrayList<>())
            .build();

        KycWorkflow saved = kycRepository.save(workflow);

        return KycIntakeResponse.builder()
            .workflowId(saved.getWorkflowId())
            .customerId(customerId)
            .status(KycStatus.PENDING)
            .requiredDocuments(List.of(
                DocumentType.GOVERNMENT_ID,
                DocumentType.PROOF_OF_ADDRESS,
                DocumentType.SELFIE
            ))
            .build();
    }

    public DocumentUploadUrlResponse generateUploadUrl(String workflowId, DocumentType docType) {
        String objectKey = String.format("kyc/%s/%s/%d", 
            workflowId, docType.name(), System.currentTimeMillis());

        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket("banking-kyc-bucket")
            .key(objectKey)
            .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .putObjectRequest(putRequest)
            .build();

        String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        return DocumentUploadUrlResponse.builder()
            .presignedUrl(presignedUrl)
            .expiresIn(Duration.ofMinutes(15))
            .objectKey(objectKey)
            .build();
    }

    @Transactional
    public void completeDocumentUpload(String workflowId, DocumentUploadEvent event) {
        KycWorkflow workflow = kycRepository.findById(workflowId)
            .orElseThrow(() -> new WorkflowNotFoundException(workflowId));

        CustomerDocument doc = CustomerDocument.builder()
            .documentType(event.getDocumentType())
            .s3ObjectKey(event.getObjectKey())
            .uploadedAt(LocalDateTime.now())
            .status(DocumentStatus.PENDING_VERIFICATION)
            .build();

        workflow.getDocuments().add(doc);
        workflow.setStatus(KycStatus.IN_REVIEW);

        kycRepository.save(workflow);

        identityVerificationAdapter.verifyIdentity(
            workflow.getCustomerId(),
            workflow.getWorkflowId(),
            workflow.getDocuments()
        );
    }
}

@Entity
@Table(name = "kyc_workflows")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycWorkflow {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String workflowId;

    private String customerId;

    @Enumerated(EnumType.STRING)
    private KycStatus status;

    @OneToMany(cascade = CascadeType.ALL)
    private List<CustomerDocument> documents;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String completionReason;
}

@Entity
@Table(name = "customer_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String documentId;

    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    private String s3ObjectKey;

    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    private LocalDateTime uploadedAt;
    private LocalDateTime verifiedAt;
    private String verificationResult;
}

public enum DocumentType {
    GOVERNMENT_ID, DRIVER_LICENSE, PASSPORT, PROOF_OF_ADDRESS, UTILITY_BILL, SELFIE
}

public enum DocumentStatus {
    PENDING_VERIFICATION, VERIFIED, REJECTED, EXPIRED
}
```

## Pluggable Identity Verification Adapter

```java
package com.banking.platform.kyc.adapter;

import com.banking.platform.customer.entity.CustomerDocument;
import java.util.List;

public interface IdentityVerificationAdapter {
    IdentityVerificationResult verifyIdentity(
        String customerId,
        String workflowId,
        List<CustomerDocument> documents
    );
}

@Component
public class SocureIdentityAdapter implements IdentityVerificationAdapter {
    @Override
    public IdentityVerificationResult verifyIdentity(
        String customerId,
        String workflowId,
        List<CustomerDocument> documents
    ) {
        SocureRequest socureReq = buildSocureRequest(documents);
        SocureResponse socureResp = socureClient.verify(socureReq);
        
        return IdentityVerificationResult.builder()
            .verified(socureResp.getRiskScore() > 0.8)
            .riskScore(socureResp.getRiskScore())
            .provider("SOCURE")
            .build();
    }
}

@Component
public class PersonaIdentityAdapter implements IdentityVerificationAdapter {
    @Override
    public IdentityVerificationResult verifyIdentity(
        String customerId,
        String workflowId,
        List<CustomerDocument> documents
    ) {
        PersonaVerifyRequest req = PersonaVerifyRequest.builder()
            .referenceId(workflowId)
            .build();
        PersonaVerifyResponse resp = personaClient.verify(req);
        
        return IdentityVerificationResult.builder()
            .verified("approved".equalsIgnoreCase(resp.getStatus()))
            .provider("PERSONA")
            .build();
    }
}
```

## Consent Management (GDPR/CCPA)

```java
package com.banking.platform.consent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_consents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String consentId;

    private String customerId;

    @Enumerated(EnumType.STRING)
    private ConsentType consentType;

    private Boolean granted;

    private LocalDateTime grantedAt;
    private LocalDateTime revokedAt;
    
    private String ipAddress;
    private String userAgent;

    public record ConsentRequest(
        String customerId,
        ConsentType consentType,
        Boolean granted
    ) {}
}

public enum ConsentType {
    MARKETING_EMAIL, MARKETING_SMS, DATA_SHARING, THIRD_PARTY_ADVERTISING
}
```

## Customer Preferences

```java
package com.banking.platform.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerPreferences {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String preferenceId;

    private String customerId;

    private Boolean notificationEmail;
    private Boolean notificationSms;
    private Boolean notificationPush;
    private Boolean notificationPhone;

    @Enumerated(EnumType.STRING)
    private LanguageCode preferredLanguage;

    private Boolean accessibilityScreenReader;
    private Boolean accessibilityHighContrast;

    private Boolean marketingOptIn;
    private String communicationFrequency;

    private LocalDateTime updatedAt;
}

public enum LanguageCode {
    EN_US, ES_US, FR_CA, ZH_CN
}
```

## REST Controller

```java
package com.banking.platform.customer.controller;

import com.banking.platform.customer.dto.*;
import com.banking.platform.customer.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customerService;
    private final AccountOpeningService accountOpeningService;
    private final KycService kycService;

    @PostMapping("/register")
    public ResponseEntity<AccountOpeningResponse> registerCustomer(
        @RequestBody AccountOpeningRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(accountOpeningService.openAccount(request));
    }

    @GetMapping("/{customerId}/kyc/upload-url")
    public ResponseEntity<DocumentUploadUrlResponse> getUploadUrl(
        @PathVariable String customerId,
        @RequestParam DocumentType documentType
    ) {
        String workflowId = kycService.getActiveWorkflowId(customerId);
        return ResponseEntity.ok(
            kycService.generateUploadUrl(workflowId, documentType)
        );
    }

    @PostMapping("/{customerId}/preferences")
    public ResponseEntity<Void> updatePreferences(
        @PathVariable String customerId,
        @RequestBody CustomerPreferencesRequest request
    ) {
        customerService.updatePreferences(customerId, request);
        return ResponseEntity.noContent().build();
    }
}
```

## Flyway Migration Example

```sql
-- V1__init_customer_domain.sql
CREATE TABLE customer_profiles (
    customer_id VARCHAR(36) PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    ssn VARCHAR(255) NOT NULL,
    date_of_birth DATE NOT NULL,
    email VARCHAR(255),
    primary_phone VARCHAR(20),
    kyc_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    status VARCHAR(50) NOT NULL DEFAULT 'PROSPECT',
    party_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (party_id) REFERENCES parties(party_id)
);

CREATE TABLE parties (
    party_id VARCHAR(36) PRIMARY KEY,
    party_type VARCHAR(50) NOT NULL,
    party_name VARCHAR(255),
    primary_contact VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product_catalog (
    product_id VARCHAR(36) PRIMARY KEY,
    product_type VARCHAR(50) NOT NULL,
    product_name VARCHAR(255),
    description TEXT,
    base_apy DECIMAL(10, 6),
    tier2_apy DECIMAL(10, 6),
    tier2_min_balance DECIMAL(20, 2),
    monthly_maintenance_fee DECIMAL(10, 2),
    overdraft_fee DECIMAL(10, 2),
    nsf_fee DECIMAL(10, 2),
    minimum_balance DECIMAL(20, 2),
    effective_date TIMESTAMP,
    status VARCHAR(50)
);

CREATE TABLE kyc_workflows (
    workflow_id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    completion_reason VARCHAR(255),
    FOREIGN KEY (customer_id) REFERENCES customer_profiles(customer_id)
);

CREATE TABLE customer_documents (
    document_id VARCHAR(36) PRIMARY KEY,
    document_type VARCHAR(50) NOT NULL,
    s3_object_key VARCHAR(500),
    status VARCHAR(50) NOT NULL,
    uploaded_at TIMESTAMP,
    verified_at TIMESTAMP,
    verification_result TEXT
);

CREATE TABLE customer_preferences (
    preference_id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    notification_email BOOLEAN DEFAULT TRUE,
    notification_sms BOOLEAN DEFAULT FALSE,
    notification_push BOOLEAN DEFAULT FALSE,
    notification_phone BOOLEAN DEFAULT FALSE,
    preferred_language VARCHAR(10) DEFAULT 'EN_US',
    accessibility_screen_reader BOOLEAN DEFAULT FALSE,
    accessibility_high_contrast BOOLEAN DEFAULT FALSE,
    marketing_opt_in BOOLEAN DEFAULT FALSE,
    communication_frequency VARCHAR(50) DEFAULT 'WEEKLY',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer_profiles(customer_id)
);

CREATE TABLE customer_consents (
    consent_id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    consent_type VARCHAR(100) NOT NULL,
    granted BOOLEAN NOT NULL,
    granted_at TIMESTAMP,
    revoked_at TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    FOREIGN KEY (customer_id) REFERENCES customer_profiles(customer_id),
    UNIQUE(customer_id, consent_type)
);

CREATE INDEX idx_customer_kyc ON customer_profiles(kyc_status);
CREATE INDEX idx_kyc_workflow_status ON kyc_workflows(status);
```

---

## 10 Critical Rules

1. **Encrypt PII at Rest**: Always encrypt SSN, DOB, and government ID numbers using VaultTransitService before persisting to database; decrypt only on authenticated read operations with proper audit logging.

2. **Idempotent Account Opening**: Use unique RequestId/CorrelationId header to prevent duplicate account creation if the initial request times out; store mapping of requestId → accountId.

3. **Validate Eligibility Before KYC**: Run EligibilityEngine checks (age, SSN format, credit score threshold) before initiating expensive KYC/identity verification workflows to fail-fast on ineligible customers.

4. **Document Upload Presigned URLs Expire**: Generate 15-minute presigned URLs for S3 document uploads; validate object was actually written by polling S3 before marking document upload complete in KYC workflow.

5. **Identity Verification is Async**: Call identity verification adapters (Socure/Persona/Alloy) asynchronously via Kafka topic; do not block account opening response; use webhook callbacks to update KYC status when verification completes.

6. **Implement KYC State Machine Strictly**: Enforce transitions PENDING → IN_REVIEW → (APPROVED|REJECTED|EXEMPTED); prevent transitions backward or to invalid states; use @Transactional to ensure state consistency.

7. **Multi-Party Household Support**: Design CustomerProfile and Party relationships to support joint accounts, authorized users, and POA scenarios; use partyId foreign key to enforce household-level constraints.

8. **Consent Records are Immutable Audit Trail**: Never update or delete Consent records; revocation is a new record with revokedAt timestamp; store IP/UserAgent for regulatory compliance and fraud investigation.

9. **Product Catalog Effective Dating**: Use effectiveDate on ProductCatalog records to support rate/fee changes; when opening account, capture product snapshot (APY/fees) at account creation time; do not use live product rates for historical accounts.

10. **Flyway Version Control Strictly**: Every schema change requires a new Flyway migration (V2, V3, etc.); never edit existing migration files; use undo migrations cautiously; coordinate migrations across team to prevent deployment ordering conflicts.
