---
name: secrets-management
description: |
  **Secrets Management Skill**: Production secrets handling for the banking platform using HashiCorp Vault (KV v2, dynamic database credentials, transit encryption, PKI/mTLS, Kubernetes auth), Spring Cloud Vault integration, Vault Agent sidecar injection, and External Secrets Operator for AWS Secrets Manager and GCP Secret Manager.

  MANDATORY TRIGGERS: Vault, HashiCorp Vault, Spring Cloud Vault, vault kv, vault write, transit encrypt, transit decrypt, dynamic credentials, dynamic secrets, database credentials, PKI, mTLS, Kubernetes auth, workload identity, secret rotation, External Secrets Operator, ESO, SecretStore, ExternalSecret, AWS Secrets Manager, GCP Secret Manager, secretsmanager, secret injection, Vault Agent, vault-agent, vault-agent-init, vault annotations, banking-policy, banking-pii, banking-platform secrets, secrets management, KV v2
---

# Secrets Management Skill — Banking Platform

This banking platform uses **HashiCorp Vault** as the primary secrets backend deployed via Helm (`vault 0.28.0`) on the same Kubernetes cluster. **External Secrets Operator** provides compatibility with AWS Secrets Manager (EKS) and GCP Secret Manager (GKE).

---

## Vault Architecture Overview

| Engine       | Path                              | Purpose                                        |
|--------------|-----------------------------------|------------------------------------------------|
| KV v2        | `secret/banking-platform/*`       | Static secrets (JWT key, Kafka creds, API keys)|
| Database     | `database/creds/banking-app-role` | Dynamic PostgreSQL credentials (1h TTL)        |
| Database     | `database/creds/banking-mongo-role` | Dynamic MongoDB credentials                  |
| Transit      | `transit/keys/banking-pii`        | Envelope encryption for PII fields (AES-256)   |
| PKI          | `pki/issue/banking-internal`      | TLS certs for service-to-service mTLS          |
| Kubernetes   | `auth/kubernetes/`                | Pod authentication — no long-lived tokens      |

---

## Vault Policy — `banking-policy.hcl`

```hcl
# secret/data/banking-platform/* — static KV secrets
path "secret/data/banking-platform/*" {
  capabilities = ["read", "list"]
}
path "secret/metadata/banking-platform/*" {
  capabilities = ["read", "list"]
}

# Dynamic PostgreSQL credentials
path "database/creds/banking-app-role" {
  capabilities = ["read"]
}

# Dynamic MongoDB credentials
path "database/creds/banking-mongo-role" {
  capabilities = ["read"]
}

# PKI — issue TLS certs for mTLS between services
path "pki/issue/banking-internal" {
  capabilities = ["create", "update"]
}
path "pki/cert/ca" {
  capabilities = ["read"]
}

# Transit — encrypt/decrypt PII fields at rest
path "transit/encrypt/banking-pii" {
  capabilities = ["update"]
}
path "transit/decrypt/banking-pii" {
  capabilities = ["update"]
}

# Token self-management (required by Spring Cloud Vault)
path "auth/token/renew-self" { capabilities = ["update"] }
path "auth/token/lookup-self" { capabilities = ["read"] }
```

---

## Vault Initialization (`init-vault.sh`)

