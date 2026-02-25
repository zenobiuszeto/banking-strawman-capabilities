package com.banking.platform.functional.journey;

import com.banking.platform.functional.config.FunctionalTestBase;
import com.banking.platform.functional.util.TestDataFactory;
import com.banking.platform.functional.wiremock.BankingPlatformStubs;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Functional test: Wire Transfer Full Lifecycle
 *
 * Domestic journey:
 *  1. Initiate domestic wire
 *  2. Approve wire (INITIATED → APPROVED)
 *  3. Complete wire (APPROVED → COMPLETED)
 *
 * International journey:
 *  4. Initiate international wire
 *  5. Cancel wire (INITIATED → CANCELLED)
 *
 * Lookup + pagination:
 *  6. Get wire by ID
 *  7. Get wire by reference
 *  8. List wires for account (paginated)
 *
 * Error cases:
 *  9. International wire without SWIFT → 400
 * 10. Wire not found → 404
 */
@DisplayName("Journey: Wire Transfers")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WireJourneyTest extends FunctionalTestBase {

    private static UUID accountId;
    private static UUID domesticWireId;
    private static UUID internationalWireId;
    private static String domesticWireReference;

    @BeforeAll
    static void init() {
        accountId = UUID.randomUUID();
    }

    @BeforeEach
    void setupStubs() {
        BankingPlatformStubs.stubPaymentNetworkWireSubmit("WIRE-NET-REF-" + System.currentTimeMillis());
        BankingPlatformStubs.stubEmailNotification();
    }

    @Test
    @Order(1)
    @DisplayName("1. Initiate domestic wire → 201 Created with $25 fee")
    void initiateDomesticWire() {
        Response response = given(requestSpec)
                .body(TestDataFactory.initiateDomesticWireRequest(accountId))
                .when()
                .post("/api/v1/wire/transfers")
                .then()
                .statusCode(201)
                .body("wireType", equalTo("DOMESTIC"))
                .body("status", equalTo("INITIATED"))
                .body("amount", comparesEqualTo(new BigDecimal("1500.00").floatValue()))
                .body("fee", comparesEqualTo(new BigDecimal("25.00").floatValue()))
                .body("wireReferenceNumber", notNullValue())
                .extract().response();

        domesticWireId = UUID.fromString(response.jsonPath().getString("id"));
        domesticWireReference = response.jsonPath().getString("wireReferenceNumber");
        assertThat(domesticWireId).isNotNull();
        assertThat(domesticWireReference).startsWith("WIRE-");
        System.out.println("[Wire] Domestic wire: " + domesticWireId + " / " + domesticWireReference);
    }

    @Test
    @Order(2)
    @DisplayName("2. Approve domestic wire → status APPROVED")
    void approveDomesticWire() {
        assertThat(domesticWireId).isNotNull();

        given(requestSpec)
                .when()
                .post("/api/v1/wire/transfers/{id}/approve", domesticWireId)
                .then()
                .statusCode(200)
                .body("status", equalTo("APPROVED"));
    }

    @Test
    @Order(3)
    @DisplayName("3. Complete domestic wire → status COMPLETED")
    void completeDomesticWire() {
        assertThat(domesticWireId).isNotNull();

        given(requestSpec)
                .body(Map.of("networkReference", "FED-" + System.currentTimeMillis()))
                .when()
                .post("/api/v1/wire/transfers/{id}/complete", domesticWireId)
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"));
    }

    @Test
    @Order(4)
    @DisplayName("4. Initiate international wire → 201 Created with $45 fee")
    void initiateInternationalWire() {
        Response response = given(requestSpec)
                .body(TestDataFactory.initiateInternationalWireRequest(accountId))
                .when()
                .post("/api/v1/wire/transfers")
                .then()
                .statusCode(201)
                .body("wireType", equalTo("INTERNATIONAL"))
                .body("status", equalTo("INITIATED"))
                .body("fee", comparesEqualTo(new BigDecimal("45.00").floatValue()))
                .body("beneficiarySwiftCode", equalTo("DEUTDEDB"))
                .extract().response();

        internationalWireId = UUID.fromString(response.jsonPath().getString("id"));
        assertThat(internationalWireId).isNotNull();
        System.out.println("[Wire] International wire: " + internationalWireId);
    }

    @Test
    @Order(5)
    @DisplayName("5. Cancel international wire → status CANCELLED")
    void cancelInternationalWire() {
        assertThat(internationalWireId).isNotNull();

        given(requestSpec)
                .when()
                .post("/api/v1/wire/transfers/{id}/cancel", internationalWireId)
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    @Test
    @Order(6)
    @DisplayName("6. Get wire by ID → 200 OK")
    void getWireById() {
        assertThat(domesticWireId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/wire/transfers/{id}", domesticWireId)
                .then()
                .statusCode(200)
                .body("id", equalTo(domesticWireId.toString()))
                .body("status", equalTo("COMPLETED"));
    }

    @Test
    @Order(7)
    @DisplayName("7. Get wire by reference number → 200 OK")
    void getWireByReference() {
        assertThat(domesticWireReference).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/wire/transfers/reference/{ref}", domesticWireReference)
                .then()
                .statusCode(200)
                .body("wireReferenceNumber", equalTo(domesticWireReference));
    }

    @Test
    @Order(8)
    @DisplayName("8. List wires for account → paginated results with ≥2 wires")
    void listWiresForAccount() {
        assertThat(accountId).isNotNull();

        given(requestSpec)
                .queryParam("page", 0)
                .queryParam("size", 20)
                .when()
                .get("/api/v1/wire/transfers/account/{accountId}", accountId)
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(2)))
                .body("totalElements", greaterThanOrEqualTo(2));
    }

    @Test
    @Order(9)
    @DisplayName("9. International wire without SWIFT code → 400 Bad Request")
    void internationalWireWithoutSwift() {
        Map<String, Object> invalid = new java.util.HashMap<>(
                TestDataFactory.initiateInternationalWireRequest(accountId));
        invalid.remove("beneficiarySwiftCode");
        invalid.put("beneficiarySwiftCode", "");  // blank SWIFT

        given(requestSpec)
                .body(invalid)
                .when()
                .post("/api/v1/wire/transfers")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(10)
    @DisplayName("10. Wire not found → 404 Not Found")
    void wireNotFound() {
        given(requestSpec)
                .when()
                .get("/api/v1/wire/transfers/{id}", UUID.randomUUID())
                .then()
                .statusCode(404);
    }
}

