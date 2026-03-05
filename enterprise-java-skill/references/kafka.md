# Kafka Reference — Producers, Consumers, Idempotency, Dead Letter, Monitoring

## 1. Dependencies

```groovy
dependencies {
    implementation 'org.springframework.kafka:spring-kafka'
    implementation 'io.confluent:kafka-avro-serializer:7.6.0'
    // For schema registry — see schema-registry.md
}
```

## 2. Configuration

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      acks: all                  # wait for all replicas
      retries: 3
      properties:
        enable.idempotence: true             # exactly-once producer semantics
        max.in.flight.requests.per.connection: 5
        schema.registry.url: ${SCHEMA_REGISTRY_URL}
        compression.type: lz4
    consumer:
      group-id: ${spring.application.name}
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      enable-auto-commit: false  # manual ack — always in enterprise
      max-poll-records: 50
      properties:
        schema.registry.url: ${SCHEMA_REGISTRY_URL}
        specific.avro.reader: true
    listener:
      ack-mode: MANUAL_IMMEDIATE
      concurrency: 3
```

## 3. Producer

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountEventPublisher {

    private final KafkaTemplate<String, AccountCreatedEvent> kafkaTemplate;

    public void publishAccountCreated(Account account) {
        AccountCreatedEvent event = AccountCreatedEvent.newBuilder()
            .setAccountId(account.getId())
            .setCustomerId(account.getCustomerId())
            .setAccountType(account.getAccountType().name())
            .setTimestamp(Instant.now().toEpochMilli())
            .build();

        kafkaTemplate.send("account.created", account.getId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("kafka.publish.failed topic=account.created key={} error={}",
                        account.getId(), ex.getMessage(), ex);
                } else {
                    log.info("kafka.publish.success topic=account.created key={} partition={} offset={}",
                        account.getId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }
}
```

## 4. Consumer with Idempotency and Dead Letter

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentService paymentService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(
        topics = "payment.initiated",
        groupId = "${spring.application.name}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentInitiated(
            @Payload PaymentInitiatedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        String eventId = event.getEventId().toString();
        log.info("kafka.receive topic=payment.initiated key={} partition={} offset={}", key, partition, offset);

        try {
            // Idempotency check — skip already-processed events
            if (processedEventRepository.existsByEventId(eventId)) {
                log.info("kafka.duplicate.skipped eventId={}", eventId);
                ack.acknowledge();
                return;
            }

            paymentService.processPayment(event);
            processedEventRepository.save(ProcessedEvent.of(eventId, Instant.now()));
            ack.acknowledge();
            log.info("kafka.processed eventId={}", eventId);

        } catch (NonRetryableException ex) {
            log.error("kafka.nonretryable eventId={} error={}", eventId, ex.getMessage(), ex);
            ack.acknowledge(); // Do NOT retry — will go to DLT via error handler
        } catch (Exception ex) {
            log.error("kafka.error eventId={} error={}", eventId, ex.getMessage(), ex);
            // Do NOT ack — let retry policy handle it
            throw ex;
        }
    }

    // Dead letter topic handler
    @KafkaListener(topics = "payment.initiated.DLT")
    public void onDeadLetter(
            @Payload PaymentInitiatedEvent event,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        log.error("kafka.dlt.received topic=payment.initiated.DLT eventId={} error={}",
            event.getEventId(), exceptionMessage);
        // Alert, store in error DB, or trigger manual review workflow
    }
}
```

## 5. Error Handler and Retry Configuration

```java
@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        // Exponential backoff: 1s, 2s, 4s — then DLT
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000);
        backOff.setMultiplier(2.0);

        DeadLetterPublishingRecoverer recoverer =
            new DeadLetterPublishingRecoverer(kafkaTemplate,
                (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", rec.partition()));

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Don't retry validation or business errors
        handler.addNotRetryableExceptions(
            NonRetryableException.class,
            JsonParseException.class,
            SerializationException.class);
        return handler;
    }
}
```

## 6. Kafka Consumer Monitoring

Key Micrometer metrics auto-exposed by spring-kafka:
- `kafka.consumer.fetch.manager.records.lag` — consumer lag per partition
- `kafka.consumer.records.consumed.total` — total records consumed
- Alert if lag exceeds threshold (e.g., > 10,000 records for 5 minutes)

```yaml
# Grafana alert rule (configure in monitoring stack)
# Alert: kafka_consumer_fetch_manager_records_lag > 10000 for 5m
```

## 7. Kafka Checklist

- [ ] `enable.idempotence=true` on all producers
- [ ] `acks=all` on all producers
- [ ] `enable-auto-commit=false` — always manual acknowledgment
- [ ] Idempotency check in every consumer (processedEventRepository or Redis set)
- [ ] Dead letter topic configured for every consumer
- [ ] Non-retryable exceptions listed explicitly
- [ ] Consumer lag monitored and alerted
- [ ] Schema Registry configured — see schema-registry.md
- [ ] Outbox pattern for DB+Kafka dual writes — see resilience.md
