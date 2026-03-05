#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# Vault Initialization Script
# Run once after Vault pod is deployed in Kubernetes.
# Idempotent — safe to re-run.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
NAMESPACE="${VAULT_NAMESPACE:-banking}"

echo "→ Waiting for Vault to be ready..."
until vault status -address="$VAULT_ADDR" 2>/dev/null | grep -q "Initialized"; do
  sleep 2
done

echo "→ Enabling KV v2 secrets engine..."
vault secrets enable -address="$VAULT_ADDR" -path=secret kv-v2 2>/dev/null || echo "  Already enabled"

echo "→ Enabling database secrets engine..."
vault secrets enable -address="$VAULT_ADDR" database 2>/dev/null || echo "  Already enabled"

echo "→ Enabling PKI secrets engine..."
vault secrets enable -address="$VAULT_ADDR" pki 2>/dev/null || echo "  Already enabled"

echo "→ Enabling transit secrets engine..."
vault secrets enable -address="$VAULT_ADDR" transit 2>/dev/null || echo "  Already enabled"

echo "→ Enabling Kubernetes auth method..."
vault auth enable -address="$VAULT_ADDR" kubernetes 2>/dev/null || echo "  Already enabled"

# Configure Kubernetes auth
K8S_HOST=$(kubectl config view --raw --minify --flatten -o jsonpath='{.clusters[].cluster.server}')
K8S_CA_CERT=$(kubectl get secret -n infra vault-sa-token \
  -o jsonpath='{.data.ca\.crt}' 2>/dev/null | base64 --decode || \
  kubectl config view --raw --minify --flatten -o jsonpath='{.clusters[].cluster.certificate-authority-data}' | base64 --decode)
SA_JWT=$(kubectl get secret -n infra vault-sa-token -o jsonpath='{.data.token}' 2>/dev/null | base64 --decode || echo "")

vault write -address="$VAULT_ADDR" auth/kubernetes/config \
  token_reviewer_jwt="$SA_JWT" \
  kubernetes_host="$K8S_HOST" \
  kubernetes_ca_cert="$K8S_CA_CERT"

echo "→ Writing banking-platform policy..."
vault policy write -address="$VAULT_ADDR" banking-platform /vault/policies/banking-policy.hcl

echo "→ Creating Kubernetes auth role for banking-platform..."
vault write -address="$VAULT_ADDR" auth/kubernetes/role/banking-platform \
  bound_service_account_names=banking-platform-sa \
  bound_service_account_namespaces=banking \
  policies=banking-platform \
  ttl=1h

echo "→ Configuring PostgreSQL dynamic credentials..."
vault write -address="$VAULT_ADDR" database/config/banking-postgres \
  plugin_name=postgresql-database-plugin \
  allowed_roles="banking-app-role" \
  connection_url="postgresql://{{username}}:{{password}}@${POSTGRES_HOST}:5432/banking_db" \
  username="${POSTGRES_ADMIN_USER}" \
  password="${POSTGRES_ADMIN_PASSWORD}"

vault write -address="$VAULT_ADDR" database/roles/banking-app-role \
  db_name=banking-postgres \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO \"{{name}}\"; GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO \"{{name}}\";" \
  default_ttl="1h" \
  max_ttl="24h"

echo "→ Creating transit encryption key for PII..."
vault write -address="$VAULT_ADDR" transit/keys/banking-pii type=aes256-gcm96

echo "→ Seeding initial secrets (replace with actual values)..."
vault kv put -address="$VAULT_ADDR" secret/banking-platform/jwt \
  secret-key="CHANGE_ME_JWT_SECRET_256_BITS"

vault kv put -address="$VAULT_ADDR" secret/banking-platform/kafka \
  sasl-username="banking-producer" \
  sasl-password="CHANGE_ME_KAFKA_PASSWORD"

vault kv put -address="$VAULT_ADDR" secret/banking-platform/external-apis \
  kyc-api-key="CHANGE_ME_KYC_KEY" \
  payment-rails-api-key="CHANGE_ME_RAILS_KEY"

echo "✅ Vault initialization complete."
