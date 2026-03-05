#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# Register banking-platform config in Consul KV
# Run after Consul is deployed.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

CONSUL_ADDR="${CONSUL_HTTP_ADDR:-http://localhost:8500}"

echo "→ Waiting for Consul..."
until curl -sf "$CONSUL_ADDR/v1/status/leader" > /dev/null; do sleep 2; done

echo "→ Uploading banking-platform config to Consul KV..."
# Spring Cloud Consul expects the config at: config/<app-name>/data
consul kv put -http-addr="$CONSUL_ADDR" \
  "config/banking-platform/data" \
  @"$(dirname "$0")/banking-platform-config.yml"

echo "→ Registering banking-platform service..."
curl -sf -X PUT "$CONSUL_ADDR/v1/agent/service/register" \
  -H "Content-Type: application/json" \
  -d '{
    "ID": "banking-platform",
    "Name": "banking-platform",
    "Tags": ["v1", "java", "spring-boot"],
    "Port": 8080,
    "Check": {
      "HTTP": "http://localhost:8080/api/actuator/health",
      "Interval": "10s",
      "Timeout": "5s",
      "DeregisterCriticalServiceAfter": "30s"
    }
  }'

echo "✅ Consul registration complete."
