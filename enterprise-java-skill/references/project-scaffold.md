# Project Scaffold Reference — New Microservice from Scratch

## 1. Scaffold Command

```bash
# Use Spring Initializr with Gradle
curl https://start.spring.io/starter.tgz \
  -d type=gradle-project \
  -d language=java \
  -d bootVersion=3.3.4 \
  -d baseDir=account-service \
  -d groupId=com.yourorg \
  -d artifactId=account-service \
  -d javaVersion=21 \
  -d dependencies=web,security,oauth2-resource-server,data-jpa,flyway,kafka,redis,actuator,validation,lombok \
  | tar -xzvf -
```

## 2. Required Files Checklist for a New Service

```
account-service/
├── src/
│   ├── main/
│   │   ├── java/com/yourorg/accountservice/
│   │   │   ├── AccountServiceApplication.java
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java           # OAuth2 resource server
│   │   │   │   ├── CacheConfig.java              # Redis cache manager
│   │   │   │   └── ResilienceConfig.java         # Resilience4j beans (if needed)
│   │   │   ├── controller/
│   │   │   │   └── AccountController.java
│   │   │   ├── service/
│   │   │   │   └── AccountService.java
│   │   │   ├── repository/
│   │   │   │   └── AccountRepository.java
│   │   │   ├── model/
│   │   │   │   ├── entity/Account.java
│   │   │   │   └── dto/CreateAccountRequest.java, AccountDto.java
│   │   │   ├── mapper/AccountMapper.java
│   │   │   └── exception/GlobalExceptionHandler.java
│   │   ├── avro/
│   │   │   └── AccountCreatedEvent.avsc          # Kafka event schema
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-dev.yml
│   │       └── db/migration/
│   │           └── V1__create_accounts_table.sql
│   └── test/
│       ├── java/com/yourorg/accountservice/
│       │   ├── architecture/ArchitectureTest.java
│       │   ├── controller/AccountControllerTest.java
│       │   ├── service/AccountServiceTest.java
│       │   └── integration/AccountIntegrationTest.java
│       └── resources/application-test.yml
├── build.gradle
├── Dockerfile
├── docker-compose.yml
├── helm/account-service/
│   ├── Chart.yaml
│   ├── values.yaml
│   ├── values-dev.yaml
│   ├── values-uat.yaml
│   └── values-prod.yaml
└── .github/workflows/
    ├── ci.yml
    ├── deploy-dev.yml
    ├── deploy-uat.yml
    └── deploy-prod.yml
```

## 3. Application Main Class

```java
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class AccountServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
```

## 4. Base application.yml Template

```yaml
spring:
  application:
    name: account-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/accounts}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    open-in-view: false          # CRITICAL: disable OSIV to prevent connection leaks
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.format_sql: false
  flyway:
    enabled: true
    validate-on-migrate: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OAUTH2_ISSUER_URI}
  threads:
    virtual:
      enabled: true

server:
  port: 8080
  shutdown: graceful
  error:
    include-message: on-param     # only in dev; restrict in prod

management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus
  endpoint:
    health:
      probes:
        enabled: true
  tracing:
    sampling:
      probability: 1.0

logging:
  level:
    com.yourorg: INFO
    org.hibernate.SQL: WARN

app:
  security:
    allowed-origins: "*"          # tighten per environment
```

## 5. Dockerfile

```dockerfile
# Multi-stage: build with Gradle, run on minimal JRE
FROM gradle:8-jdk21 AS builder
WORKDIR /app
COPY --chown=gradle:gradle . .
RUN gradle build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8080 50051
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

## 6. New Service Checklist

- [ ] Spring Initializr used as starting point
- [ ] All required config files created (security, cache, resilience)
- [ ] `open-in-view: false` set in application.yml
- [ ] GlobalExceptionHandler with ProblemDetail
- [ ] ArchitectureTest with layering rules
- [ ] Flyway V1 migration script for initial schema
- [ ] Kafka event Avro schema defined
- [ ] Dockerfile with non-root user
- [ ] Helm chart with dev/uat/prod values
- [ ] GitHub Actions CI, dev, uat, prod workflows