```bash
#!/bin/bash
set -euo pipefail
VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
NAMESPACE="${VAULT_NAMESPACE:-banking}"

# 1. Enable secrets engines (idempotent)
vault secrets enable -path=secret kv-v2         2>/dev/null || echo "Already enabled"
vault secrets enable database                    2>/dev/null || echo "Already enabled"
vault secrets enable pki                         2>/dev/null || echo "Already enabled"
vault secrets enable transit                     2>/dev/null || echo "Already enabled"

# 2. Enable Kubernetes auth
vault auth enable kubernetes 2>/dev/null || echo "Already enabled"

K8S_HOST=$(kubectl config view --raw --minify --flatten -o jsonpath='{.clusters[].cluster.server}')
K8S_CA=$(kubectl get secret -n infra vault-sa-token \
  -o jsonpath='{.data.ca\.crt}' | base64 --decode)
SA_JWT=$(kubectl get secret -n infra vault-sa-token \
  -o jsonpath='{.data.token}' | base64 --decode)

vault write auth/kubernetes/config \
  token_reviewer_jwt="$SA_JWT" \
  kubernetes_host="$K8S_HOST" \
  kubernetes_ca_cert="$K8S_CA"

# 3. Write policy and create K8s auth role
vault policy write banking-platform /vault/policies/banking-policy.hcl

vault write auth/kubernetes/role/banking-platform \
  bound_service_account_names=banking-platform-sa \
  bound_service_account_namespaces=banking \
  policies=banking-platform \
  ttl=1h

# 4. Configure PostgreSQL dynamic credentials
vault write database/config/banking-postgres \
  plugin_name=postgresql-database-plugin \
  allowed_roles="banking-app-role" \
  connection_url="postgresql://{{username}}:{{password}}@${POSTGRES_HOST}:5432/banking_db" \
  username="${POSTGRES_ADMIN_USER}" \
  password="${POSTGRES_ADMIN_PASSWORD}"

vault write database/roles/banking-app-role \
  db_name=banking-postgres \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
    GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO \"{{name}}\"; \
    GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO \"{{name}}\";" \
  default_ttl="1h" \
  max_ttl="24h"

# 5. Create PII transit encryption key (AES-256-GCM96)
vault write transit/keys/banking-pii type=aes256-gcm96

# 6. Seed initial KV secrets (replace CHANGE_ME values before go-live)
vault kv put secret/banking-platform/jwt \
  secret-key="CHANGE_ME_JWT_SECRET_256_BITS"

vault kv put secret/banking-platform/kafka \
  sasl-username="banking-producer" \
  sasl-password="CHANGE_ME_KAFKA_PASSWORD"

vault kv put secret/banking-platform/external-apis \
  kyc-api-key="CHANGE_ME_KYC_KEY" \
  payment-rails-api-key="CHANGE_ME_RAILS_KEY"

echo "✅ Vault initialization complete."
```

---

## Kubernetes Service Account (Required for Vault Auth)

```yaml
# infra/k8s/serviceaccount.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: banking-platform-sa
  namespace: banking
  annotations:
    # On EKS — IRSA annotation added via Terraform
    # eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT_ID:role/banking-platform-role
    # On GKE — Workload Identity annotation
    # iam.gke.io/gcp-service-account: banking-platform-gsa@PROJECT.iam.gserviceaccount.com
```

---

## Vault Agent Sidecar Injection (Kubernetes)

The Vault Agent sidecar is the recommended approach for Kubernetes — it authenticates with Vault using the pod's ServiceAccount token and writes secrets as files into a shared `emptyDir` volume.

```yaml
# In infra/k8s/deployment.yml — add these annotations to pod template
metadata:
  annotations:
    vault.hashicorp.com/agent-inject: "true"
    vault.hashicorp.com/role: "banking-platform"          # K8s auth role created in init-vault.sh
    vault.hashicorp.com/agent-inject-secret-db: "database/creds/banking-app-role"
    vault.hashicorp.com/agent-inject-template-db: |
      {{- with secret "database/creds/banking-app-role" -}}
      spring.datasource.username={{ .Data.username }}
      spring.datasource.password={{ .Data.password }}
      {{- end }}
    vault.hashicorp.com/agent-inject-secret-app: "secret/data/banking-platform/jwt"
    vault.hashicorp.com/agent-inject-template-app: |
      {{- with secret "secret/data/banking-platform/jwt" -}}
      app.jwt.secret={{ .Data.data.secret-key }}
      {{- end }}
    vault.hashicorp.com/agent-inject-secret-kafka: "secret/data/banking-platform/kafka"
    vault.hashicorp.com/agent-inject-template-kafka: |
      {{- with secret "secret/data/banking-platform/kafka" -}}
      spring.kafka.properties.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="{{ .Data.data.sasl-username }}" password="{{ .Data.data.sasl-password }}";
      {{- end }}
    vault.hashicorp.com/agent-pre-populate-only: "false"   # Keep sidecar running for lease renewal
    vault.hashicorp.com/secret-volume-path: "/vault/secrets"
```

