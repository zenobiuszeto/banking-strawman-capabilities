# Multi-Module Gradle Reference

## 1. Project Structure

```
enterprise-platform/
├── settings.gradle
├── build.gradle              # root — shared config only
├── gradle/
│   └── libs.versions.toml   # version catalog
├── platform-bom/            # Bill of Materials module
│   └── build.gradle
├── common-lib/              # Shared DTOs, exceptions, utils
│   └── src/...
├── account-service/         # Microservice
│   └── src/...
├── payment-service/
│   └── src/...
└── notification-service/
    └── src/...
```

## 2. Version Catalog (libs.versions.toml)

```toml
# gradle/libs.versions.toml
[versions]
spring-boot         = "3.3.4"
spring-cloud        = "2023.0.3"
resilience4j        = "2.2.0"
confluent           = "7.6.0"
testcontainers      = "1.20.1"
mapstruct           = "1.6.0"
archunit            = "1.3.0"
flyway              = "10.15.0"

[libraries]
spring-boot-starter-web         = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-security    = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-data-jpa    = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-kafka       = { module = "org.springframework.kafka:spring-kafka" }
resilience4j-spring-boot3       = { module = "io.github.resilience4j:resilience4j-spring-boot3", version.ref = "resilience4j" }
kafka-avro-serializer           = { module = "io.confluent:kafka-avro-serializer", version.ref = "confluent" }
mapstruct                       = { module = "org.mapstruct:mapstruct", version.ref = "mapstruct" }
mapstruct-processor             = { module = "org.mapstruct:mapstruct-processor", version.ref = "mapstruct" }
testcontainers-bom              = { module = "org.testcontainers:testcontainers-bom", version.ref = "testcontainers" }
archunit                        = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }
flyway-core                     = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }

[bundles]
spring-web     = ["spring-boot-starter-web", "spring-boot-starter-security"]
observability  = ["micrometer-registry-prometheus", "spring-boot-starter-actuator"]

[plugins]
spring-boot        = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency  = { id = "io.spring.dependency-management", version.ref = "spring-dependency-mgmt" }
avro               = { id = "com.github.davidmc24.gradle.plugin.avro", version = "1.9.1" }
sonarqube          = { id = "org.sonarqube", version = "5.0.0.4638" }
```

## 3. Root build.gradle — Shared Config

```groovy
// Root build.gradle — shared config only, no application code
subprojects {
    apply plugin: 'java'
    apply plugin: 'checkstyle'
    apply plugin: 'jacoco'

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
        maven { url 'https://packages.confluent.io/maven/' }
    }

    dependencies {
        compileOnly 'org.projectlombok:lombok'
        annotationProcessor 'org.projectlombok:lombok'
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
    }

    test {
        useJUnitPlatform()
        jvmArgs '-XX:+EnableDynamicAgentLoading'  // for Java 21 compatibility
    }

    // All services must pass quality checks before build
    build.dependsOn jacocoTestCoverageVerification
}
```

## 4. Service build.gradle

```groovy
// account-service/build.gradle
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency)
}

dependencies {
    implementation project(':common-lib')
    implementation libs.bundles.spring.web
    implementation libs.bundles.observability
    implementation libs.spring.boot.starter.data.jpa
    implementation libs.resilience4j.spring.boot3
    implementation libs.flyway.core
    implementation libs.mapstruct
    annotationProcessor libs.mapstruct.processor

    testImplementation libs.archunit
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation 'org.testcontainers:postgresql'
    testImplementation 'org.testcontainers:kafka'
}
```

## 5. Common Library Structure

```
common-lib/src/main/java/com/yourorg/common/
├── dto/
│   ├── PagedResponse.java      # shared pagination envelope
│   └── ErrorResponse.java      # ProblemDetail wrapper
├── exception/
│   ├── ResourceNotFoundException.java
│   ├── BusinessValidationException.java
│   └── NonRetryableException.java
├── security/
│   ├── AuthenticatedUser.java
│   └── SecurityContextHelper.java
├── audit/
│   ├── AuditAction.java        # annotation
│   └── AuditAspect.java
└── util/
    ├── JsonUtils.java
    └── IdGenerator.java
```

## 6. Multi-Module Checklist

- [ ] Version catalog (`libs.versions.toml`) used for all dependency versions
- [ ] No version numbers in service `build.gradle` — all from catalog
- [ ] Common exceptions, DTOs, and security helpers in `common-lib`
- [ ] Root `build.gradle` enforces shared quality plugins across all subprojects
- [ ] All services use `implementation project(':common-lib')` — not copy-paste
