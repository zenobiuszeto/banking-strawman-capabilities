---
name: spring-kafka
description: |
  **Spring Kafka Skill**: Production-grade Kafka producers, consumers, event models, dead-letter topics, idempotency, and error handling for Java 21 + Spring Boot 3.x. Use whenever the user wants to publish or consume Kafka messages, design event-driven flows, configure topics, handle consumer failures, set up DLT/DLQ, or implement exactly-once semantics.

  MANDATORY TRIGGERS: Kafka, @KafkaListener, @SendTo, KafkaTemplate, ProducerRecord, ConsumerRecord, @EnableKafka, KafkaAdmin, TopicBuilder, NewTopic, event-driven, message broker, dead letter topic, DLT, DLQ, idempotent consumer, consumer group, offset, partition, Avro, Protobuf, Schema Registry, exactly-once, transactional Kafka, @KafkaTransaction, retry, backoff, ErrorHandlingDeserializer, DefaultErrorHandler, SeekToCurrentErrorHandler, event sourcing, domain event, TransactionEvent, ReportEvent.
---

# Spring Kafka Skill — Producers · Consumers · DLT · Idempotency

You are building event-driven features for a **Java 21 / Spring Boot 3.3+ banking platform** using:
- **Spring Kafka 3.x** with JSON serialization (Jackson)
- **KafkaTemplate** for producing
- **@KafkaListener** for consuming with manual offset acknowledgement
- **DefaultErrorHandler + DeadLetterPublishingRecoverer** for DLT routing
- **Idempotent producers** and **deduplicated consumers**
- Kafka running in KRaft mode (no ZooKeeper)

Every generated file must be **commit-ready** and safe for a high-throughput financial workload.

---

## Topic Design — Naming Convention

```
{env}.{domain}.{entity}.{event-type}

Examples:
  prod.banking.transaction.created
  prod.banking.account.status-changed
  prod.banking.wire.initiated
  prod.banking.transaction.created.DLT      ← Dead Letter Topic suffix

Rules:
  - Lowercase kebab-case
  - Dot separators for hierarchy
  - DLT suffix for dead letter topics
  - Partitions: start with 12 for high-throughput, 3 for low-throughput
  - Replication factor: 3 in production, 1 in local dev
```

---

## Topic Configuration Bean

```java
package com.banking.platform.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // Topic constants — reference these in @KafkaListener and KafkaTemplate calls
    public static final String TRANSACTION_CREATED   = "prod.banking.transaction.created";
    public static final String ACCOUNT_STATUS_CHANGED = "prod.banking.account.status-changed";
    public static final String WIRE_INITIATED        = "prod.banking.wire.initiated";
    public static final String REPORT_GENERATED      = "prod.banking.report.generated";

    @Bean
    public NewTopic transactionCreatedTopic() {
        return TopicBuilder.name(TRANSACTION_CREATED)
                .partitions(12)
                .replicas(3)
                .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000L))  // 7 days
                .config("min.insync.replicas", "2")
                .build();
    }

    @Bean
    public NewTopic accountStatusChangedTopic() {
        return TopicBuilder.name(ACCOUNT_STATUS_CHANGED)
                .partitions(6)
                .replicas(3)
                .config("min.insync.replicas", "2")
                .build();
    }
}
```

---

## Event Model (Domain Events)

```java
package com.banking.platform.transaction.model.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable domain event published when a transaction is successfully created.
 * Consumers: ledger-service, reporting-service, notification-service.
 *
 * Schema evolution: add fields with @JsonProperty + default value only — never remove or rename.
 */
public record TransactionCreatedEvent(

    @JsonProperty("event_id")
    UUID eventId,               // Globally unique — used by consumers for deduplication

    @JsonProperty("correlation_id")
    UUID correlationId,         // Propagated from inbound HTTP request (X-Correlation-Id)

    @JsonProperty("transaction_id")
    UUID transactionId,

    @JsonProperty("account_id")
    UUID accountId,

    @JsonProperty("amount")
    BigDecimal amount,

    @JsonProperty("currency")
    String currency,

    @JsonProperty("transaction_type")
    String transactionType,     // DEBIT | CREDIT | TRANSFER

    @JsonProperty("occurred_at")
    Instant occurredAt,

    @JsonProperty("schema_version")
    int schemaVersion           // Increment on breaking schema changes
) {
    public static TransactionCreatedEvent of(Transaction tx, UUID correlationId) {
        return new TransactionCreatedEvent(
            UUID.randomUUID(),
            correlationId,
            tx.getId(),
            tx.getAccountId(),
            tx.getAmount(),
            tx.getCurrency(),
            tx.getType().name(),
            tx.getCreatedAt(),
            1
        );
    }
}
```