```yaml
# Spring Boot reads Vault-injected files as additional property sources
# In application-kubernetes.yml:
spring:
  config:
    import:
      - "optional:file:/vault/secrets/db"
      - "optional:file:/vault/secrets/app"
      - "optional:file:/vault/secrets/kafka"
```

---

## Spring Cloud Vault (Alternative to Vault Agent)

Spring Cloud Vault authenticates directly from the application using the pod's ServiceAccount token.

```yaml
# In application-kubernetes.yml
spring:
  cloud:
    vault:
      enabled: true
      uri: http://vault.infra.svc.cluster.local:8200
      authentication: KUBERNETES
      kubernetes:
        role: banking-platform
        service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token
      kv:
        enabled: true
        backend: secret
        application-name: banking-platform   # reads secret/data/banking-platform/application
      database:
        enabled: true
        backend: database
        role: banking-app-role               # reads database/creds/banking-app-role → overwrites datasource.username/password
      config:
        lifecycle:
          enabled: true                      # Renews leases automatically (critical for dynamic creds)
          min-renewal: 10s
          expiry-threshold: 1m
```

```groovy
// build.gradle — add Spring Cloud Vault
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-vault-config'
    implementation 'org.springframework.cloud:spring-cloud-vault-config-databases'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:2023.0.3"
    }
}
```

---

## Dynamic Database Credentials — How It Works

```
Pod starts → Vault Agent (or Spring Cloud Vault) authenticates via K8s SA token
         → Vault verifies SA token with K8s API server
         → Vault issues ephemeral DB credentials (TTL: 1h, max: 24h)
         → Spring datasource.username / datasource.password are set
         → Before TTL expires, Spring Cloud Vault lease lifecycle renews automatically
         → When pod terminates, Vault revokes the credentials immediately
```

```sql
-- What Vault executes in PostgreSQL for each new credential lease:
CREATE ROLE "v-kubernet-banking-xK7mNp" WITH LOGIN PASSWORD 'A3bCdE...'
  VALID UNTIL '2026-03-04 15:00:00+00';
GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public
  TO "v-kubernet-banking-xK7mNp";
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public
  TO "v-kubernet-banking-xK7mNp";
```

---

## Transit Encryption — PII Fields

Use Vault's transit engine to encrypt PII (SSN, account numbers, etc.) before storing in the database. The encryption key `banking-pii` never leaves Vault.

```java
// src/main/java/com/banking/platform/security/VaultTransitService.java
@Service
@RequiredArgsConstructor
public class VaultTransitService {

    private final VaultTemplate vaultTemplate;
    private static final String KEY_NAME = "banking-pii";

    /**
     * Encrypt a plain-text PII value. Returns "vault:v1:Base64EncodedCiphertext".
     * Store this ciphertext in the database — never the raw value.
     */
    public String encrypt(String plaintext) {
        VaultTransitContext context = VaultTransitContext.empty();
        return vaultTemplate.opsForTransit()
                .encrypt(KEY_NAME, Plaintext.of(plaintext), context)
                .getCiphertext();
    }

    /**
     * Decrypt a vault ciphertext back to plaintext.
     * Only call this when you genuinely need the raw value (e.g., display to account owner).
     */
    public String decrypt(String ciphertext) {
        return vaultTemplate.opsForTransit()
                .decrypt(KEY_NAME, Ciphertext.of(ciphertext))
                .asString();
    }

    /**
     * Batch encrypt — for bulk operations (e.g., migration).
     * Vault processes up to 10,000 items per request.
     */
    public List<String> batchEncrypt(List<String> plaintexts) {
        List<Plaintext> inputs = plaintexts.stream()
                .map(Plaintext::of).toList();
        return vaultTemplate.opsForTransit()
                .encrypt(KEY_NAME, inputs).stream()
                .map(VaultEncryptionResult::getCiphertext).toList();
    }
}
```

