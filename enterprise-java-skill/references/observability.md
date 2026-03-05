# Observability Reference — OpenTelemetry, Micrometer, Prometheus, Grafana, Jaeger

## 1. Dependencies

```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
implementation 'io.micrometer:micrometer-observation'
```

## 2. Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
  tracing:
    sampling:
      probability: 1.0          # 100% in dev; use 0.1 in prod
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/traces
    metrics:
      export:
        enabled: true

logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} traceId=%X{traceId} spanId=%X{spanId} - %msg%n"
```

## 3. Custom Metrics

```java
@Component
@RequiredArgsConstructor
public class AccountMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter accountsCreated;
    private final Timer accountCreationTimer;

    public AccountMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.accountsCreated = Counter.builder("accounts.created.total")
            .description("Total number of accounts created")
            .tag("environment", "prod")
            .register(meterRegistry);
        this.accountCreationTimer = Timer.builder("accounts.creation.duration")
            .description("Time taken to create an account")
            .register(meterRegistry);
    }

    public void recordAccountCreated(AccountType type) {
        meterRegistry.counter("accounts.created.total", "type", type.name()).increment();
    }

    public <T> T timeAccountCreation(Supplier<T> supplier) {
        return accountCreationTimer.record(supplier);
    }
}
```

## 4. Custom Spans

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final Tracer tracer;

    public PaymentDto processPayment(PaymentRequest request) {
        Span span = tracer.nextSpan()
            .name("payment.process")
            .tag("payment.type", request.type().name())
            .tag("payment.currency", request.currency())
            .start();

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            log.info("payment.processing orderId={}", request.orderId());
            PaymentDto result = doProcess(request);
            span.tag("payment.result", "success");
            return result;
        } catch (Exception ex) {
            span.tag("payment.result", "failure");
            span.tag("error", ex.getMessage());
            throw ex;
        } finally {
            span.end();
        }
    }
}
```

## 5. Grafana Dashboard — Key Panels

Define in `grafana/dashboards/service-overview.json`:
- **Request Rate**: `rate(http_server_requests_seconds_count[5m])` by `uri`, `method`, `status`
- **Error Rate**: `rate(http_server_requests_seconds_count{status=~"5.."}[5m])`
- **P99 Latency**: `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))`
- **JVM Heap**: `jvm_memory_used_bytes{area="heap"}`
- **Circuit Breaker State**: `resilience4j_circuitbreaker_state`
- **Kafka Consumer Lag**: `kafka_consumer_fetch_manager_records_lag`

## 6. Alerting Rules (Prometheus)

```yaml
# prometheus/rules/service-alerts.yml
groups:
  - name: account-service
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High 5xx error rate on {{ $labels.service }}"

      - alert: HighLatency
        expr: histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m])) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "P99 latency > 2s on {{ $labels.service }}"

      - alert: KafkaConsumerLag
        expr: kafka_consumer_fetch_manager_records_lag > 10000
        for: 5m
        labels:
          severity: critical
```

## 7. Observability Checklist

- [ ] Prometheus scraping configured (actuator/prometheus endpoint)
- [ ] Trace ID injected into log pattern (`%X{traceId}`)
- [ ] Custom metrics for key business operations
- [ ] Grafana dashboard with error rate, latency P99, JVM, Kafka lag
- [ ] Alerts configured for error rate, latency, and circuit breaker
- [ ] Sampling rate set appropriately per environment
