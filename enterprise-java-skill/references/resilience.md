# Resilience Reference — Resilience4j, Circuit Breaker, Retry, Rate Limiter, Bulkhead

## Overview

Every external call — to another service, a database, a cache, or a third-party API — must be wrapped in a resilience pattern. This reference covers Resilience4j configuration, usage patterns, and testing strategies.

---

## 1. Dependencies

```groovy
// build.gradle
dependencies {
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
    implementation 'io.github.resilience4j:resilience4j-micrometer:2.2.0'
    implementation 'org.springframework.boot:spring-boot-starter-aop' // required for annotations
}
```

---

## 2. Configuration (application.yml)

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        waitDurationInOpenState: 30s
        failureRateThreshold: 50
        slowCallDurationThreshold: 2s
        slowCallRateThreshold: 80
        recordExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - feign.FeignException.ServiceUnavailable
        ignoreExceptions:
          - com.yourorg.exception.BusinessValidationException
    instances:
      paymentService:
        baseConfig: default
        waitDurationInOpenState: 60s     # payments need longer recovery
      inventoryService:
        baseConfig: default

  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
        ignoreExceptions:
          - com.yourorg.exception.ResourceNotFoundException
          - com.yourorg.exception.BusinessValidationException
    instances:
      paymentService:
        baseConfig: default
        maxAttempts: 2              # payments — fewer retries to avoid duplicate charges
      inventoryService:
        baseConfig: default
        maxAttempts: 4

  ratelimiter:
    configs:
      default:
        registerHealthIndicator: true
        limitForPeriod: 100
        limitRefreshPeriod: 1s
        timeoutDuration: 250ms
    instances:
      externalPaymentGateway:
        limitForPeriod: 50          # respect third-party rate limits
        limitRefreshPeriod: 1s

  bulkhead:
    configs:
      default:
        maxConcurrentCalls: 20
        maxWaitDuration: 100ms
    instances:
      paymentService:
        maxConcurrentCalls: 10      # limit concurrency to payment processor

  timelimiter:
    configs:
      default:
        timeoutDuration: 3s
        cancelRunningFuture: true
    instances:
      paymentService:
        timeoutDuration: 10s        # payments need more time
```

---

## 3. Annotation-Based Usage (Preferred)

Apply resilience patterns via annotations in the correct order: `TimeLimiter → Bulkhead → RateLimiter → CircuitBreaker → Retry`.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceClient {

    private final PaymentGatewayFeignClient gatewayClient;

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentService")
    @Bulkhead(name = "paymentService", type = Bulkhead.Type.SEMAPHORE)
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("payment.attempt orderId={} amount={}", request.orderId(), request.amount());
        return gatewayClient.charge(request);
    }

    // Fallback must have the same signature + a Throwable parameter at the end
    private PaymentResponse paymentFallback(PaymentRequest request, Throwable ex) {
        log.warn("payment.fallback orderId={} reason={}", request.orderId(), ex.getMessage());
        // Return a graceful degraded response or rethrow a domain exception
        throw new PaymentUnavailableException("Payment service temporarily unavailable, please retry");
    }
}
```

---

## 4. Programmatic Usage (for Complex Flows)

When you need more control — e.g., wrapping a lambda or adding custom event listeners:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryClient {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final InventoryFeignClient inventoryClient;

    public StockResponse checkStock(String productId) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("inventoryService");
        Retry retry = retryRegistry.retry("inventoryService");

        // Add event listeners for metrics/alerting
        cb.getEventPublisher()
            .onStateTransition(e -> log.warn("circuit.transition service=inventoryService from={} to={}",
                e.getStateTransition().getFromState(), e.getStateTransition().getToState()));

        Supplier<StockResponse> supplier = CircuitBreaker.decorateSupplier(cb,
            () -> inventoryClient.getStock(productId));
        supplier = Retry.decorateSupplier(retry, supplier);

        return Try.ofSupplier(supplier)
            .recover(CallNotPermittedException.class, ex -> StockResponse.unknown(productId))
            .getOrElseThrow(ex -> new InventoryUnavailableException("Inventory check failed", ex));
    }
}
```

---

## 5. Outbox Pattern — Reliable Event Publishing

When a database write must be paired with a Kafka event (no dual-write risk):

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;

    @Transactional  // single transaction: save order + outbox entry
    public Order createOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(Order.from(request));

        outboxRepository.save(OutboxEvent.builder()
            .aggregateId(order.getId())
            .aggregateType("ORDER")
            .eventType("ORDER_CREATED")
            .payload(JsonUtils.toJson(OrderCreatedEvent.from(order)))
            .status(OutboxStatus.PENDING)
            .createdAt(Instant.now())
            .build());

        log.info("order.created orderId={} customerId={}", order.getId(), order.getCustomerId());
        return order;
    }
}

// Separate outbox poller publishes pending events to Kafka
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPoller {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> events = outboxRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING);
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(topicFor(event.getAggregateType()), event.getAggregateId(), event.getPayload().getBytes());
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                log.info("outbox.published eventId={} type={}", event.getId(), event.getEventType());
            } catch (Exception ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                log.error("outbox.publish.failed eventId={} attempt={}", event.getId(), event.getRetryCount(), ex);
            }
        }
    }
}
```

---

## 6. Health Indicators

Resilience4j automatically registers health indicators when `registerHealthIndicator: true`. Access via:
- `GET /actuator/health` — includes circuit breaker states
- `GET /actuator/circuitbreakers` — full state detail
- `GET /actuator/retryevents` — recent retry events

Integrate these into your Kubernetes liveness/readiness probes.

---

## 7. Resilience Testing

```java
@SpringBootTest
@Slf4j
class PaymentServiceClientResilienceTest {

    @Autowired PaymentServiceClient client;
    @Autowired CircuitBreakerRegistry cbRegistry;
    @MockBean PaymentGatewayFeignClient gatewayClient;

    @Test
    void circuitBreaker_opensAfterThresholdFailures() {
        // Force enough failures to open the circuit
        when(gatewayClient.charge(any())).thenThrow(new IOException("Gateway timeout"));

        for (int i = 0; i < 10; i++) {
            assertThrows(PaymentUnavailableException.class,
                () -> client.processPayment(testRequest()));
        }

        CircuitBreaker cb = cbRegistry.circuitBreaker("paymentService");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void retry_attemptsConfiguredMaxTimes() {
        when(gatewayClient.charge(any())).thenThrow(new IOException("timeout"));

        assertThrows(PaymentUnavailableException.class,
            () -> client.processPayment(testRequest()));

        // Verify retry attempted exactly maxAttempts times
        verify(gatewayClient, times(2)).charge(any());
    }
}
```

---

## 8. Resilience Checklist for Every External Call

- [ ] Circuit breaker configured with appropriate `failureRateThreshold` and `waitDurationInOpenState`
- [ ] Retry configured — fewer retries for payment/financial operations to prevent duplicate charges
- [ ] Fallback method defined and returns a graceful degraded response
- [ ] Bulkhead configured for high-concurrency external dependencies
- [ ] Rate limiter configured for third-party APIs with known rate limits
- [ ] Outbox pattern used for Kafka + DB dual writes
- [ ] Health indicators registered and wired into K8s readiness probe
- [ ] Resilience events logged (circuit state transitions, retry exhaustion)
- [ ] Resilience4j metrics flowing to Prometheus (auto-configured via Micrometer)