---

## Kafka Producer

```java
package com.banking.platform.transaction.service;

import com.banking.platform.config.KafkaTopicConfig;
import com.banking.platform.transaction.model.event.TransactionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a TransactionCreatedEvent.
     * Key = accountId ensures all events for the same account go to the same partition (ordering).
     */
    public void publishTransactionCreated(TransactionCreatedEvent event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(
            KafkaTopicConfig.TRANSACTION_CREATED,
            null,                               // Let Kafka assign partition via key hash
            event.accountId().toString(),        // Partition key — guarantees per-account ordering
            event
        );
        // Propagate correlation ID as a Kafka header
        record.headers().add(new RecordHeader(
            "X-Correlation-Id",
            event.correlationId().toString().getBytes(StandardCharsets.UTF_8)
        ));

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(record);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("transaction.event.publish.failed topic={} eventId={} error={}",
                        KafkaTopicConfig.TRANSACTION_CREATED, event.eventId(), ex.getMessage(), ex);
                // Rethrow or handle — depends on whether the caller is inside a DB transaction
            } else {
                log.info("transaction.event.published topic={} partition={} offset={} eventId={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        event.eventId());
            }
        });
    }
}
```

---

## Kafka Consumer

```java
package com.banking.platform.ledger.consumer;

import com.banking.platform.config.KafkaTopicConfig;
import com.banking.platform.ledger.service.LedgerService;
import com.banking.platform.transaction.model.event.TransactionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final LedgerService ledgerService;

    @KafkaListener(
        topics = KafkaTopicConfig.TRANSACTION_CREATED,
        groupId = "ledger-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTransactionCreated(
            ConsumerRecord<String, TransactionCreatedEvent> record,
            Acknowledgment ack) {

        // Propagate correlation ID from Kafka header into MDC for log tracing
        var correlationHeader = record.headers().lastHeader("X-Correlation-Id");
        if (correlationHeader != null) {
            MDC.put("correlationId", new String(correlationHeader.value(), StandardCharsets.UTF_8));
        }

        TransactionCreatedEvent event = record.value();
        log.info("transaction.event.received topic={} partition={} offset={} eventId={}",
                record.topic(), record.partition(), record.offset(), event.eventId());

        try {
            // CRITICAL: Consumer must be idempotent — check if already processed
            ledgerService.processTransactionEvent(event);
            ack.acknowledge();     // Manual commit AFTER successful processing
            log.info("transaction.event.processed eventId={}", event.eventId());

        } catch (Exception ex) {
            log.error("transaction.event.processing.failed eventId={}", event.eventId(), ex);
            // Do NOT acknowledge — DefaultErrorHandler will retry, then route to DLT
            throw ex;
        } finally {
            MDC.remove("correlationId");
        }
    }
}
```

---

## Idempotent Consumer Pattern

```java
// In LedgerService — check for duplicate event before processing
@Transactional
public void processTransactionEvent(TransactionCreatedEvent event) {
    // 1. Check deduplication table
    if (processedEventRepository.existsByEventId(event.eventId())) {
        log.warn("transaction.event.duplicate.skipped eventId={}", event.eventId());
        return;   // Idempotent — safe to skip already-processed events
    }

    // 2. Process the business logic
    journalEntryService.createJournalEntry(event);

    // 3. Mark as processed within the same transaction
    processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
}

// ProcessedEvent entity (lightweight dedup table)
@Entity
@Table(name = "processed_events", indexes = @Index(columnList = "event_id", unique = true))
public class ProcessedEvent {
    @Id @UuidGenerator private UUID id;
    @Column(name = "event_id", nullable = false, unique = true) private UUID eventId;
    @Column(name = "processed_at") private Instant processedAt;
    // Purge rows older than 7 days via a scheduled job
}
```

