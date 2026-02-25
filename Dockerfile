# Stage 1: Build stage
FROM eclipse-temurin:21-jdk as builder

WORKDIR /workspace

# Copy gradle wrapper and build files
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .

# Copy source code
COPY src src

# Build the application
RUN chmod +x ./gradlew && \
    ./gradlew build -x test --parallel

# Extract the JAR file
RUN mkdir -p build/dependency && \
    cd build/dependency && \
    jar -xf ../libs/*.jar

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache dumb-init

# Create non-root user
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

WORKDIR /app

# Copy dependencies from builder
COPY --from=builder --chown=appuser:appuser /workspace/build/dependency/BOOT-INF/lib /app/lib
COPY --from=builder --chown=appuser:appuser /workspace/build/dependency/BOOT-INF/classes /app/classes
COPY --from=builder --chown=appuser:appuser /workspace/build/dependency/META-INF /app/META-INF

# Set JVM options for performance
ENV JAVA_OPTS="-XX:+UseZGC \
    -XX:+ZGenerational \
    -XX:+UnlockExperimentalVMOptions \
    -Dspring.profiles.active=docker \
    -Dspring.jpa.hibernate.ddl-auto=validate"

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/actuator/health/liveness || exit 1

ENTRYPOINT ["dumb-init", "--"]
CMD ["sh", "-c", "java ${JAVA_OPTS} -cp /app/classes:/app/lib/* com.banking.platform.BankingPlatformApplication"]