```java
// Usage in entity converter — transparent to JPA layer
@Converter
@RequiredArgsConstructor
public class PiiEncryptingConverter implements AttributeConverter<String, String> {

    private final VaultTransitService transitService;

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) return null;
        return transitService.encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String ciphertext) {
        if (ciphertext == null) return null;
        return transitService.decrypt(ciphertext);
    }
}

// Apply to entity fields:
@Entity
public class CustomerProfile {
    @Convert(converter = PiiEncryptingConverter.class)
    @Column(name = "ssn_encrypted")
    private String socialSecurityNumber;     // stored as vault:v1:... in DB

    @Convert(converter = PiiEncryptingConverter.class)
    @Column(name = "account_number_encrypted")
    private String accountNumber;
}
```

---

## PKI — mTLS Between Services

```bash
# Configure PKI max TTL and issue role
vault secrets tune -max-lease-ttl=87600h pki

vault write pki/root/generate/internal \
  common_name="banking-platform-ca" \
  ttl=87600h

vault write pki/roles/banking-internal \
  allowed_domains="banking.svc.cluster.local,banking.internal" \
  allow_subdomains=true \
  max_ttl=72h
```

```java
// Request a cert programmatically (Spring Cloud Vault handles this automatically
// when spring.cloud.vault.pki.enabled=true)
@Component
@RequiredArgsConstructor
public class MtlsCertificateManager {

    private final VaultTemplate vaultTemplate;

    public CertificateBundle issueCertificate(String serviceName) {
        VaultCertificateRequest request = VaultCertificateRequest.builder()
                .commonName(serviceName + ".banking.svc.cluster.local")
                .ttl(Duration.ofHours(24))
                .build();
        return vaultTemplate.opsForPki()
                .issueCertificate("banking-internal", request);
    }
}
```

---

## External Secrets Operator — AWS Secrets Manager (EKS)

```yaml
# infra/k8s/externalsecret-eks.yaml
apiVersion: external-secrets.io/v1beta1
kind: ClusterSecretStore
metadata:
  name: aws-secretsmanager
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-east-1
      auth:
        jwt:
          serviceAccountRef:
            name: banking-platform-sa
            namespace: banking
---
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: banking-platform-secrets
  namespace: banking
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secretsmanager
    kind: ClusterSecretStore
  target:
    name: banking-platform-secrets
    creationPolicy: Owner
  data:
    - secretKey: db-password
      remoteRef:
        key: banking-platform/db-password
    - secretKey: jwt-secret
      remoteRef:
        key: banking-platform/jwt-secret
    - secretKey: kafka-password
      remoteRef:
        key: banking-platform/kafka-password
```

---

## External Secrets Operator — GCP Secret Manager (GKE)

```yaml
# infra/k8s/externalsecret-gke.yaml
apiVersion: external-secrets.io/v1beta1
kind: ClusterSecretStore
metadata:
  name: gcp-secretmanager
spec:
  provider:
    gcpsm:
      projectID: banking-platform-prod
      auth:
        workloadIdentity:
          clusterLocation: us-central1
          clusterName: banking-platform-production
          serviceAccountRef:
            name: banking-platform-sa
            namespace: banking
---
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: banking-platform-secrets
  namespace: banking
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: gcp-secretmanager
    kind: ClusterSecretStore
  target:
    name: banking-platform-secrets
    creationPolicy: Owner
  data:
    - secretKey: db-password
      remoteRef:
        key: banking-platform-db-password
    - secretKey: jwt-secret
      remoteRef:
        key: banking-platform-jwt-secret
```

