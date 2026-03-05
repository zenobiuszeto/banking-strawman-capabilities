# ─────────────────────────────────────────────────────────────────────────────
# Vault Policy: banking-platform
# Applied to the K8s service account that the Spring Boot app runs as.
# ─────────────────────────────────────────────────────────────────────────────

# Read all secrets for banking-platform
path "secret/data/banking-platform/*" {
  capabilities = ["read", "list"]
}

path "secret/metadata/banking-platform/*" {
  capabilities = ["read", "list"]
}

# Dynamic database credentials (PostgreSQL)
path "database/creds/banking-app-role" {
  capabilities = ["read"]
}

# Dynamic MongoDB credentials
path "database/creds/banking-mongo-role" {
  capabilities = ["read"]
}

# PKI — issue TLS certificates for mTLS between services
path "pki/issue/banking-internal" {
  capabilities = ["create", "update"]
}

# Read PKI CA certificate for trust chain
path "pki/cert/ca" {
  capabilities = ["read"]
}

# Transit engine — encrypt/decrypt PII fields at rest
path "transit/encrypt/banking-pii" {
  capabilities = ["update"]
}
path "transit/decrypt/banking-pii" {
  capabilities = ["update"]
}

# Allow the app to renew its own token
path "auth/token/renew-self" {
  capabilities = ["update"]
}

path "auth/token/lookup-self" {
  capabilities = ["read"]
}