---

## Error Handling & Dead Letter Topic

```java
package com.banking.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        // Route failed messages to {originalTopic}.DLT after exhausting retries
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> {
                log.error("kafka.dlt.routing topic={} key={} error={}",
                        record.topic(), record.key(), ex.getMessage());
                // Route to DLT with same partition as original
                return new org.apache.kafka.common.TopicPartition(
                    record.topic() + ".DLT", record.partition()
                );
            }
        );

        // Exponential backoff: 1s → 2s → 4s → 8s (max 3 retries before DLT)
        var backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(3);
        backOff.setMaxElapsedTime(30_000L);

        var errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Don't retry deserialization errors — they will never succeed
        errorHandler.addNotRetryableExceptions(
            org.springframework.kafka.support.serializer.DeserializationException.class
        );

        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        // Manual acknowledgement — ack only after successful processing
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // Concurrency: one thread per partition (tune based on partition count)
        factory.setConcurrency(3);

        return factory;
    }
}
```

---

## application.yml — Kafka Config

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all                           # Wait for all in-sync replicas
      retries: 3
      properties:
        enable.idempotence: true          # Exactly-once delivery per producer session
        max.in.flight.requests.per.connection: 5
        compression.type: snappy          # Compress in production
        linger.ms: 5                      # Batch up to 5ms for throughput
        batch.size: 32768                 # 32KB batch size
    consumer:
      group-id: banking-platform
      auto-offset-reset: earliest
      enable-auto-commit: false           # Manual commit via Acknowledgment
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.banking.platform.*"
        spring.json.use.type.headers: false
        max.poll.records: 500
        session.timeout.ms: 30000
```

---

## Transactional Outbox Pattern

Use the **Outbox Pattern** to guarantee at-least-once delivery without distributed transactions:

```java
// 1. In the DB transaction: save the entity AND the outbox event atomically
@Transactional
public TransactionResponse createTransaction(CreateTransactionRequest req) {
    Transaction tx = transactionRepository.save(toEntity(req));

    // Save event to outbox table (same DB transaction as the domain write)
    outboxRepository.save(OutboxEvent.builder()
        .aggregateType("Transaction")
        .aggregateId(tx.getId())
        .eventType("TransactionCreated")
        .payload(objectMapper.writeValueAsString(TransactionCreatedEvent.of(tx, correlationId())))
        .build());

    return transactionMapper.toResponse(tx);
}

// 2. Scheduled poller reads unprocessed outbox events and publishes to Kafka
@Scheduled(fixedDelay = 1000)
@Transactional
public void pollAndPublish() {
    outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc().forEach(event -> {
        kafkaTemplate.send(topicFor(event.getEventType()), event.getAggregateId().toString(),
                event.getPayload());
        event.setPublished(true);
    });
}
```

---

## Critical Rules

1. **Always use idempotent consumers** — deduplicate by `eventId` before processing.
2. **Never use `enable.auto.commit=true`** — always commit manually after successful processing.
3. **Always set `acks=all`** for producers in financial workloads — never use `acks=0` or `acks=1`.
4. **Route failures to a DLT** — never silently swallow or discard messages.
5. **Include `eventId` in every event** — consumers need it for deduplication.
6. **Never commit the offset before processing completes** — process first, acknowledge after.
7. **Use the Outbox Pattern** when publishing Kafka events inside a DB transaction.
8. **Monitor consumer lag** — alert when lag exceeds a threshold (Prometheus metric: `kafka_consumer_lag`).
9. **Never remove or rename event fields** — only add optional fields; bump `schemaVersion` on breaks.
10. **Test consumers with `EmbeddedKafka`** — never mock `KafkaTemplate` in integration tests.