---

## Secret Rotation Checklist

| Secret Type            | Rotation Method                                          | Frequency   |
|------------------------|----------------------------------------------------------|-------------|
| DB credentials (Vault) | Automatic — Vault issues new creds each pod start        | Per pod     |
| JWT signing key        | `vault kv put secret/banking-platform/jwt secret-key=…` | 90 days     |
| Kafka SASL password    | `vault kv put secret/banking-platform/kafka sasl-password=…` | 90 days |
| External API keys      | Rotate in provider dashboard, update Vault KV            | 90 days     |
| Transit key (PII)      | `vault write transit/keys/banking-pii/rotate` (rewrap)   | Annual      |
| PKI root CA            | Re-initialize PKI engine with new root                   | 5 years     |
| Vault Kubernetes role  | TTL 1h auto-renewal — no manual rotation needed          | Automatic   |

```bash
# Rotate transit key (doesn't break existing ciphertexts — Vault supports key versions)
vault write -f transit/keys/banking-pii/rotate

# Rewrap all existing ciphertexts to the latest key version
vault write transit/rewrap/banking-pii \
  ciphertext="vault:v1:8SDd3WHDOjf8A..."
# Returns: vault:v2:... (new version)
```

---

## Vault Kubernetes Debugging

```bash
# Check if Vault Agent sidecar injected correctly
kubectl get pod <pod-name> -n banking -o jsonpath='{.spec.containers[*].name}'
# Should include: vault-agent and vault-agent-init

# View Vault Agent logs
kubectl logs <pod-name> -n banking -c vault-agent

# Check what secrets were injected
kubectl exec <pod-name> -n banking -c banking-platform -- ls /vault/secrets/

# Test Kubernetes auth manually
kubectl exec <pod-name> -n banking -- \
  vault write auth/kubernetes/login \
    role=banking-platform \
    jwt=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)

# Check dynamic DB credential status
vault lease lookup database/creds/banking-app-role/<lease-id>

# Force credential renewal
vault lease renew database/creds/banking-app-role/<lease-id>

# Revoke all leases for a role (use during incident response)
vault lease revoke -prefix database/creds/banking-app-role/
```

---

## 10 Critical Rules

1. **Never mount GCP/AWS service account key files in pods** — use Workload Identity (GKE) or IRSA (EKS).
2. **Never store raw PII in the database** — always pass through `VaultTransitService.encrypt()` first; store only `vault:v1:…` ciphertexts.
3. **Never log credentials** — use `@JsonIgnore`/`@ToString.Exclude` on all secret fields; never log `datasource.password` or JWT secrets.
4. **Enable `lifecycle.enabled: true`** in Spring Cloud Vault config — without this, dynamic DB credentials expire mid-session and crash the app.
5. **Use `database/creds/` (dynamic) for PostgreSQL** — never put the DB password in KV; Vault's PostgreSQL plugin creates and revokes ephemeral users automatically.
6. **Policy least-privilege** — the `banking-platform` policy only grants `read` on `database/creds/`, not `create`/`delete`; only Vault itself manages credential lifecycle.
7. **Rewrap after key rotation** — after `vault write -f transit/keys/banking-pii/rotate`, run a background job to rewrap all stored ciphertexts to the latest key version.
8. **ESO `refreshInterval: 1h`** — External Secrets Operator polls for secret changes; set this to balance freshness vs. Vault/Secrets Manager API rate limits.
9. **Separate Vault namespaces for staging/production** — use Vault Enterprise namespaces or separate Vault clusters; never share the same `banking-platform` K8s role between environments.
10. **Bootstrap Vault with `init-vault.sh` idempotently** — the script uses `2>/dev/null || echo "Already enabled"` guards; safe to re-run after cluster rebuilds without corrupting existing secrets.
