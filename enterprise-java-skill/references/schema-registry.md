# Schema Registry Reference — Avro, Protobuf, Confluent Schema Registry

## 1. Overview

All Kafka events in the enterprise platform use schemas registered with Confluent Schema Registry. This enforces data contracts between producers and consumers, prevents breaking changes, and enables schema evolution.

## 2. Dependencies

```groovy
// build.gradle
plugins {
    id 'com.github.davidmc24.gradle.plugin.avro' version '1.9.1'
}

repositories {
    maven { url 'https://packages.confluent.io/maven/' }
}

dependencies {
    implementation 'io.confluent:kafka-avro-serializer:7.6.0'
    implementation 'io.confluent:kafka-schema-registry-client:7.6.0'
    implementation 'org.apache.avro:avro:1.11.3'
}
```

## 3. Avro Schema Definition

Place schemas in `src/main/avro/`. The Avro Gradle plugin auto-generates Java classes.

```json
// src/main/avro/AccountCreatedEvent.avsc
{
  "type": "record",
  "name": "AccountCreatedEvent",
  "namespace": "com.yourorg.events.account",
  "doc": "Published when a new account is created. Do not remove or rename fields without a schema evolution plan.",
  "fields": [
    { "name": "eventId",     "type": "string",  "doc": "Unique event ID for idempotency" },
    { "name": "accountId",   "type": "string",  "doc": "The new account's UUID" },
    { "name": "customerId",  "type": "string",  "doc": "The owning customer's UUID" },
    { "name": "accountType", "type": { "type": "enum", "name": "AccountType",
                                       "symbols": ["CHECKING", "SAVINGS", "MONEY_MARKET"] } },
    { "name": "timestamp",   "type": "long",    "logicalType": "timestamp-millis",
                              "doc": "Event creation time in epoch milliseconds" },
    { "name": "version",     "type": "string",  "default": "1.0",
                              "doc": "Schema version for debugging" }
  ]
}
```

## 4. Schema Evolution Rules

| Change Type | Backward Compatible | Forward Compatible | Action |
|---|---|---|---|
| Add field with default | ✅ Yes | ✅ Yes | Safe — add with `"default"` |
| Add field without default | ❌ No | ✅ Yes | Requires full compatibility mode change |
| Remove optional field | ✅ Yes | ❌ No | Deprecate first, then remove in next version |
| Rename field | ❌ No | ❌ No | Use `"aliases"` for backward compat |
| Change field type | ❌ No | ❌ No | Never — add new field instead |

**Platform rule: All schemas must use BACKWARD_TRANSITIVE compatibility.**

## 5. Schema Registration in CI

Register/validate schemas in CI before any Kafka code deploys:

```yaml
# .github/workflows/schema-validation.yml
- name: Validate Avro Schemas
  run: |
    ./gradlew generateAvroJava  # verify schemas compile
    # Register with registry (dev environment)
    for schema in src/main/avro/*.avsc; do
      curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" \
        --data "{\"schema\": $(jq -c . < $schema | jq -Rs .)}" \
        "$SCHEMA_REGISTRY_URL/subjects/$(basename $schema .avsc)-value/versions"
    done
```

## 6. Producer Configuration

```yaml
spring:
  kafka:
    producer:
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      properties:
        schema.registry.url: ${SCHEMA_REGISTRY_URL}
        auto.register.schemas: false    # NEVER auto-register in prod — schemas go through CI
        use.latest.version: true
```

## 7. Consumer Configuration

```yaml
spring:
  kafka:
    consumer:
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      properties:
        schema.registry.url: ${SCHEMA_REGISTRY_URL}
        specific.avro.reader: true       # deserialize to generated Java class (not GenericRecord)
```

## 8. Schema Registry Checklist

- [ ] All Kafka event schemas defined in Avro or Protobuf files under `src/main/avro/` or `src/main/proto/`
- [ ] `auto.register.schemas=false` in all non-local environments
- [ ] Schema compatibility set to `BACKWARD_TRANSITIVE` in Schema Registry
- [ ] Schema validation step in CI before deploy
- [ ] Schema changes go through PR review — treat like a database migration
- [ ] Breaking changes versioned as a new topic (e.g., `account.created.v2`)
