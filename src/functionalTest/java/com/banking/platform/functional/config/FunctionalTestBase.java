package com.banking.platform.functional.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Base class for all functional journey tests.
 *
 * Boots the full Spring Boot application on a random port using an in-memory
 * H2 database (PostgreSQL-compatible mode) with Redis, MongoDB, Kafka, Temporal
 * and Batch schedulers all disabled via the "functionaltest" profile.
 *
 * A WireMock server is started alongside to stub any downstream HTTP calls
 * (KYC, notifications, payment network, fraud detection, routing lookup).
 *
 * RestAssured is configured to call the live Spring Boot server on the random port.
 *
 * To run against a real deployment instead:
 *   ./gradlew functionalTest -PbaseUrl=https://staging.bank.internal
 *   (set functional.baseUrl system property to skip in-process boot)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("functionaltest")
@TestPropertySource(properties = {
        "temporal.enabled=false",
        "batch.scheduler.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class FunctionalTestBase {

    protected static final int WIREMOCK_PORT = Integer.parseInt(
            System.getProperty("functional.wiremock.port", "8089"));

    protected static WireMockServer wireMockServer;

    @LocalServerPort
    protected int serverPort;

    protected RequestSpecification requestSpec;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(
                WireMockConfiguration.options()
                        .port(WIREMOCK_PORT)
                        .usingFilesUnderClasspath("wiremock")
                        .notifier(new com.github.tomakehurst.wiremock.common.Slf4jNotifier(false)));
        wireMockServer.start();
        WireMock.configureFor("localhost", WIREMOCK_PORT);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void configureRestAssured() {
        // Point RestAssured at the in-process Spring Boot server
        requestSpec = new RequestSpecBuilder()
                .setBaseUri("http://localhost")
                .setPort(serverPort)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .log(LogDetail.METHOD)
                .log(LogDetail.URI)
                .build();

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        wireMockServer.resetAll();   // fresh stubs for each test
    }

    /** Expose WireMock URL for stub assertions. */
    protected String wiremockUrl() {
        return "http://localhost:" + WIREMOCK_PORT;
    }
}
